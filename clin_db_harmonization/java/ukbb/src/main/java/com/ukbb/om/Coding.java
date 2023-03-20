package com.ukbb.om;

import org.apache.commons.lang3.StringUtils;

import com.ukbb.util.StringFormat;

import lombok.Data;

@Data
public class Coding {

	int integerCode;
	String stringCode;
	String meaning;

	public Coding() {
		// defaults
		this.integerCode = -99999999;
		this.stringCode = null;
		this.meaning = null;
	}

	/**
	 * Give a table name, make sure it is safe for table creation
	 * 
	 * @param tblName
	 * @return
	 */
	public String getSQLFieldName() {

		if (!StringUtils.isEmpty(this.getMeaning())) {
			String fieldName = this.getMeaning();

			// cannot start with a number
			char ch = fieldName.charAt(0);
			if (Character.isDigit(ch))
				fieldName = "F_" + fieldName;

			return StringFormat.getSQLFormat(fieldName);
		} else {
			return "";
		}

	}
}
