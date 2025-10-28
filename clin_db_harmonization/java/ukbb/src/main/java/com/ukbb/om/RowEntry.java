package com.ukbb.om;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

@Data
public class RowEntry {

	String patId;
	int instanceId;
	int arrayIndex;

	List<DataField> fields;
	List<String> fieldNames;
	List<String> values;

	// the table this row is assigned to
	Category dbTable;

	public RowEntry() {
		this.fields = new ArrayList<>();
		this.fieldNames = new ArrayList<>();
		this.values = new ArrayList<>();
		this.instanceId = -1;
		this.arrayIndex = -1;
		this.dbTable = null;
	}

	public String getSQL() {

		// test that there is a table assigned to the row
		if (this.dbTable == null)
			return "";

		// test that the row has some data to input
		if (this.hasData() == false)
			return "";

		if (this.fields.size() != this.values.size()) {
			System.err.println("Mismatch on row entry, fields and values don't match!");
			return "";
		}

		// we have data
		String sql = new String();
		sql = "INSERT INTO " + this.dbTable.getSQLTableName() + " (EID, ";
		if (this.dbTable.hasInstances() == true)
			sql = sql + "INSTANCE_ID, ";

		if (this.dbTable.hasArrayIndex() == true && this.dbTable.hasMultipleChoice() == false)
			sql = sql + "ARRAY_INDEX, ";

		// iterate through every field
		if (this.fields.size() != this.values.size() && this.fieldNames.size() != this.values.size()) {
			System.err.println("Error: Mismatch on columns/value sizes");
			return "";
		} else {

			// Add each field name
			for (int col_idx = 0; col_idx < this.fieldNames.size(); col_idx++) {
				String fieldName = this.fieldNames.get(col_idx);
				// unique field name
				sql = sql + fieldName + ",";

			}

			// remove last comma
			sql = sql.substring(0, sql.length() - 1);
			sql = sql + " ) VALUES ( " + this.patId + ", ";

			// if the table captures event level information
			if (this.dbTable.hasInstances() == true)
				sql = sql + this.instanceId + ", ";

			// if the table captures event level information
			if (this.dbTable.hasArrayIndex() == true && this.dbTable.hasMultipleChoice() == false)
				sql = sql + this.arrayIndex + ", ";

			// convert data fields to sql entries
			for (int colIdx = 0; colIdx < values.size(); colIdx++) {

				// we know that a field could be multiple choice and therefore
				// we have a different field name
				DataField field = this.fields.get(colIdx);
				String value = this.values.get(colIdx);

				// remove the T from the date/timestampe
				if (field.isDateType()) {
					value = value.replace("T", " ");
				} else {
					// escape a quote with double quote for PostgreSQL compliant text fields
					value = value.replace("'", "''");
				}

				if (!StringUtils.isEmpty(value)) {

					// MULTIPLE is flattened to integer 0/1 responses, INTEGER or NUMERIC do not
					// require quotes
					if (field.valueType == 11 || field.valueType == 31 || field.isMultipleChoice()) {
						sql = sql + value + ",";

					} else {

						if (field.hasEncoding()) {
							if (!field.isDateType() && field.isIntegerEncoded() == true) {
								sql = sql + value + ",";
							} else {
								// quote the value
								sql = sql + "'" + value + "',";
							}
						} else {
							// quote the value
							sql = sql + "'" + value + "',";
						}
					}
				} else {
					sql = sql + "null,";
				}
			}
			sql = sql.substring(0, sql.length() - 1);
			sql = sql + ");\n";
			return sql;
		}

	}

	public boolean hasData() {
		boolean hasData = false;
		for (String val : this.values)
			if (!StringUtils.isEmpty(val))
				hasData = true;
		return hasData;
	}

	public void addEntry(DataField field, String fieldName, String value) {
		this.getFields().add(field);
		this.getFieldNames().add(fieldName);
		this.getValues().add(value);
		return;
	}

	public String getCSV(String DELIMITER) {

		// test that there is a table assigned to the row
		if (this.dbTable == null)
			return "";

		// test that the row has some data to input
		if (this.hasData() == false)
			return "";

		if (this.fields.size() != this.values.size()) {
			System.err.println("Mismatch on row entry, fields and values don't match!");
			return "";
		}

		// iterate through every field
		if (this.fields.size() != this.values.size() && this.fieldNames.size() != this.values.size()) {
			System.err.println("Error: Mismatch on columns/value sizes");
			return "";
		} else {

			// Add primary key and patient id
			String csvRow = this.patId + DELIMITER;

			// if the table captures event level information
			if (this.dbTable.hasInstances() == true)
				csvRow = csvRow + this.instanceId + DELIMITER;

			// if the table captures event level information
			if (this.dbTable.hasArrayIndex() == true && this.dbTable.hasMultipleChoice() == false)
				csvRow = csvRow + this.arrayIndex + DELIMITER;
			
			// convert data fields to sql entries
			for (int colIdx = 0; colIdx < values.size(); colIdx++) {

				// we know that a field could be multiple choice and therefore
				// we have a different field name
				DataField field = this.fields.get(colIdx);
				String value = this.values.get(colIdx);

				// remove the T from the date/timestamp data types
				if ( field.isDateType() ) 
				{
					if (value.toLowerCase().contains("t")) {
						value = value.replace("T", " ");
					}
				} else {
					// escape a quote with double quote for PostgreSQL compliant text fields
					value = value.replace("'", "''");
				}

				if (!StringUtils.isEmpty(value)) {

					// MULTIPLE is flattened to integer 0/1 responses, INTEGER or NUMERIC do not
					// require quotes
					if (field.valueType == 11 || field.valueType == 31 || field.isMultipleChoice()) {
						csvRow = csvRow + value + DELIMITER;

					} else {

						if (field.hasEncoding()) {
							if (field.isIntegerEncoded() == true) {
								csvRow = csvRow + value + DELIMITER;
							} else {
								// quote the value
								csvRow = csvRow + "\"" + value + "\"" + DELIMITER;
							}
						} else {
							// quote the value
							csvRow = csvRow + "\"" + value + "\"" + DELIMITER;
						}
					}
				} else {
					csvRow = csvRow + DELIMITER;
				}
			}
			
			csvRow = csvRow.substring(0, csvRow.length() - 1);
			return csvRow;
		}

	}
}
