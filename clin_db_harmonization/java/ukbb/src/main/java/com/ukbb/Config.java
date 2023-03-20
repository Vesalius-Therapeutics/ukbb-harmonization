package com.ukbb;

import java.io.BufferedReader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

@Data
public class Config {

	// properties file
	String propertyFile;
	String tableFile;
	String rebuildTableFile;

	// patient data may be in a different location
	String patientDataDir;
	String configDataDir;
	String outputDataDir;

	// input files
	String fieldFile;
	String categoryFile;
	String dataDictionaryFile;
	String dataEncodingFile;
	String patientDataFile;

	// AWS params
	boolean localDatabase;
	String awsDbName;
	String awsKey;
	String awsSecret;
	String awsToken;
	String awsRegion;
	String awsS3Bucket;
	String awsS3Path;

	// Some fields we do not need (duplicated in HESIN tables)
	String ignoreFieldFile;
	String reservedWordFile;

	// Some patients must be excluded due to withdrawn consent
	String ignorePatientFile;

	// output files
	String dbSchemaFile;
	String patientSqlFile;

	// All table types go here
	HashMap<String, String> tableTypes;

	// do we want SQL or CSV output files?
	boolean csvOutput;
	String csvDelimiter;

	// compressed output?
	boolean compressedOutput;

	boolean writeSchema;
	boolean writeDataFiles;
	boolean generateSequences;
	boolean rebuildTables;

	String workingDirectory;
	private static String PROJECT_DIRECTORY = "${project.basedir}";

	public Config(String propertiesFile) {

		// defaults
		this.csvOutput = true;
		this.compressedOutput = false;
		this.writeSchema = true;
		this.writeDataFiles = true;
		this.generateSequences = true;
		this.rebuildTables = false;

		// set default delimiter
		this.csvDelimiter = ",";

		// Set our working directory once globbaly
		this.workingDirectory = System.getProperty("user.dir");

		this.propertyFile = propertiesFile;
		this.setupPaths();

		// assign table types
		this.tableTypes = new HashMap<>();
		this.setupTableDefinitions();
	}

	public HashMap<Integer, Boolean> getRevokedPatients() {
		// use a hashmap to prevent duplicates
		HashMap<Integer, Boolean> revokedPatients = new HashMap<>();
		if (StringUtils.isNotEmpty(this.ignorePatientFile)) {
			try {

				File file = new File(this.ignorePatientFile);
				if (file.exists()) {

					BufferedReader reader;
					reader = new BufferedReader(new FileReader(this.ignorePatientFile));
					String line = reader.readLine();
					while (line != null) {
						// ignore comment lines
						if (StringUtils.isNotEmpty(line) && !line.startsWith("#")) {
							try {
								int eid = Integer.parseInt(line.trim());
								revokedPatients.put(eid, true);
							} catch (Exception f) {
								// fail silently on non-integer based lines
							}
						}

						line = reader.readLine();
					}
					reader.close();
					System.out.println("Revoked patients: " + revokedPatients.size());

				} else {
					System.err.println("Could not find IGNORE_PATIENT_FILE: " + this.ignorePatientFile);
				}

			} catch (Exception e) {
				System.err.println("Error processing the IGNORE_PATIENT_FILE: " + e.toString());
			}
		}

		// no patients found, return an empty list
		return revokedPatients;
	}

	public String getPrettyMappedTable(String tbl) {

		// LC everything
		tbl = tbl.toLowerCase();

		List<String> tables = this.getMultiQuestionTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "MULTIPLE_CHOICE";

		tables = this.getIgnoreMultipleChoiceTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "IGNORE_MULTIPLE_CHOICE";

		tables = this.getRowTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "ROW_ARRAY_INDEX";

		tables = this.getColumnTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "COL_ARRAY_INDEX";

		tables = this.getMixedTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "MIXED (Needs Work)";

		// these tables should not be built
		tables = this.getExcludeTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "Excluded (No data)";

		return null;
	}

	public String mappedTable(String tbl) {

		tbl = tbl.toLowerCase();

		List<String> tables = this.getMultiQuestionTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "MULTI";

		// don't flatten these multi-choice questions
		tables = this.getIgnoreMultipleChoiceTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "IGNORE_MULTI";

		tables = this.getRowTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "ROW";

		tables = this.getColumnTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "COL";

		tables = this.getMixedTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "MIXED";

		// these tables should not be built
		tables = this.getExcludeTables();
		for (String t : tables)
			if (tbl.contentEquals(t.toLowerCase()))
				return "NONE";

		// default to ROW repeat table
		return "ROW";
	}

	/**
	 * These tables need more manual work as they have both types
	 */
	public List<String> getMixedTables() {

		Map<String, String> result = this.tableTypes.entrySet().stream()
				.filter(map -> map.getValue().toLowerCase().contentEquals("mixed")) // filter by value
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

	/**
	 * These tables have been inspected and contain no data
	 */
	public List<String> getExcludeTables() {

		Map<String, String> result = this.tableTypes.entrySet().stream()
				.filter(map -> map.getValue().toLowerCase().contentEquals("exclude")) // filter by value
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

	/**
	 * For these, the array index should be used to map common elements (items
	 * without an array index), to matched pairs of columns ( a, b, c ) are common
	 * and ( d, e, f ) are indexed 1..4 then a row would always include ( a, b, c, )
	 * and ( d_1, e_1, f_1 ) etc.
	 * 
	 * These do not need special handling, our standard way accounts for this...
	 * 
	 */
	public List<String> getRowTables() {

		Map<String, String> result = this.tableTypes.entrySet().stream()
				.filter(map -> map.getValue().toLowerCase().contentEquals("row_table")) // filter by value
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

	/**
	 * For these tables, the array index indicates col_1, col_2, etc.
	 * 
	 * @return
	 */
	public List<String> getColumnTables() {

		Map<String, String> result = this.tableTypes.entrySet().stream()
				.filter(map -> map.getValue().toLowerCase().contentEquals("column_table")) // filter by value
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

	/**
	 * These tables contain multiple choice questions after further inspection, all
	 * array indexed fields in these tables should be represented as column repeats
	 * (col_1, col_2, etc)
	 * 
	 * @return
	 */

	public List<String> getMultiQuestionTables() {

		Map<String, String> result = this.tableTypes.entrySet().stream()
				.filter(map -> map.getValue().toLowerCase().contentEquals("multichoice")) // filter by value
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

	/**
	 * For these tables, we don't want to flatten the multi-choice question
	 * 
	 * @return
	 */
	public List<String> getIgnoreMultipleChoiceTables() {

		Map<String, String> result = this.tableTypes.entrySet().stream()
				.filter(map -> map.getValue().toLowerCase().contentEquals("ignore_multi")) // filter by value
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

	private static String fixPath(String path) {
		if (StringUtils.isNotEmpty(path)) {
			String remove = "/\"/\"";
			path = path.replace(remove, "/");

			remove = "/\"/";
			path = path.replace(remove, "/");

			if (path.startsWith("\""))
				path = path.substring(1, path.length());

			if (path.endsWith("\""))
				path = path.substring(0, path.length() - 1);

			return path;
		}
		return null;
	}

	private static String quoteIfNotVar(String s) {
		if (s.startsWith(":")) {
			return ":'" + s.substring(1) + "'";
		} else {
			return "'" + s + "'";
		}
	}

	private String replaceProjectDirectory(String path) {
		if (path.contains(PROJECT_DIRECTORY))
			path = path.replace(PROJECT_DIRECTORY, this.getWorkingDirectory());
		return fixPath(path);
	}

	public void setupTableDefinitions() {
		try {

			File file = new File(this.tableFile);
			if (file.exists()) {

				Properties props = new Properties();
				InputStream input = new DataInputStream(new FileInputStream(file));
				props.load(input);

				// Store all table types
				props.forEach((k, v) -> {
					String tableName = k.toString();
					String tableType = v.toString();
					this.tableTypes.put(tableName, tableType);
				});

				input.close();
				System.out.println("Loaded table types: " + this.tableTypes.size());

			} else {
				System.err.println("Table settings file not found!");
			}
		} catch (Exception e) {
			System.err.println("Error loading table settings: " + e.toString());
		}
	}

	/**
	 * Set up global paths for data input and output files
	 */
	public void setupPaths() {
		try {

			File file = new File(this.propertyFile);
			if (file.exists()) {

				Properties props = new Properties();
				InputStream input = new DataInputStream(new FileInputStream(file));
				props.load(input);

				// ------------------------------------------------------------
				// Set our output modes
				// ------------------------------------------------------------
				this.setWriteSchema(isPropertyValueTrue(props.getProperty("CREATE_SCHEMA")));
				this.setWriteDataFiles(isPropertyValueTrue(props.getProperty("CREATE_DATA")));
				this.setCsvOutput(isPropertyValueTrue(props.getProperty("CSV_OUTPUT")));
				this.setGenerateSequences(isPropertyValueTrue(props.getProperty("GENERATE_SEQUENCE")));
				this.setRebuildTables(isPropertyValueTrue(props.getProperty("REBUILD_TABLES")));

				// Use the default delimiter unless one is provided
				setDelimiterCharacter(props);

				this.setCompressedOutput(isPropertyValueTrue(props.getProperty("COMPRESS_OUTPUT")));

				// Assign the work directories
				this.configDataDir = replaceProjectDirectory(props.getProperty("CONFIG_DIR"));
				this.patientDataDir = replaceProjectDirectory(props.getProperty("DATA_DIR"));
				this.outputDataDir = replaceProjectDirectory(props.getProperty("OUTPUT_DIR"));

				//
				// Configuration files
				//
				this.fieldFile = getConfigFilePath(props, "FIELD_FILE");
				this.ignoreFieldFile = getConfigFilePath(props, "IGNORE_FIELD_FILE");
				this.ignorePatientFile = getConfigFilePath(props, "IGNORE_PATIENT_FILE");
				this.reservedWordFile = getConfigFilePath(props, "RESERVE_WORD_FILE");
				this.categoryFile = getConfigFilePath(props, "CAT_FILE");
				this.dataDictionaryFile = getConfigFilePath(props, "DATA_DICTIONARY_FILE");
				this.dataEncodingFile = getConfigFilePath(props, "DATA_ENCODING_FILE");
				this.tableFile = getConfigFilePath(props, "TABLE_TYPES");
				this.rebuildTableFile = getConfigFilePath(props, "REBUILD_TABLE_TYPES");

				//
				// Data files
				//
				if (props.getProperty("PATIENT_IN_FILE").startsWith("/")) {
					this.patientDataFile = props.getProperty("PATIENT_IN_FILE");
				} else {
					this.patientDataFile = this.getPatientDataDir() + "/" + props.getProperty("PATIENT_IN_FILE");
				}
				this.patientDataFile = fixPath(this.patientDataFile);

				// output files
				this.dbSchemaFile = this.getOutputDataDir() + "/" + props.getProperty("SCHEMA_FILE");
				this.dbSchemaFile = fixPath(this.dbSchemaFile);

				this.patientSqlFile = this.getOutputDataDir() + "/" + props.getProperty("PATIENT_OUT_FILE");
				this.patientSqlFile = fixPath(this.patientSqlFile);

				//
				// AWS database parameters
				//
				this.setLocalDatabase(isPropertyValueTrue(props.getProperty("LOCAL_DATABASE")));
				this.setAwsDbName(props.getProperty("AWS_DB_NAME"));
				this.setAwsKey(quoteIfNotVar(props.getProperty("AWS_KEY")));
				this.setAwsSecret(quoteIfNotVar(props.getProperty("AWS_SECRET")));
				this.setAwsToken(quoteIfNotVar(props.getProperty("AWS_TOKEN")));
				this.setAwsRegion(props.getProperty("AWS_REGION"));
				this.setAwsS3Bucket(props.getProperty("AWS_S3_BUCKET"));
				this.setAwsS3Path(props.getProperty("AWS_S3_PATH"));

				// ------------------------------------------------------------
				System.out.println("Property File Loaded Succesfully");
				// ------------------------------------------------------------

			} else {
				System.err.println("Cannot find properties file: " + this.getPropertyFile());
			}
		} catch (Exception e) {
			System.err.println("Error setting up configuration properties: " + e.toString());
		}
	}

	/**
	 * Set the CSV output character delimiter If it fails, we will keep the default
	 * comma delimiter
	 * 
	 * @param props
	 */
	private void setDelimiterCharacter(Properties props) {
		if (StringUtils.isNotEmpty(props.getProperty("CSV_DELIMITER"))) {
			String delimiter = props.getProperty("CSV_DELIMITER").replaceAll("\"", "");
			if (delimiter.length() == 1)
				this.setCsvDelimiter(delimiter);
			else
				System.err.println("Delimiter cannot be more than a single char: " + delimiter);
		} else {
			// skip, use the comma as the default
		}
	}

	/**
	 * Helper method to determine if a value was set to true or not
	 * 
	 * Acceptable true values include any case variation of "T", "True" or "1" all
	 * others will be considered false.
	 * 
	 * @param testPropertyValue
	 * @return
	 */
	private static boolean isPropertyValueTrue(String testPropertyValue) {
		HashMap<String, Boolean> trueStatements = new HashMap<>();
		trueStatements.put("t", true);
		trueStatements.put("true", true);
		trueStatements.put("1", true);

		if (StringUtils.isNotEmpty(testPropertyValue)) {
			testPropertyValue = testPropertyValue.toLowerCase().trim();
			if (trueStatements.containsKey(testPropertyValue))
				return true;
		}
		return false;
	}

	/**
	 * Find a config file and it's full path if it exists
	 * 
	 * @param props
	 * @param propertyName
	 * @return
	 */
	private String getConfigFilePath(Properties props, String propertyName) {
		try {
			String path = this.getConfigDataDir() + "/" + props.getProperty(propertyName);
			return fixPath(path);
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * Setup the output path for a CSV table file
	 * 
	 * @param catId
	 * @return
	 */
	public String getCSVTableOutputFile(String catId) {

		if (!StringUtils.isEmpty(catId))
			return fixPath(this.getOutputDataDir()) + "/" + catId + ".csv";
		else
			return null;
	}

	public String getSQLCopyFile() {
		return fixPath(this.getOutputDataDir()) + "/ukbb_copy_csv_to_tables.sql";
	}

	
	public void setTablesToRebuild() {
		try {

			// Reset all of our table types
			this.tableTypes = new HashMap<>();
			
			File file = new File(this.rebuildTableFile);
			if (file.exists()) {
				
				Properties props = new Properties();
				InputStream input = new DataInputStream(new FileInputStream(file));
				props.load(input);

				// Store all table types
				props.forEach((k, v) -> {
					String tableName = k.toString();
					String tableType = v.toString();
					this.tableTypes.put(tableName, tableType);
				});

				input.close();
				System.out.println("Loaded rebuild table types: " + this.tableTypes.size());

			} else {
				System.err.println("Rebuild table settings file not found!");
			}
		} catch (Exception e) {
			System.err.println("Error loading rebuild table settings: " + e.toString());
		}
	}
	
	
	public List<String> getTablesToRebuild() {

		// first update list of tables...
		this.setTablesToRebuild();

		// now get all of our tables
		Map<String, String> result = this.tableTypes.entrySet().stream()
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

		return result.keySet().stream().collect(Collectors.toList());
	}

}
