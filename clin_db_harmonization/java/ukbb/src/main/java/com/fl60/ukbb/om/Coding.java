package com.fl60.ukbb.om;

import org.apache.commons.lang3.StringUtils;

import com.fl60.ukbb.util.StringFormat;

/*
 *  ------------------------------------------------------------------
 *  FL60
 *  Cambridge, MA
 *  https://fl60inc.com
 *  
 *  Original Author: Jeffery Painter, jpainter@fl60inc.com
 *
 *  Copyright (c) 2020 FL60, Inc. All Rights Reserved. Permission
 *  to copy, modify and distribute this software and code
 *  included and its documentation (collectively, the "PROGRAM") for
 *  any purpose is hereby prohibited.
 *
 *  THE PROGRAM IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EITHER EXPRESSED OR IMPLIED, INCLUDING, WITHOUT
 *  LIMITATION, WARRANTIES THAT THE PROGRAM IS FREE OF
 *  DEFECTS, MERCHANTABLE, FIT FOR A PARTICULAR PURPOSE OR
 *  NON-INFRINGING. THE ENTIRE RISK AS TO THE QUALITY AND
 *  PERFORMANCE OF THE PROGRAM IS WITH YOU. SHOULD ANY PART
 *  OF THE PROGRAM PROVE DEFECTIVE IN ANY RESPECT, YOU
 *  (NOT JIVECAST) ASSUME THE COST OF ANY NECESSARY SERVICING,
 *  REPAIR OR CORRECTION. THIS DISCLAIMER OF WARRANTY CONSTITUTES
 *  AN ESSENTIAL PART OF THIS LICENSE. NO USE OF
 *  THE PROGRAM IS AUTHORIZED HEREUNDER EXCEPT
 *  UNDER THIS DISCLAIMER.
 *
 *  ------------------------------------------------------------------
 */

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
