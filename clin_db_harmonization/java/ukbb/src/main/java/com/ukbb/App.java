package com.ukbb;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import com.ukbb.om.Category;
import com.ukbb.om.Coding;
import com.ukbb.om.DataDictionaryCode;
import com.ukbb.om.DataField;
import com.ukbb.om.RowEntry;
import com.ukbb.parser.UkbbParser;
import com.ukbb.util.GzipPrintWriter;
import com.ukbb.util.Logger;
import com.ukbb.util.StringFormat;

public class App {

	private Config myConfig;
	private HashMap<String, Boolean> reserveWords;
	private String DELIMITER = ",";
	private static String PATIENT_TABLE = "UKBB_PATIENT";
	private static Logger log;

	public App() {
		this.myConfig = null;
		this.reserveWords = new HashMap<>();
		log = new Logger();
	}

	public static void main(String[] args) {

		try {
			App app = new App();
			log.log("Begin");

			Options options = new Options().addRequiredOption("c", "config", true, "Config properties file path");
			CommandLineParser clparse = new DefaultParser();
			CommandLine cmd = clparse.parse(options, args);

			// parameters
			String propertyFile = null;

			if (cmd.hasOption("c") || cmd.hasOption("config")) {
				propertyFile = cmd.getOptionValue("c");
				app.myConfig = new Config(propertyFile);
				app.DELIMITER = app.myConfig.getCsvDelimiter();
				System.out.println("app.myconfig: " + app.myConfig);

			} else {
				log.error("You must provide a path to the properties file");
				System.exit(-1);
			}

			app.doWork();
			// app.listPilotFields();

			log.log("End");

		} catch (Exception e) {
			log.error("Error: " + e.toString());
		}
	}

	/**
	 * Test to see if any of the remaining fields contain 'pilot' questions that we
	 * should exclude
	 * 
	 * Last run shows that we have captured all in our current data dictionary.
	 */
	public void listPilotFields() {
		log.log("Start: " + new Date());

		try {

			// Build a parser to process the data
			UkbbParser parser = new UkbbParser(myConfig);

			HashMap<String, Boolean> ignoreFields = parser.loadIgnoreFields(myConfig.getIgnoreFieldFile());

			// First load all possible data fields
			HashMap<String, DataField> allFields = parser.loadAllFields(myConfig.getFieldFile());

			for (String key : allFields.keySet()) {
				DataField field = allFields.get(key);
				// skip already ignored fields
				if (ignoreFields.containsKey(key) == false) {
					if (field.getDescription().toLowerCase().contains("pilot")) {
						// we should exclude this one
						log.log("Exclude: " + field.getCode() + "\t" + field.getDescription());
					} else {
						// we have no reason to exclude
						// log.log("Keeping: " + field.getDescription());
					}
				} else {
					// previously ignored - test that our method is doing the right thing
					if (field.getDescription().toLowerCase().contains("pilot")) {
						// log.log("IGNORED: " + field.getCode() + "\t" +
						// field.getDescription());
					}
				}
			}

		} catch (Exception e) {
			log.error("Error: " + e.toString());
		}

		log.log("End: " + new Date());

	}

	public void doWork() {

		// Build a parser to process the data
		UkbbParser parser = new UkbbParser(myConfig);

		HashMap<String, Boolean> ignoreFields = parser.loadIgnoreFields(myConfig.getIgnoreFieldFile());

		// set database reserve words
		this.reserveWords = parser.loadReserveWords(myConfig.getReservedWordFile());

		// First load all possible data fields
		HashMap<String, DataField> allFields = parser.loadAllFields(myConfig.getFieldFile());

		// update the hierarchy from our categories file
		parser.setUkbbHierarchy(myConfig.getCategoryFile());

		// Set the root table for UKBB
		Category rootCategory = Category.createRoot(PATIENT_TABLE, myConfig, this.reserveWords);
		parser.setRootCategory(rootCategory);

		// remove the excluded tables
		parser.removeExcludedTables(myConfig.getExcludeTables());
		
		// remove all tables except for the ones we want to rebuild
		if ( myConfig.isRebuildTables() == true )
		{
			parser.setRebuildTables(myConfig.getTablesToRebuild());
		}

		log.log("Total categories: " + parser.getTotalCategories());

		//
		// assign the main category to all fields
		//
		for (String code : allFields.keySet()) {
			DataField field = allFields.get(code);

			// test that the category is to be included
			if (parser.hasCategory(field.getCategoryId())) {
				Category cat = parser.getCategories().get(field.getCategoryId());
				field.setMainCategory(cat);
			}
		}

		// load availabe data fields and set their columns based on our data dictionary
		HashMap<String, Boolean> availableFields = parser.setAvailableFields(myConfig.getDataDictionaryFile(),
				allFields, ignoreFields);

		// encodings
		HashMap<Integer, HashMap<String, String>> encodings = parser.setEncodings(myConfig.getDataEncodingFile());

		// log.log("Available fields: " + availableFields.size());
		List<DataField> finalSet = new ArrayList<>();
		for (String code : availableFields.keySet()) {

			DataField field = allFields.get(code);
			if (field != null) {
				if (parser.hasCategory(field.getCategoryId()) == true) {

					finalSet.add(field);

					// add to the category linked
					Category cat = field.getMainCategory();
					cat.addDataField(field);

					//
					// Test if a field has an encoding and update it
					//
					// Note: for some reason field 22700 is a Date but has an encoding
					// add a special check to exclude date types from testing for
					// any special encoding values - those without a date
					// were given a default date of 1904-04-04 with data code set 1313
					//
					// http://biobank.ctsu.ox.ac.uk/crystal/coding.cgi?id=1313
					//
					// value type of 51 = Date
					//
					if (field.getValueType() != 51) {
						if (field.hasEncoding() == true) {
							if (encodings.containsKey(field.getEncodingId())) {
								HashMap<String, String> emap = encodings.get(field.getEncodingId());
								if (emap.size() > 0) {
									for (String enc_key : emap.keySet()) {
										Coding e_code = new Coding();

										// see if this is an integer or string
										try {
											int intKey = Integer.parseInt(enc_key);
											e_code.setIntegerCode(intKey);
										} catch (Exception e) {
											e_code.setStringCode(enc_key);
										}

										// update the encoded meaning
										String meaning = emap.get(enc_key);
										e_code.setMeaning(meaning);

										// add the encoding value to the map
										field.getEncodingValues().add(e_code);
									}
								} else {
									log.error("Missing encodings for field: " + field.getCode() + " --> "
											+ field.getEncodingId());
								}
							} else {
								log.error("Missing encodings for field: " + field.getCode() + " --> "
										+ field.getEncodingId());

							}
						}
					}
				}
			}
		}

		log.log("Final question set: " + finalSet.size());

		//
		// Parameters let us define whether or not to generate the schema and dictionary
		//

		//
		// Run this code only if we are NOT generating the schema
		// otherwise you will get column names defined twice for
		// each table
		//
		// Convert categories/fields to SQL Tables
		// even if we are not writing out the schema file
		// we still need to go through this process since
		// it helps setup and define our table structure
		// which we will need to get the CSV header later
		//
		if (!myConfig.isWriteSchema()) {

			log.log("Skipping creating schema");
			for (Category cat : parser.getCategoriesWithData()) {
				String mapType = myConfig.mappedTable(cat.getSQLTableName());
				String sql = cat.getSQL(mapType, myConfig.isGenerateSequences());
			}

		} else {

			int totalCategories = 0;
			int totalFields = 0;
			try {

				PrintWriter schemaWriter = new PrintWriter(myConfig.getDbSchemaFile());

				for (Category cat : parser.getCategoriesWithData()) {

					String mapType = myConfig.mappedTable(cat.getSQLTableName());

					schemaWriter.println("-- ----------------------------------------");
					schemaWriter.println("-- " + cat.getHierarchy());
					schemaWriter.println("-- ----------------------------------------");

					// we will set the column names here when we generate the SQL code
					String sql = cat.getSQL(mapType, myConfig.isGenerateSequences());
					if (sql != null)
						schemaWriter.println(sql);
					else
						log.error("No data...");
					schemaWriter.flush();

					totalCategories++;
					totalFields = totalFields + cat.getDataFields().size();
				}

				//
				// Output the data dictionary
				//
				schemaWriter.println("-- ----------------------------------------");
				schemaWriter.println("-- UKBB Data Dictionary");
				schemaWriter.println("-- ----------------------------------------");

				StringBuilder createDataDictionarySQL = new StringBuilder();
				createDataDictionarySQL.append("DROP TABLE IF EXISTS UKBB_DATA_DICTIONARY;" + "\n");
				createDataDictionarySQL.append("CREATE TABLE UKBB_DATA_DICTIONARY ( " + "\n");
				createDataDictionarySQL.append("    TABLE_NAME VARCHAR(65) NOT NULL, " + "\n");
				createDataDictionarySQL.append("    COLUMN_NAME VARCHAR(65) NOT NULL, " + "\n");
				createDataDictionarySQL.append("    INTEGER_CODE INTEGER, " + "\n");
				createDataDictionarySQL.append("    STRING_CODE VARCHAR(20), " + "\n");
				createDataDictionarySQL.append("    MEANING VARCHAR(500) NOT NULL " + "\n");
				createDataDictionarySQL.append("); " + "\n");

				// create table
				schemaWriter.print(createDataDictionarySQL.toString());

				for (Category cat : parser.getCategoriesWithData()) {

					// update event counts
					schemaWriter.println("-- ----------------------------------------");
					schemaWriter.println("-- " + cat.getHierarchy());
					schemaWriter.println("-- ----------------------------------------");
					schemaWriter.println(cat.getDataDictionary());
				}

				schemaWriter.flush();
				schemaWriter.close();

				log.log("Categories covered: " + totalCategories);
				log.log("Data fields covered: " + totalFields);

			} catch (Exception e) {
				log.error("Error writing SQL file: " + e.toString());
			}
		}

		if (myConfig.isWriteDataFiles() == true) {

			// let's see how many tables have issues like
			// GENOTYPES_GENOTYPING_PROCESS_AND_SAMPLE_QC
			List<Category> dbTables = parser.getCategoriesWithData();
			int counter = 1;

			System.out.println(
					"-----------------------------------------------------------------------------------------------------------");
			System.out.printf("%10s %65s %20s\n", "NUM", "TABLE_NAME", "TYPE");
			System.out.println(
					"-----------------------------------------------------------------------------------------------------------");

			for (Category dbTbl : dbTables) {

				String mapped = myConfig.getPrettyMappedTable(dbTbl.getSQLTableName());
				if (mapped == null)
					mapped = "STANDARD";
				System.out.printf("%10s %65s %25s", counter, dbTbl.getSQLTableName().toLowerCase(), mapped);
				System.out.println();
				counter++;

				HashMap<String, Boolean> hideTypes = new HashMap<>();
				hideTypes.put("STANDARD", true);
				hideTypes.put("COL", true); // col is working
				hideTypes.put("MULTI", true); // multiple choice expansion works
				hideTypes.put("ROW", true); // row is standard

				// if ( hideTypes.containsKey(mapped) == false )
				// dbTbl.testConsistentIndex();
			}

			log.log("-----------------------------------------------------------------------------------------------------------");

			// These all use the array indexing method to insert data
			HashMap<String, Boolean> standardMapTypes = new HashMap<>();
			standardMapTypes.put("STANDARD", true);
			standardMapTypes.put("ROW", true);
			standardMapTypes.put("MULTI", true);
			standardMapTypes.put("IGNORE_MULTI", true);

			log.log("Begin Load Data: " + new Date());

			boolean loadData = true;
			if (loadData == true) {
				if (myConfig.isCsvOutput() == true) {
					log.log("Making CSV files...");
					makeCSVImportFiles(parser, dbTables, standardMapTypes, myConfig.isCompressedOutput());
				} else {
					log.log("SQL output mode");
					makeSQLInsertStatements(dbTables, standardMapTypes);
				}
			}

		} else {
			log.log("Skipping patient data...");
		}

		log.log("Done!");
	}

	private void makeCSVImportFiles(UkbbParser parser, List<Category> dbTables,
			HashMap<String, Boolean> standardMapTypes, boolean compressOutput) {
		try {
			boolean hasHeader = true;

			// get the set of patients who had their consent revoked
			HashMap<Integer, Boolean> ignorePatients = myConfig.getRevokedPatients();

			// generate an output stream for each table in the database
			HashMap<String, java.io.Writer> outputStreams = new HashMap<>();
			HashMap<String, String> outputFiles = new HashMap<>();
			for (Category dbTable : parser.getCategoriesWithData()) {

				String catId = dbTable.getSQLTableName();
				String outfile = myConfig.getCSVTableOutputFile(catId);

				// remove if it already exists and start fresh
				Path pfile = Paths.get(outfile);
				Files.deleteIfExists(pfile);

				// Get the table header
				String tableHeader = dbTable.getCSVHeader(DELIMITER);
				if (StringUtils.isEmpty(tableHeader)) {
					log.error("Error, no table header found for table: " + catId);
				}

				// Test if we want on the fly compression
				if (compressOutput == true) {
					// generate gzip writers
					outfile = outfile + ".gz";

					GzipPrintWriter writer = new GzipPrintWriter(outfile);
					outputStreams.put(catId, writer);
					outputFiles.put(catId, outfile);

					// write the header for this table
					writer.println(tableHeader);
					writer.flush();

				} else {
					// generate new printwriter
					PrintWriter writer = new PrintWriter(outfile);
					outputStreams.put(catId, writer);
					outputFiles.put(catId, outfile);

					// write the header for this table
					writer.println(tableHeader);
					writer.flush();
				}
			}

			long csvRow = 0;
			long patientCounter = 0;
			long totalRows = 0;

			// read the file content into the CSV parser
			Reader in = new FileReader(myConfig.getPatientDataFile());
			CSVParser records = CSVFormat.DEFAULT.parse(in);
			for (CSVRecord record : records) {
				if (hasHeader) {
					hasHeader = false;
				} else {
					// Test if the row is consistent with the header
					if (!record.isConsistent()) {
						log.error("Error on row: " + csvRow);
					} else {
						String[] rowData = StringFormat.csvRecordToStringArray(record);

						// Patient ID is always the first column
						boolean excludePatient = testIfPatientIsRevoked(ignorePatients, rowData[0]);
						if (!excludePatient) {

							List<RowEntry> tableRows = processRow(rowData, dbTables, myConfig, standardMapTypes);

							// write the rows to the file
							for (RowEntry row : tableRows) {
								if (row != null) {
									String cid = row.getDbTable().getSQLTableName();
									if (outputStreams.containsKey(cid) == true) {

										// Compression or not
										if (compressOutput == true) {
											GzipPrintWriter writer = (GzipPrintWriter) outputStreams.get(cid);
											writer.println(row.getCSV(DELIMITER));
										} else {
											PrintWriter writer = (PrintWriter) outputStreams.get(cid);
											writer.println(row.getCSV(DELIMITER));
										}

										totalRows++;

									} else {
										log.error("Missing table to write data: " + cid);
									}
								}
							}

							// new patient row
							patientCounter++;

							// flush after each patient
							flushTables(outputStreams);
						}

					}

					// next row
					csvRow++;
				}
			}

			// flush and close all of our output streams
			for (String catId : outputStreams.keySet()) {
				try {
					if (compressOutput == true) {
						GzipPrintWriter writer = (GzipPrintWriter) outputStreams.get(catId);
						writer.flush();
						writer.close();
					} else {
						PrintWriter writer = (PrintWriter) outputStreams.get(catId);
						writer.flush();
						writer.close();
					}

				} catch (Exception f) {
					log.error("Error closing output stream for table: " + catId);
				}
			}

			// generate the import code
			try {

				// output stream
				PrintWriter writer = new PrintWriter(myConfig.getSQLCopyFile());

				// get the file delimiter
				String myDelimiter = fixTabDelimiter();

				// local or AWS?
				if (myConfig.isLocalDatabase() == true) {
					// this table must come first
					writer.println(getTableCopyCommand(parser, outputFiles, PATIENT_TABLE));
					for (String catId : outputFiles.keySet()) {
						if (catId.contentEquals(PATIENT_TABLE) == false) {
							// test that the file contains output to load...

							writer.println(getTableCopyCommand(parser, outputFiles, catId));
						}
					}
				} else {

					writer.println("--");
					writer.println("-- This is the AWS code to import CSV from S3 buckets");
					writer.println("-- directly into the serverless Postgresql instance");
					writer.println("--");

					// We need the field names to pass into the copy command
					String fieldNames = parser.getCategory("UKBB_PATIENT").getCSVHeader(", ");

					// output the credentials
					writer.println("SELECT aws_commons.create_aws_credentials(" + myConfig.getAwsKey() + ", "
							+ myConfig.getAwsSecret() + ", " + myConfig.getAwsToken() + ") AS creds \\gset");

					// Patient table is first
					String tblName = "UKBB_PATIENT";
					String filename = tblName + ".csv";
					if (myConfig.isCompressedOutput() == true)
						filename = filename + ".gz";

					writer.println("SELECT aws_s3.table_import_from_s3('" + tblName + "', '" + fieldNames
							+ "', '(delimiter E''" + myDelimiter + "'', format csv, header)', "
							+ "aws_commons.create_s3_uri('" + myConfig.getAwsS3Bucket() + "', '"
							+ myConfig.getAwsS3Path() + "/" + filename + "', '" + myConfig.getAwsRegion()
							+ "'), :'creds' );");
					writer.println("");

					// Remaining tables
					for (String table : outputFiles.keySet()) {
						// get field names for this table
						fieldNames = parser.getCategory(table).getCSVHeader(", ");

						if (table.contentEquals(PATIENT_TABLE) == false) {
							filename = table + ".csv";
							if (myConfig.isCompressedOutput() == true)
								filename = filename + ".gz";

							writer.println("SELECT aws_s3.table_import_from_s3('" + table + "', '" + fieldNames
									+ "', '(delimiter E''" + myDelimiter + "'', format csv, header)', "
									+ "aws_commons.create_s3_uri('" + myConfig.getAwsS3Bucket() + "', '"
									+ myConfig.getAwsS3Path() + "/" + filename + "', '" + myConfig.getAwsRegion()
									+ "'), :'creds' );");
							writer.println("");
						}
					}
				}

				writer.flush();
				writer.close();

			} catch (Exception f) {
				log.error("Error writing sql copy file: " + f.toString());
			}

			log.log("Total patients processed: " + patientCounter);
			log.log("Total rows: " + totalRows);
		} catch (Exception e) {
			log.error("Error generating SQL insert statements: " + e.toString());
		}
	}

	/**
	 * Test if a patient should be excluded
	 * 
	 * @param ignorePatients
	 * @param strEid
	 * @return
	 */
	private boolean testIfPatientIsRevoked(HashMap<Integer, Boolean> ignorePatients, String strEid) {

		boolean exclude = false;
		try {
			// test converting to an integer
			int eid = Integer.parseInt(strEid);
			if (ignorePatients.containsKey(eid) == true)
				exclude = true;

		} catch (Exception e) {
			System.err.println("testIfPatientIsRevoked() Unable to parse patient id field: " + strEid);
			exclude = true;
		}
		return exclude;
	}

	private String getTableCopyCommand(UkbbParser parser, HashMap<String, String> outputFiles, String catId) {
		try {
			String filename = outputFiles.get(catId);
			String myDelimiter = fixTabDelimiter();

			Category c = parser.getCategory(catId);
			if (c != null) {
				String fieldNames = parser.getCategory(catId).getCSVHeader(", ");
				String cmd = "\\copy " + catId.toLowerCase().trim() + "(" + fieldNames + ") from '" + filename
						+ "' delimiter E'" + myDelimiter + "' csv header;";
				return cmd;
			} else {
				log.error("Could not locate table in parser: " + catId);
			}
		} catch (Exception e) {
			log.error("Error getting copy command for table: " + catId);
		}

		return "";
	}

	private String fixTabDelimiter() {
		String fix_tab_delim = "\\t";
		String myDelimiter = DELIMITER;
		if (DELIMITER.contentEquals("\t"))
			myDelimiter = fix_tab_delim;
		return myDelimiter;
	}

	/**
	 * Flush all output streams
	 * 
	 * @param outputStreams
	 * @throws IOException
	 */
	private void flushTables(HashMap<String, Writer> outputStreams) throws IOException {

		for (String cid : outputStreams.keySet())
			outputStreams.get(cid).flush();

	}

	private void makeSQLInsertStatements(List<Category> dbTables, HashMap<String, Boolean> standardMapTypes) {
		try {
			boolean hasHeader = true;

			// get the set of patients who had their consent revoked
			HashMap<Integer, Boolean> ignorePatients = myConfig.getRevokedPatients();

			// patient SQL inserts go here
			PrintWriter sqlOutput = new PrintWriter(myConfig.getPatientSqlFile());
			int totalRows = 0;
			int patientCounter = 0;
			int csvRow = 0;

			// read the file content into the CSV parser
			Reader in = new FileReader(myConfig.getPatientDataFile());
			CSVParser records = CSVFormat.DEFAULT.parse(in);
			for (CSVRecord record : records) {
				if (hasHeader) {
					hasHeader = false;
				} else {
					// Test if the row is consistent with the header
					if (!record.isConsistent()) {
						log.error("Error on row: " + csvRow);
					} else {
						String[] rowData = StringFormat.csvRecordToStringArray(record);

						// Patient ID is always the first column
						boolean excludePatient = testIfPatientIsRevoked(ignorePatients, rowData[0]);
						if (!excludePatient) {

							List<RowEntry> tableRows = processRow(rowData, dbTables, myConfig, standardMapTypes);

							// write the rows to the file
							for (RowEntry row : tableRows) {
								if (row != null) {
									sqlOutput.println(row.getSQL());
									totalRows++;
								}
							}

							// new patient row
							patientCounter++;

							// flush after each patient
							sqlOutput.flush();
						}
					}

					// next row
					csvRow++;
				}
			}

			// close SQL file
			sqlOutput.flush();
			sqlOutput.close();

			log.log("Total patients processed: " + patientCounter);
			log.log("Total SQL insert statements: " + totalRows);
		} catch (Exception e) {
			log.error("Error generating SQL insert statements: " + e.toString());
		}
	}

	private List<RowEntry> processRow(String[] rowData, List<Category> dbTables, Config myConfig,
			HashMap<String, Boolean> standardMapTypes) {
		ArrayList<RowEntry> allTableRows = new ArrayList<>();
		String patid = rowData[0];

		//
		// create entries for every table that the patient has data for
		// getCategories method insures the root category is always
		// the first category/table loaded to make sure
		// the referential integrity is maintained
		//
		for (Category dbTbl : dbTables) {

			// what map type is this?
			String tblName = dbTbl.getSQLTableName();
			String mapType = myConfig.mappedTable(tblName);

			// Table rows for this patient
			ArrayList<RowEntry> tableRows = new ArrayList<>();
			HashMap<Integer, Boolean> instances = dbTbl.getUniqueEventPeriods();

			// Null rows are standard rows
			if (standardMapTypes.containsKey(mapType) == true || mapType == null) {
				// iterate over all instances for this table
				for (int instanceId : instances.keySet())
					processStandardTableRows(rowData, patid, dbTbl, tableRows, instanceId, mapType);

			} else {


				if (mapType.contentEquals("COL")) {
					// iterate over all instances for this table
					for (int instanceId : instances.keySet()) {
						// non-array indexed table
						dbTbl.setMultiColumn(true);
						processColRepeatTableRows(rowData, patid, dbTbl, tableRows, instanceId);
					}

				}

			}

			// add the tables
			allTableRows.addAll(tableRows);
		}

		return allTableRows;
	}

	/**
	 * Process a single table row that has a column with repeats
	 * 
	 * @param record
	 * @param patid
	 * @param dbTbl
	 * @param tableRows
	 * @param instanceId
	 * @return
	 */
	private void processColRepeatTableRows(String[] record, String patid, Category dbTbl, ArrayList<RowEntry> tableRows,
			int instanceId) {

		boolean hasData = testColRepeatForData(record, dbTbl, instanceId);
		if (hasData) {

			// Create a new row for the patient
			RowEntry row = new RowEntry();
			row.setPatId(patid);
			row.setInstanceId(instanceId);
			row.setArrayIndex(0);
			row.setDbTable(dbTbl);

			// Track duplicate field names
			HashMap<String, Integer> fieldNames = new HashMap<>();

			// convert data fields to sql entries
			for (DataField field : dbTbl.getSingleSortedDataFields()) {
				// Multiple choice expansion is handled here
				if (field.isMultipleChoice()) {
					// Convert multiple choice questions into integer entries
					extractMultiChoiceQuestion(record, row, fieldNames, field, instanceId);

				} else {

					String fieldName = field.getSQLFieldName();
					fieldName = StringFormat.testDuplicateFieldName(fieldNames, fieldName, this.reserveWords);

					int col_idx = -1;
					for (DataDictionaryCode code : field.getCodeEntries()) {
						// non-array indexed variable
						if (code.getInstanceId() == instanceId && code.getArrayIndex() == 0) {
							col_idx = code.getCsvColumn();
						}
					}

					// test for column assignment
					if (col_idx > 0) {
						String value = record[col_idx];
						row.addEntry(field, fieldName, value);
					}
				}

			}

			for (DataField field : dbTbl.getMultiColDataFieldsSorted()) {

				if (field.isMultipleChoice()) {
					// Convert multiple choice questions into integer entries
					extractMultiChoiceQuestion(record, row, fieldNames, field, instanceId);
				} else {

					// add multiple columns for this field
					for (int arrayIndex = 0; arrayIndex <= field.getMaxArrayIndex(); arrayIndex++) {
						// add field
						String fieldName = field.getSQLFieldName() + "_" + arrayIndex;
						fieldName = StringFormat.testDuplicateFieldName(fieldNames, fieldName, this.reserveWords);

						int col_idx = -1;
						for (DataDictionaryCode code : field.getCodeEntries()) {
							if (code.getInstanceId() == instanceId && code.getArrayIndex() == arrayIndex) {
								col_idx = code.getCsvColumn();
							}
						}

						// test for column assignment
						if (col_idx > 0) {
							String value = record[col_idx];
							row.addEntry(field, fieldName, value);
						}

					}
				}
			}

			// check if the row is good
			if (row != null && row.hasData() == true)
				tableRows.add(row);
		}
		return;
	}

	/**
	 * Process a single table row that has a column with repeats
	 * 
	 * @param record
	 * @param patid
	 * @param dbTbl
	 * @param tableRows
	 * @param instanceId
	 * @return
	 */
	private boolean testColRepeatForData(String[] record, Category dbTbl, int instanceId) {

		// convert data fields to sql entries
		for (DataField field : dbTbl.getSingleSortedDataFields()) {
			// Multiple choice expansion is handled here
			if (field.isMultipleChoice()) {
				if (testMultiForEmpty(record, field, instanceId) == true)
					return true;
			} else {

				int col_idx = -1;
				for (DataDictionaryCode code : field.getCodeEntries()) {
					// non-array indexed variable
					if (code.getInstanceId() == instanceId && code.getArrayIndex() == 0) {
						col_idx = code.getCsvColumn();
					}
				}

				// test for column assignment
				if (col_idx > 0) {
					String value = record[col_idx];
					if (!StringUtils.isEmpty(value))
						return true;
				}
			}
		}

		for (DataField field : dbTbl.getMultiColDataFieldsSorted()) {

			if (field.isMultipleChoice()) {
				if (testMultiForEmpty(record, field, instanceId) == true)
					return true;
			} else {

				for (int arrayIndex = 0; arrayIndex <= field.getMaxArrayIndex(); arrayIndex++) {
					int col_idx = -1;
					for (DataDictionaryCode code : field.getCodeEntries()) {
						if (code.getInstanceId() == instanceId && code.getArrayIndex() == arrayIndex) {
							col_idx = code.getCsvColumn();
						}
					}

					// test for column assignment
					if (col_idx > 0) {
						String value = record[col_idx];
						if (!StringUtils.isEmpty(value))
							return true;
					}

				}
			}
		}

		return false;
	}

	private boolean testMultiForEmpty(String[] record, DataField field, int instanceId) {
		// is it string or integer encoded?
		if (field.isIntegerEncoded()) {
			for (int col_idx : field.getColumns(instanceId)) {
				String value = record[col_idx];
				if (!StringUtils.isEmpty(value)) {
					return true;
				}
			}
		} else {
			// String encoded values
			for (int col_idx : field.getColumns(instanceId)) {
				String value = record[col_idx].trim();
				if (!StringUtils.isEmpty(value))
					return true;
			}
		}
		return false;
	}

	private void extractMultiChoiceQuestion(String[] record, RowEntry row, HashMap<String, Integer> fieldNames,
			DataField field, int instanceId) {

		// this will store the values for each option we read
		HashMap<String, String> columnValues = new HashMap<>();
		List<String> columnNames = new ArrayList<>();
		for (Coding encode : field.getEncodingValuesOrdered()) {
			boolean hasCode = false;
			String fieldName = encode.getSQLFieldName();
			fieldName = StringFormat.testDuplicateFieldName(fieldNames, fieldName, this.reserveWords);

			// keep these ordered
			columnNames.add(fieldName);

			// is it string or integer encoded?
			if (field.isIntegerEncoded()) {
				// Integer encoded values
				int encodeValue = encode.getIntegerCode();
				List<Integer> columns = field.getColumns(instanceId);
				for (int col_idx : columns) {

					String value = record[col_idx];
					if (!StringUtils.isEmpty(value)) {
						try {
							int testVal = Integer.parseInt(value);
							if (testVal == encodeValue)
								hasCode = true;
						} catch (Exception e) {
						}
					}
				}
			} else {
				// String encoded values
				String encodeValue = encode.getStringCode().trim();
				List<Integer> columns = field.getColumns(instanceId);
				for (int col_idx : columns) {
					String value = record[col_idx].trim();
					if (!StringUtils.isEmpty(value))
						if (value.contentEquals(encodeValue))
							hasCode = true;
				}
			}

			// update values
			if (hasCode)
				columnValues.put(fieldName, "1");
			else
				columnValues.put(fieldName, "0");

		}

		// Add each column as a row entry in the proper order
		for (String fieldName : columnNames) {
			if (columnValues.containsKey(fieldName)) {
				row.addEntry(field, fieldName, columnValues.get(fieldName));
			} else {
				row.addEntry(field, fieldName, "");
			}
		}
	}

	private void processStandardTableRows(String[] record, String patid, Category dbTbl, ArrayList<RowEntry> tableRows,
			int instanceId, String mapType) {


		// Test for multiple choice questions
		boolean hasMultipleChoice = dbTbl.hasMultipleChoice();
		if ( mapType.contentEquals("IGNORE_MULTI") )
			hasMultipleChoice = false;

		// assume no array indexes
		int maxArrayIndex = -1;
		if (dbTbl.hasMixedArrayIndexes() && hasMultipleChoice == false)
			maxArrayIndex = dbTbl.getMaxArrayIndex();

		// more than one array index?
		if (maxArrayIndex > 0) {
			for (int arrayIndex = 0; arrayIndex < maxArrayIndex; arrayIndex++) {

				// test for data before we add
				boolean hasData = testForEmpty(record, dbTbl, patid, instanceId, arrayIndex, mapType);

				if (hasData) {
					// Extract the data for this table, instance and array index
					RowEntry row = getTableValues(record, dbTbl, patid, instanceId, arrayIndex);
					if (row != null && row.hasData() == true)
						tableRows.add(row);
				}
			}
		} else {

			// Single row to extract
			boolean hasData = testForEmpty(record, dbTbl, patid, instanceId, 0, mapType);
			if (hasData) {
				RowEntry row = getTableValues(record, dbTbl, patid, instanceId, 0);
				if (row != null && row.hasData() == true)
					tableRows.add(row);
			} else {

				// always add our primary key table
				if (dbTbl.getSQLTableName().contentEquals(PATIENT_TABLE)) {
					RowEntry row = getTableValues(record, dbTbl, patid, instanceId, 0);
					if (row != null && row.hasData() == true)
						tableRows.add(row);

				}
			}
		}
	}

	/**
	 * Test if any data exists for this patient at this instance and array index
	 * value
	 * 
	 * @param record
	 * @param dbTbl
	 * @param patId
	 * @param instanceId
	 * @param arrayIndex
	 * @return
	 */
	private boolean testForEmpty(String[] record, Category dbTbl, String patId, int instanceId, int arrayIndex, String mapType) {
		// We just need to scan the columns and see if there is any data
		for (DataField field : dbTbl.getSortedDataFields()) {

			// test multiple choice questions for data
			if (field.isMultipleChoice() == true && !mapType.contentEquals("IGNORE_MULTI")) {
				if (testMultiForEmpty(record, field, instanceId) == true)
					return true;
			} else {

				// all other type of questions
				int col_idx = -1;
				for (DataDictionaryCode code : field.getCodeEntries()) {
					if (code.getInstanceId() == instanceId && code.getArrayIndex() == arrayIndex) {
						col_idx = code.getCsvColumn();
					}

					if (col_idx > 0) {
						String val = record[col_idx];
						if (!StringUtils.isEmpty(val))
							return true;
					}
				}
			}
		}
		return false;
	}

	private RowEntry getTableValues(String[] record, Category dbTbl, String patId, int instanceId, int arrayIndex) {

		// Create a new row for the patient
		RowEntry row = new RowEntry();
		row.setPatId(patId);
		row.setInstanceId(instanceId);
		row.setArrayIndex(arrayIndex);
		row.setDbTable(dbTbl);

		// Track duplicate field names
		HashMap<String, Integer> fieldNames = new HashMap<>();

		// Add all column values
		for (DataField field : dbTbl.getSortedDataFields()) {

			// Multiple choice expansion is handled here
			if (field.isMultipleChoice()) {

				// Convert multiple choice questions into integer entries
				extractMultiChoiceQuestion(record, row, fieldNames, field, instanceId);

			} else {

				String fieldName = field.getSQLFieldName();
				fieldName = StringFormat.testDuplicateFieldName(fieldNames, fieldName, this.reserveWords);

				int col_idx = -1;
				for (DataDictionaryCode code : field.getCodeEntries()) {
					if (code.getInstanceId() == instanceId && code.getArrayIndex() == arrayIndex) {
						col_idx = code.getCsvColumn();
					}
				}

				// test for column assignment
				if (col_idx > 0) {
					String value = record[col_idx];
					row.addEntry(field, fieldName, value);
				} else {

					// special case - just recording unique patient id numbers
					if (col_idx == -1 && dbTbl.getSQLTableName().contentEquals(PATIENT_TABLE)) {
						row.addEntry(field, fieldName, patId);
					} else {
						//
						// we have a blank entry - many tables like Process completion times
						// have entries across different instances but do not record
						// values for all entries in that particular instace
						//
						row.addEntry(field, fieldName, "");
					}
				}
			}
		}
		return row;
	}

}
