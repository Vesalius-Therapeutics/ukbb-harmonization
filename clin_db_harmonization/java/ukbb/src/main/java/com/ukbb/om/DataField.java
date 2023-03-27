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
public class DataField {

	// type values
	int valueType;
	int baseType;
	int itemType;

	String code;
	String description;
	String units;
	String codeType;
	String notes;

	Category mainCategory;
	int encodingId;
	int categoryId;
	double showcaseOrder;

	int count;
	// every column maps to an event period (collection point 0, 1, 2, etc)
	public List<DataDictionaryCode> codeEntries;
	public List<Coding> encodingValues;

	private Config myConfig;

	public DataField(Config conf) {
		this.codeEntries = new ArrayList<>();
		this.encodingValues = new ArrayList<>();
		this.encodingId = -1;
		this.count = 0;
		this.mainCategory = null;
		this.categoryId = 0;
		this.myConfig = conf;
	}

	/**
	 * Test that all encoding values are integers
	 * 
	 * @return
	 */
	public boolean isIntegerEncoded() {
		boolean isInteger = true;
		for (Coding c : this.encodingValues) {
			// has a string code
			if (c.getIntegerCode() == -99999999 && c.getStringCode() != null)
				isInteger = false;
		}
		return isInteger;
	}

	/**
	 * If any entry has an array index value greater than zero, then we consider the
	 * entire data field to contain array indexes
	 * 
	 * @return
	 */
	public boolean hasArrayIndex() {
		for (DataDictionaryCode ddc : this.codeEntries)
			if (ddc.getArrayIndex() > 0)
				return true;
		return false;
	}

	/**
	 * This tracks events as an array across the data field
	 * 
	 * @param eventPeriod
	 */
	public void addCSVColumn(DataDictionaryCode col) {
		this.codeEntries.add(col);
	}

	public boolean isMultipleChoice() {

		String mapType = this.myConfig.mappedTable(this.mainCategory.getSQLTableName());
		if (mapType != null && mapType.contentEquals("IGNORE_MULTI"))
			return false;

		if (this.getValueType() == 21 || this.getValueType() == 22)
			return true;
		return false;
	}

	/**
	 * Extra case to check the encoding type when we are using ignore multiple
	 * choice flattening options
	 * 
	 * @return
	 */
	public String getSQLDataTypeIgnoreMultiFlatten() {

		if (this.getValueType() == 21 || this.getValueType() == 22) {
			if (this.hasEncoding()) {
				if (this.isIntegerEncoded() == true) {
					return "INTEGER";
				} else {
					return "VARCHAR(65)";
				}
			} else {
				// unknown data type, just use characters to store
				return "VARCHAR(65)";
			}

		} else {
			return this.getSQLDataType();
		}

	}

	public String getSQLDataType() {
		/*
		 * 11 = Integer - whole numbers, for example the age of a participant on a
		 * particular date;
		 * 
		 * 21 = Categorical (single) - a single answer selected from a coded list or
		 * tree of mutually exclusive options, for example a yes/no choice;
		 * 
		 * -- note this behavior is violated on some fields -- (e.g. 20112 - illness of
		 * adopted father) -- so treat the same as multiple categorical and flatten
		 * 
		 * 22 = Categorical (multiple) - sets of answers selected from a coded list or
		 * tree of options, for instance concurrent medications;
		 * 
		 * 31 = Continuous - floating-point numbers, for example the height of a
		 * participant;
		 * 
		 * 41 = Text - data composed of alphanumeric characters, for example the first
		 * line of an address;
		 * 
		 * 51 = Date - a calendar date, for example 14th October 2010;
		 * 
		 * 61 = Time - a time, for example 13:38:05 on 14th October 2010;
		 * 
		 * 101 = Compound - a set of values required as a whole to describe some
		 * compound property, for example an ECG trace;
		 */

		// special case to test for
		if (this.valueType != 51 && this.valueType != 61 && this.hasEncoding())
			if (this.isIntegerEncoded())
				return "INTEGER";

		HashMap<Integer, String> dtypes = new HashMap<>();
		dtypes.put(11, "INTEGER");
		dtypes.put(31, "NUMERIC");
		dtypes.put(41, "TEXT");
		dtypes.put(51, "DATE");
		dtypes.put(61, "TIMESTAMP");
		dtypes.put(101, "TEXT");

		// These will both be converted to Binary responses/outcomes (yes/no)
		dtypes.put(21, "INTEGER");
		dtypes.put(22, "INTEGER");

		// test that data type exists
		if (dtypes.containsKey(this.valueType))
			return dtypes.get(this.valueType);
		else
			System.err.println("Missing value type for datafield: " + this.getCode());

		// default type if not found
		return "TEXT";

	}

	public List<Coding> getEncodingValuesOrdered() {
		// do we sort on int values or strings?
		boolean intValues = true;
		for (Coding ec : this.getEncodingValues()) {
			if (ec.getIntegerCode() == -99999999)
				intValues = false;
		}

		// make a choice on which way to sort the encoding values
		if (intValues == true) {
			Collections.sort(this.getEncodingValues(), Comparator.comparing(Coding::getIntegerCode));
			return this.getEncodingValues();
		} else {

			// Check that every element has a string value
			for (Coding ec : this.getEncodingValues()) {
				if (ec.getStringCode() == null)
					if (ec.getIntegerCode() > -99999999)
						ec.setStringCode(new Integer(ec.getIntegerCode()).toString());

				if (ec.getStringCode() == null) {
					System.err.println("Encoding for code [" + this.getCode()
							+ "] should be a string, but has no value... " + ec.getMeaning());
				}
			}

			Collections.sort(this.getEncodingValues(), Comparator.comparing(Coding::getStringCode));
			return this.getEncodingValues();
		}

	}

	/**
	 * Convert to SQL
	 * 
	 * @return
	 */
	public String getSQL() {
		return (this.getSQLFieldName() + "\t" + this.getSQLDataType());
	}

	/**
	 * Give a table name, make sure it is safe for table creation
	 * 
	 * @param tblName
	 * @return
	 */
	public String getSQLFieldName() {

		if (!StringUtils.isEmpty(this.getDescription())) {
			String fieldName = this.getDescription();

			// cannot start with a number
			char ch = fieldName.charAt(0);
			if (Character.isDigit(ch))
				fieldName = "F_" + fieldName;

			return StringFormat.getSQLFormat(fieldName);
		} else {
			return "";
		}

	}

	public String toString(String delimiter) {
		StringBuilder output = new StringBuilder();
		output.append(this.code);
		output.append(delimiter);
		output.append(this.description);
		return output.toString();

	}

	public String toLongString(String delimiter) {
		StringBuilder output = new StringBuilder();

		output.append(this.code);
		output.append(delimiter);

		output.append(this.description);
		output.append(delimiter);

		output.append(this.codeType);
		output.append(delimiter);

		output.append(this.getAllColumns());
		return output.toString();

	}

	public List<Integer> getColumns(int instanceId) {
		ArrayList<Integer> cols = new ArrayList<>();
		for (DataDictionaryCode code : this.codeEntries)
			if (code.getInstanceId() == instanceId)
				cols.add(code.getCsvColumn());
		return cols;
	}

	public List<Integer> getAllColumns() {
		ArrayList<Integer> cols = new ArrayList<>();
		for (DataDictionaryCode code : this.codeEntries)
			cols.add(code.getCsvColumn());
		return cols;
	}

	public String getHierarchy() {

		return this.mainCategory.getHierarchy();
	}

	public boolean hasEncoding() {
		if (this.encodingId > 0)
			return true;
		return false;
	}

	/**
	 * Get the columns of this data field which map to a particular event/time
	 * period for data collection
	 * 
	 * @param instanceId
	 * @return
	 */
	public ArrayList<Integer> getColumnsAtInstance(int instanceId) {

		ArrayList<Integer> eventColumns = new ArrayList<>();
		for (DataDictionaryCode code : this.getDdcAtInstance(instanceId))
			eventColumns.add(code.getCsvColumn());
		return eventColumns;
	}

	/**
	 * Get the columns of this data field which map to a particular event/time
	 * period for data collection
	 * 
	 * @param instanceId
	 * @return
	 */
	public ArrayList<DataDictionaryCode> getDdcAtInstance(int instanceId) {

		ArrayList<DataDictionaryCode> dlist = new ArrayList<>();
		for (DataDictionaryCode code : this.getCodeEntries())
			if (code.getInstanceId() == instanceId)
				dlist.add(code);
		return dlist;
	}

	/**
	 * Get max index value
	 * 
	 * @return
	 */
	public int getMaxArrayIndex() {

		int maxIndex = 0;
		for (DataDictionaryCode code : this.getCodeEntries())
			if (code.getArrayIndex() > maxIndex)
				maxIndex = code.getArrayIndex();
		return maxIndex;
	}

	/**
	 * Test if date type object
	 * 
	 * @return
	 */
	public boolean isDateType() {
		
		boolean isDate = false;
		if ( this.getSQLDataType().contentEquals("DATE") )
			isDate = true;

		if ( this.getSQLDataType().contentEquals("TIMESTAMP") )
			isDate = true;

		return isDate;
	}

}
