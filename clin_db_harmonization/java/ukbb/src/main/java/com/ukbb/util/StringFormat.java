package com.ukbb.util;

import java.util.HashMap;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

public class StringFormat {

	/** Max table or column name length **/
	public static final int MAX_MYSQL_NAME_LENGTH = 60;

	/**
	 * Convert a string to a safe SQL form
	 * 
	 * @param input
	 * @return
	 */
	public static String getSQLFormat(String input) {

		if (!StringUtils.isEmpty(input)) {

			input = input.toUpperCase().trim();
			input = input.replace(" ", "_");
			input = input.replace("-", "_");
			input = input.replace("__", "_");
			input = input.replace("(", "");
			input = input.replace(")", "");

			// punctuation
			input = input.replace("'", "");
			input = input.replace(".", "");
			input = input.replace("?", "");
			input = input.replace("\"", "");
			input = input.replace("#", "");
			input = input.replace("@", "");
			input = input.replace(";", "_");
			input = input.replace(",", "_");
			input = input.replace(":", "_");

			// slashes
			input = input.replace("\\", "_");
			input = input.replace("/", "_");

			// remove these comparators from names
			input = input.replace("<=", "LTE");
			input = input.replace("<", "LT");
			input = input.replace(">=", "GTE");
			input = input.replace(">", "GT");
			input = input.replace("+", "PLUS");
			input = input.replace("&", "AND");
			input = input.replace("__", "_");

			if (input.length() >= MAX_MYSQL_NAME_LENGTH)
				input = input.substring(0, MAX_MYSQL_NAME_LENGTH);

			// Don't end with an underscore
			if (input.endsWith("_"))
				input = input.substring(0, input.length() - 1);

			return input;
		} else {
			return "";
		}
	}

	/**
	 * Track if a field is a duplicate, if so, create a new indexed name for it
	 * 
	 * @param fieldNames
	 * @param fieldName
	 * @return
	 */
	public static String testDuplicateFieldName(HashMap<String, Integer> fieldNames, String fieldName, HashMap<String, Boolean> reservedTerms) {

		// perform substitution if necessary
		if (reservedTerms.containsKey(fieldName.toUpperCase()))
		{
			fieldName = "R_" + fieldName.toUpperCase();
			// System.err.println("Updating fieldname: " + fieldName);
		}

		// Keep only alpha-numeric and underscores
		fieldName = fieldName.replaceAll("__",  "_");
		fieldName = fieldName.replaceAll("[^a-zA-Z0-9_\\s]", "");
		String returnName = fieldName;

		// first time
		if (fieldNames.containsKey(fieldName) == false) {
			fieldNames.put(fieldName, 1);
		} else {

			// seen before, get last index
			int lastIdx = fieldNames.get(fieldName);
			int newIdx = lastIdx + 1;

			// update index
			fieldNames.put(fieldName, newIdx);

			// check max length
			if (fieldName.length() < (StringFormat.MAX_MYSQL_NAME_LENGTH - 1))
				returnName = fieldName + "_" + newIdx;
			else
				returnName = fieldName.substring(0, (StringFormat.MAX_MYSQL_NAME_LENGTH - 2)) + "_" + newIdx;

		}

		// replace doubles
		returnName = returnName.replace("__",  "_");
		return returnName;
	}

	public static String[] csvRecordToStringArray(CSVRecord record) {
		if (record != null) {
			String[] result = new String[record.size()];
			for (int idx = 0; idx < record.size(); idx++)
				result[idx] = record.get(idx);
			return result;
		} else {
			return null;
		}
	}
}
