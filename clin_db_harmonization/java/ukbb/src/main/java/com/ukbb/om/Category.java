package com.ukbb.om;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.ukbb.Config;
import com.ukbb.util.StringFormat;

import lombok.Data;

@Data
public class Category {

	private int refId;
	private String name;
	private List<Category> children;
	private Category parent;
	private Category rootCategory;
	private boolean multiColumn;
	private HashMap<String, Boolean> reserveWords;

	// the root category will establish the eid as a reference key in all other
	// tables
	private boolean root;

	private List<DataField> dataFields;
	private List<String> myColumnNames;

	private Config myConfig;

	public Category(Config conf) {
		this.refId = -1;
		this.name = "";
		this.children = new ArrayList<>();
		this.parent = null;
		this.dataFields = new ArrayList<>();
		this.root = false;
		this.rootCategory = null;
		this.multiColumn = false;
		this.myConfig = conf;
		this.myColumnNames = new ArrayList<>();
		return;
	}

	/**
	 * Does a table need the instance ID field? If there is at least one instance id
	 * greater than 0, we will assume yes, otherwise, no
	 * 
	 * @return
	 */
	public boolean hasInstances() {

		for (int period : this.getUniqueEventPeriods().keySet())
			if (period > 0)
				return true;

		return false;
	}

	public boolean hasMixedArrayIndexes() {
		HashMap<Integer, Boolean> array_counts = new HashMap<>();
		for (DataField field : this.getDataFields()) {
			int maxIndex = field.getMaxArrayIndex();
			array_counts.put(maxIndex, true);
		}

		// do we see mixed array index sizes?
		if (array_counts.size() > 1)
			return true;
		return false;
	}

	/**
	 * Test if category contains any multiple choice questions
	 * 
	 * @return
	 */
	public boolean hasMultipleChoice() {
		for (DataField field : this.getDataFields())
			if (field.isMultipleChoice())
				return true;
		return false;
	}

	public boolean hasArrayIndex() {

		// Multi-column tables do not have array indexed rows
		if (this.isMultiColumn() == true)
			return false;

		for (DataField field : this.dataFields) {
			if (field.hasArrayIndex())
				return true;
		}
		return false;
	}

	/**
	 * Give a table name, make sure it is safe for table creation
	 * 
	 * @param tblName
	 * @return
	 */
	public String getSQLTableName() {
		if (!StringUtils.isEmpty(this.name)) {
			String tblName;

			// some table names are not unique, append parent category if there is one
			if (this.hasParent())
				tblName = this.parent.name + "_" + this.name;
			else
				tblName = this.name;

			// trim
			tblName = tblName.trim();

			// cannot start with a number
			char ch = tblName.charAt(0);
			if (Character.isDigit(ch))
				tblName = "T_" + tblName;

			return StringFormat.getSQLFormat(tblName);
		} else {
			return "";
		}
	}

	/**
	 * Convert a category and data fields into a SQL table
	 * 
	 * @return
	 */
	public String getSQL(String mapType, boolean generateSequences) {

		if (mapType == null) {
			return getStandardSQLTable(generateSequences);
		} else {

			// Exclude
			if (mapType.contentEquals("NONE")) {
				return "";
			}

			// row base repeats
			if (mapType.contentEquals("ROW") || mapType.contentEquals("MULTI")
					|| mapType.contentEquals("IGNORE_MULTI")) {
				return getStandardSQLTable(generateSequences);
			}

			// column based variable repeats (multiple choice questions are all the same
			// format too)
			if (mapType.contentEquals("COL")) {
				return getColumnRepeatSQLTable(generateSequences);
			}

			// mixed requires special work? let's try a column repeat first
			if (mapType.contentEquals("MIXED")) {
				return getColumnRepeatSQLTable(generateSequences);
			}

		}
		return "";
	}

	/**
	 * Get a row-repeat SQL table fields that match on array index generate new rows
	 * 
	 * @return
	 */
	private String getColumnRepeatSQLTable(boolean generateSequences) {

		if (this.getDataFields().size() > 0) {

			String tblName = this.getSQLTableName();
			String tblSeqName = tblName + "_SEQ";
			if (generateSequences == true) {
				if (tblSeqName.length() > StringFormat.MAX_MYSQL_NAME_LENGTH) {
					tblSeqName = tblName.substring(0, (StringFormat.MAX_MYSQL_NAME_LENGTH - 6)) + "_SEQ";
				}
			}

			StringBuilder sql = new StringBuilder();

			sql.append("DROP TABLE IF EXISTS " + this.getSQLTableName() + ";" + "\n");
			if (generateSequences == true) {
				sql.append("DROP SEQUENCE IF EXISTS " + tblSeqName + ";" + "\n");
				sql.append(
						"CREATE SEQUENCE " + tblSeqName + " INCREMENT BY 1 START WITH 1 NO MAXVALUE NO CYCLE;" + "\n");
			}
			sql.append("CREATE TABLE " + this.getSQLTableName() + " ( " + "\n");

			if (generateSequences == true) {
				sql.append("\t" + "REF_ID INTEGER NOT NULL DEFAULT nextval('" + tblSeqName + "')," + "\n");
			}
			sql.append("\t" + "EID INTEGER NOT NULL," + "\n"); // patient id

			this.myColumnNames.add("EID");

			// only if the table needs event number entries per row
			if (this.hasInstances()) {
				// it is possible that a patient's data may be collected
				// at different event periods (collection 01, 02, etc)
				sql.append("\t" + "INSTANCE_ID INTEGER NOT NULL," + "\n");
				this.myColumnNames.add("INSTANCE_ID");
			}

			HashMap<String, Integer> fieldNames = new HashMap<>();

			// convert data fields to sql entries
			for (DataField field : this.getSingleSortedDataFields()) {

				if (field.isMultipleChoice()) {
					// add multiple columns for this field, must match on array_index value
					for (Coding encode : field.getEncodingValuesOrdered()) {
						String fieldName = encode.getSQLFieldName();
						addFieldToTable(sql, field, fieldNames, "INTEGER", fieldName);
					}

				} else {
					// add field
					String fieldName = field.getSQLFieldName();
					addFieldToTable(sql, field, fieldNames, field.getSQLDataType(), fieldName);
				}
			}

			for (DataField field : this.getMultiColDataFieldsSorted()) {

				if (field.isMultipleChoice()) {

					System.out.println("Should not see this...");

					// add multiple columns for this field, must match on array_index value
					for (Coding encode : field.getEncodingValuesOrdered()) {
						String fieldName = encode.getSQLFieldName();
						addFieldToTable(sql, field, fieldNames, "INTEGER", fieldName);
					}

				} else {

					//
					// add multiple columns for this field, must match on array_index value
					//
					for (int arrayIndex = 0; arrayIndex <= field.getMaxArrayIndex(); arrayIndex++) {

						int col_idx = -1;
						for (DataDictionaryCode code : field.getCodeEntries()) {
							if (code.getArrayIndex() == arrayIndex) {
								col_idx = code.getCsvColumn();
							}
						}

						if (col_idx > 0) {
							// add field
							String fieldName = field.getSQLFieldName() + "_" + arrayIndex;
							addFieldToTable(sql, field, fieldNames, field.getSQLDataType(), fieldName);
						}
					}
				}
			}

			// if we are not generating sequences, we need to remove the last comma
			// inserted
			if (generateSequences == false) {
				String text = removeLastComma(sql.toString());
				sql = new StringBuilder();
				sql.append(text);
			}

			if (this.isRoot() == true) {
				sql.append("\tUNIQUE(eid)");
				if (generateSequences == true) {
					sql.append(",");
				}
				sql.append("\n");
			}

			if (generateSequences == true) {
				sql.append("\tPRIMARY KEY(REF_ID)" + "\n");
			}

			// we need to remove the last comma if we are not using
			// sequences
			if (generateSequences == false) {
				String output = removeLastComma(sql.toString());
			}

			sql.append(");\n\n");

			if (this.isRoot() == false) {
				sql.append("--\n");
				sql.append("-- Link eid as foreign key to root category \n");
				sql.append("--\n\n");
				sql.append("ALTER TABLE " + this.getSQLTableName() + "\n");
				sql.append("\t" + "ADD CONSTRAINT " + this.getForeignKey(1) + "\n");
				sql.append("\t" + "FOREIGN KEY (eid) " + "\n");
				sql.append("\t" + "REFERENCES " + this.getRootCategory().getSQLTableName() + " (eid);" + "\n\n");
			}

			return sql.toString();
		} else {
			return "-- no data";
		}
	}

	/**
	 * Remove the last comma in a text string
	 * 
	 * @param string
	 * @return
	 */
	private String removeLastComma(String inputString) {

		if (inputString.contains(",")) {
			int lastComma = inputString.lastIndexOf(",");
			if (lastComma > 0) {
				return inputString.substring(0, lastComma) + inputString.substring(lastComma + 1, inputString.length());
			} else {
				return inputString;
			}
		} else {
			return inputString;
		}
	}

	/**
	 * Get standard SQL table
	 * 
	 * @return
	 */
	private String getStandardSQLTable(boolean generateSequences) {
		if (this.getDataFields().size() > 0) {
			String tblName = this.getSQLTableName();
			String tblSeqName = tblName + "_SEQ";
			if (generateSequences == true) {
				if (tblSeqName.length() > StringFormat.MAX_MYSQL_NAME_LENGTH) {
					tblSeqName = tblName.substring(0, (StringFormat.MAX_MYSQL_NAME_LENGTH - 6)) + "_SEQ";
				}
			}

			StringBuilder sql = new StringBuilder();
			sql.append("DROP TABLE IF EXISTS " + this.getSQLTableName() + ";" + "\n");
			if (generateSequences == true) {
				sql.append("DROP SEQUENCE IF EXISTS " + tblSeqName + ";" + "\n");
				sql.append(
						"CREATE SEQUENCE " + tblSeqName + " INCREMENT BY 1 START WITH 1 NO MAXVALUE NO CYCLE;" + "\n");
			}
			sql.append("CREATE TABLE " + this.getSQLTableName() + " ( " + "\n");
			if (generateSequences == true) {
				sql.append("\t" + "REF_ID INTEGER NOT NULL DEFAULT nextval('" + tblSeqName + "')," + "\n");
			}
			sql.append("\t" + "EID INTEGER NOT NULL," + "\n"); // patient id

			this.myColumnNames.add("EID");

			// only if the table needs event number entries per row
			if (this.hasInstances()) {
				// it is possible that a patient's data may be collected
				// at different event periods (collection 01, 02, etc)
				sql.append("\t" + "INSTANCE_ID INTEGER NOT NULL," + "\n");
				this.myColumnNames.add("INSTANCE_ID");
			}

			//
			// only if the table needs event number entries per row,
			// tables with multiple choice categories don't count
			// as the array index is just used to capture multiple
			// choice answers (if more than one applies)
			if (this.hasArrayIndex() && this.hasMultipleChoice() == false) {
				// if a value is collected multiple times at a single
				// instance, we will order it by the array index value
				sql.append("\t" + "ARRAY_INDEX INTEGER NOT NULL," + "\n");
				this.myColumnNames.add("ARRAY_INDEX");
			}

			HashMap<String, Integer> fieldNames = new HashMap<>();

			// convert data fields to sql entries
			for (DataField field : this.getSortedDataFields()) {

				if (field.isMultipleChoice()) {
					//
					// let's try to convert these out to FIELD_MULTI_VALUE_NAME
					// - must preserve order so we know how to do the inserts
					// at the patient record level
					//

					for (Coding encode : field.getEncodingValuesOrdered()) {
						String fieldName = encode.getSQLFieldName();
						if (field.isIntegerEncoded() == true) {
							addFieldToTable(sql, field, fieldNames, "INTEGER", fieldName);
						} else {
							addFieldToTable(sql, field, fieldNames, "VARCHAR(65)", fieldName);
						}
					}
				} else {
					// add field
					String fieldName = field.getSQLFieldName();
					addFieldToTable(sql, field, fieldNames, field.getSQLDataTypeIgnoreMultiFlatten(), fieldName);
				}
			}

			// if we are not generating sequences, we need to remove the last comma
			// inserted
			if (this.isRoot() == false && generateSequences == false) {
				String text = removeLastComma(sql.toString());
				sql = new StringBuilder();
				sql.append(text);
			}

			if (this.isRoot() == true) {
				
				sql.append("\tUNIQUE(eid)");
				if (generateSequences == true) {
					sql.append(",");
				}
				sql.append("\n");
			}

			if (generateSequences == true) {
				sql.append("\tPRIMARY KEY(REF_ID)" + "\n");
			}
			sql.append(");\n\n");

			if (this.isRoot() == false) {
				sql.append("--\n");
				sql.append("-- Link eid as foreign key to root category \n");
				sql.append("--\n\n");
				sql.append("ALTER TABLE " + this.getSQLTableName() + "\n");
				sql.append("\t" + "ADD CONSTRAINT " + this.getForeignKey(1) + "\n");
				sql.append("\t" + "FOREIGN KEY (eid) " + "\n");
				sql.append("\t" + "REFERENCES " + this.getRootCategory().getSQLTableName() + " (eid);" + "\n\n");
			}

			return sql.toString();
		} else {
			return "-- no data";
		}
	}

	private void addFieldToTable(StringBuilder sql, DataField field, HashMap<String, Integer> fieldNames,
			String sqlDataType, String fieldName) {

		try {
			fieldName = StringFormat.testDuplicateFieldName(fieldNames, fieldName, this.reserveWords);

			// save to local list of column names
			if (this.myColumnNames == null)
				this.myColumnNames = new ArrayList<>();
			this.myColumnNames.add(fieldName);

			sql.append("\t" + fieldName + "\t" + sqlDataType + ",\t\t -- Code: " + field.getCode() + "\n");
		} catch (Exception e) {
			System.err.println("Error adding field to table: " + e.toString());
		}
	}

	/**
	 * Get foreign key constraint
	 * 
	 * @param index
	 * @return
	 */
	public String getForeignKey(int index) {
		String fk = this.getSQLTableName() + "_F_K" + index;
		if (fk.length() >= StringFormat.MAX_MYSQL_NAME_LENGTH)
			fk = this.getSQLTableName().substring(0, StringFormat.MAX_MYSQL_NAME_LENGTH - 6) + "_F_K" + index;
		return fk;
	}

	/**
	 * Add a data field to this category but only once
	 * 
	 * @param field
	 */
	public void addDataField(DataField field) {
		boolean add = true;
		for (DataField f : this.dataFields) {
			if (f.getCode().contentEquals(field.getCode()))
				add = false;
		}
		if (add)
			this.dataFields.add(field);
	}

	public boolean hasParent() {
		if (this.parent != null)
			return true;
		return false;
	}

	public boolean hasChildren() {
		if (this.children.size() > 0)
			return true;
		return false;
	}

	public String toString() {
		StringBuilder output = new StringBuilder();
		output.append(this.getRefId());
		output.append("\t");
		output.append(this.getName());
		return output.toString();
	}

	public String getHierarchy() {

		String output = this.getName();
		if (this.hasParent()) {
			Category current = this.getParent();
			while (current != null) {
				output = current.getName() + " --> " + output;
				if (current.hasParent())
					current = current.getParent();
				else
					current = null;
			}
		}
		return output;
	}

	public List<DataField> getSortedDataFields() {
		// sort the data fields inplace
		List<DataField> fields = this.getDataFields();
		Collections.sort(fields, Comparator.comparingDouble(DataField::getShowcaseOrder));
		return fields;
	}

	/**
	 * Get fields without an array index
	 * 
	 * @return
	 */
	public List<DataField> getSingleSortedDataFields() {
		// sort the data fields inplace
		List<DataField> fields = this.getDataFields();

		List<DataField> singleFields = new ArrayList<>();
		for (DataField field : fields) {

			if (field.isMultipleChoice()) {
				singleFields.add(field);
			} else {

				if (field.hasArrayIndex() == false) {
					singleFields.add(field);
				}
			}
		}

		Collections.sort(singleFields, Comparator.comparingDouble(DataField::getShowcaseOrder));
		return singleFields;
	}

	/**
	 * Get fields without an array index
	 * 
	 * @return
	 */
	public List<DataField> getMultiColDataFieldsSorted() {
		// sort the data fields inplace
		List<DataField> fields = this.getDataFields();

		List<DataField> multiFields = new ArrayList<>();
		for (DataField field : fields) {
			if (!field.isMultipleChoice() && field.hasArrayIndex() == true) {
				multiFields.add(field);
			}
		}

		Collections.sort(multiFields, Comparator.comparingDouble(DataField::getShowcaseOrder));
		return multiFields;
	}

	public String getDataDictionary() {

		StringBuilder output = new StringBuilder();

		// Track duplicate field names
		HashMap<String, Integer> fieldNames = new HashMap<>();

		for (DataField field : this.getSortedDataFields()) {
			// ----------------------------------------
			// track field names the same we we do for tables
			// ----------------------------------------
			String fieldName = field.getSQLFieldName();
			if (fieldNames.containsKey(fieldName) == false) {
				fieldNames.put(fieldName, 1);
			} else {

				// add unique name
				int idx = fieldNames.get(fieldName) + 1;

				// check max length
				if (fieldName.length() < 63)
					fieldName = fieldName + idx;
				else
					fieldName = fieldName.substring(0, 62) + idx;

				fieldNames.put(field.getSQLFieldName(), idx);
			}

			if (field.hasEncoding()) {
				for (Coding code : field.getEncodingValues()) {
					output.append("INSERT INTO UKBB_DATA_DICTIONARY ( ");
					output.append(" TABLE_NAME, ");
					output.append(" COLUMN_NAME , ");
					output.append(" INTEGER_CODE, ");
					output.append(" STRING_CODE, ");
					output.append(" MEANING ) values ( ");

					// convert these to lower case since PostgreSQL
					// converts all table/field names to lower and
					// will make it easier for users to find data
					output.append("'" + this.getSQLTableName().toLowerCase() + "'" + ",");
					output.append("'" + fieldName.toLowerCase() + "'" + ",");

					// what type of field is this?
					if (field.isIntegerEncoded() == true) {
						output.append(code.getIntegerCode() + ",null,");
					} else {
						output.append("null,'" + code.getStringCode() + "',");
					}

					output.append("'" + code.getMeaning().replace("'", "''") + "'");
					output.append("); " + "\n");
				}

			}
		}
		return output.toString();
	}

	/**
	 * Create root category
	 * 
	 * @param string
	 * @param reserveWords
	 */
	public static Category createRoot(String string, Config conf, HashMap<String, Boolean> reserveWords) {

		// Create a new category to serve as our primary reference for all patient id's
		Category root = new Category(conf);
		root.refId = -1;
		root.name = "UKBB_PATIENT";
		root.parent = null;
		root.root = true;
		root.children = new ArrayList<>();
		root.setReserveWords(reserveWords);

		// create data fields
		DataField eid = new DataField(conf);
		eid.setMainCategory(root);
		eid.setCode("eid");
		eid.setDescription("Unique UKBB patient identifier");
		eid.setValueType(11); // integer
		eid.setCodeType("");
		eid.setNotes("");
		eid.setBaseType(0);
		eid.setShowcaseOrder(0);

		// default code
		DataDictionaryCode ddc = new DataDictionaryCode();
		ddc.setCode("eid");
		ddc.setArrayIndex(0);
		ddc.setInstanceId(0);
		ddc.setCsvColumn(-1);
		eid.addCSVColumn(ddc);

		// set the data field
		root.addDataField(eid);

		return root;

	}

	public HashMap<Integer, Boolean> getUniqueEventPeriods() {
		HashMap<Integer, Boolean> instances = new HashMap<>();

		for (DataField field : this.dataFields) {
			for (DataDictionaryCode code : field.getCodeEntries()) {
				int instanceId = code.getInstanceId();
				instances.put(instanceId, true);
			}
		}

		// we just have the zero instance
		if (instances.size() == 0)
			instances.put(0, true);

		// update event periods
		return instances;
	}

	public int getMaxArrayIndex() {

		int maxIndex = 0;
		for (DataField field : this.dataFields) {
			int fieldIndex = field.getMaxArrayIndex();
			if (fieldIndex > maxIndex)
				maxIndex = fieldIndex;
		}
		return maxIndex;
	}

	public void testConsistentIndex() {

		HashMap<Integer, ArrayList<String>> arrayIndexCount = new HashMap<>();
		for (DataField field : this.dataFields) {
			int total = field.getMaxArrayIndex();
			if (arrayIndexCount.containsKey(total) == false)
				arrayIndexCount.put(total, new ArrayList<String>());
			arrayIndexCount.get(total).add(field.getCode() + " -- " + field.getValueType());
		}

		boolean print = true;
		if (print) {
			if (arrayIndexCount.size() > 1) {
				System.out.println("Table has different array index counts: " + this.getSQLTableName());
				for (int size : arrayIndexCount.keySet()) {
					int t = arrayIndexCount.get(size).size();
					if (t > 0) {

						System.out.println("\t" + size + " :: " + t);
						for (String code : arrayIndexCount.get(size))
							System.out.println("\t\t" + code);
					}
				}
			}
		}

	}

	/**
	 * Construct CSV header line
	 * 
	 * @return
	 */
	public String getCSVHeader(String delimiter) {
		StringBuilder header = new StringBuilder();
		boolean first = true;
		if (this.myColumnNames != null) {
			for (String col : this.myColumnNames) {
				if (first == true) {
					first = false;
				} else {
					header.append(delimiter);
				}
				// all table and col names are lower case in psql
				header.append(col.toLowerCase().trim());
			}
		}
		return header.toString();
	}

}
