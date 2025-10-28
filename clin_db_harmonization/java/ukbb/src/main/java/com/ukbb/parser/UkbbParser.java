package com.ukbb.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import com.ukbb.Config;
import com.ukbb.om.Category;
import com.ukbb.om.DataDictionaryCode;
import com.ukbb.om.DataField;

public class UkbbParser {

	private String url;
	private List<Category> roots;
	private HashMap<Integer, Category> categories;
	private Category rootCategory;
	private Config myConfig;
	HashMap<String, Boolean> reserveWords;

	public UkbbParser(Config conf) {
		this.categories = new HashMap<>();
		this.roots = new ArrayList<>();
		this.rootCategory = null;
		this.myConfig = conf;
		this.reserveWords = new HashMap<>();
	}

	public static void log(String msg) {
		System.out.println(msg);
	}

	public static int getCategoryId(String url) {

		int cat_id = 0;
		if (!StringUtils.isEmpty(url)) {
			if (url.contains("?")) {
				String[] params = url.split("\\?");
				String[] values = params[1].split("=");
				try {
					cat_id = Integer.parseInt(values[1]);
				} catch (Exception e) {

				}
			}
		}
		return cat_id;
	}

	public List<Category> getCategoriesWithData() {
		List<Category> cats = new ArrayList<>();

		// always add the root category first
		if (this.rootCategory != null)
			cats.add(this.rootCategory);

		// add all the other categories
		for (int catId : this.categories.keySet()) {
			Category c = this.categories.get(catId);
			if (c.isRoot() == false && c.getDataFields().size() > 0)
				cats.add(c);
		}
		return cats;
	}

	/**
	 * Load database reserved words
	 * 
	 * @param infile
	 * @return
	 */
	public HashMap<String, Boolean> loadReserveWords(String infile) {
		HashMap<String, Boolean> reserved_words = new HashMap<>();
		boolean has_header = true;
		BufferedReader reader;
		try {

			reader = new BufferedReader(new FileReader(infile));
			String line = reader.readLine();
			while (line != null) {
				if (has_header == true) {
					has_header = false;
				} else {

					line = line.trim();
					if (line.contains("|")) {
						String[] elements = line.split("\\|");
						String word = elements[0].trim().toUpperCase();
						if (StringUtils.isEmpty(word) == false)
							reserved_words.put(word, true);
					}

				}
				line = reader.readLine();
			}

			reader.close();

		} catch (Exception e) {
			System.err.println("Error loading reserve words: " + e.toString());
		}

		System.out.println("Reserve words:  " + reserved_words.size());

		// keep local copy
		this.reserveWords = reserved_words;
		return reserved_words;

	}

	/**
	 * Load fields that we should ignore since they are covered in other data
	 * outside the large UKBB data file
	 * 
	 * @param infile
	 * @return
	 */
	public HashMap<String, Boolean> loadIgnoreFields(String infile) {
		HashMap<String, Boolean> ignore_fields = new HashMap<>();
		boolean has_header = true;
		BufferedReader reader;
		try {

			reader = new BufferedReader(new FileReader(infile));
			String line = reader.readLine();
			while (line != null) {
				if (has_header == true) {
					has_header = false;
				} else {

					line = line.trim();

					// test that it isn't a comment line
					if (!line.startsWith("#")) {
						String code = line;
						if (line.matches(".*\\s.*"))
							code = line.split("\\s+")[0];

						try {
							// if the code parses into an integer successfully, then we have a valid code
							int int_code = Integer.parseInt(code);
							if (int_code > 0)
								ignore_fields.put(code, true);
						} catch (Exception e) {
							// pass
						}
					}

				}
				line = reader.readLine();
			}

			reader.close();

		} catch (Exception e) {
			System.err.println("Error loading codes to ignore: " + e.toString());
		}

		System.out.println("Fields to ignore:  " + ignore_fields.size());
		return ignore_fields;

	}

	public HashMap<String, DataField> loadAllFields(String infile) {

		HashMap<String, DataField> all_fields = new HashMap<>();
		boolean has_header = true;
		BufferedReader reader;
		try {

			reader = new BufferedReader(new FileReader(infile));
			String line = reader.readLine();
			while (line != null) {
				if (has_header == true) {
					has_header = false;
				} else {
					String[] fields = line.split("\t");
					String code = fields[0];
					String code_desc = fields[1];

					// debug to get list of pilot q's to ignore
					// if ( code_desc.toLowerCase().contains("pilot"))
					// {
					// System.out.println(code + " --> " + code_desc);
					// }

					// type values
					int valueType = Integer.parseInt(fields[5]);
					int baseType = Integer.parseInt(fields[6]);
					int itemType = Integer.parseInt(fields[7]);

					String units = fields[12];
					int cat_id = Integer.parseInt(fields[13]);
					int code_id = Integer.parseInt(fields[14]);
					String notes = fields[20];
					int item_count = Integer.parseInt(fields[24]);
					double showcaseOrder = Double.parseDouble(fields[25]);

					DataField field = new DataField(this.myConfig);
					field.setCode(code);
					field.setDescription(code_desc);
					field.setValueType(valueType);
					field.setBaseType(baseType);
					field.setItemType(itemType);
					field.setUnits(units);
					field.setNotes(notes);
					field.setCategoryId(cat_id);
					field.setShowcaseOrder(showcaseOrder);
					field.setCount(item_count);

					if (code_id > 0)
						field.setEncodingId(code_id);

					all_fields.put(code, field);
				}
				line = reader.readLine();
			}

			reader.close();

		} catch (Exception e) {
			System.err.println("Error loading codes: " + e.toString());
		}

		System.out.println("All fields loaded: " + all_fields.size());
		return all_fields;

	}

	/**
	 * Get the categories loaded
	 * 
	 * @return
	 */
	public HashMap<Integer, Category> getCategories() {
		return this.categories;
	}

	/**
	 * Set the categories
	 * 
	 * @param cat
	 */
	public void setCategories(HashMap<Integer, Category> cat) {
		this.categories = cat;
	}

	/**
	 * Count categories loaded
	 * 
	 * @return
	 */
	public int getTotalCategories() {
		return this.categories.size();
	}

	/**
	 * Get a single category based on key value
	 * 
	 * @param key
	 * @return
	 */
	public Category getCategoryEntry(int key) {

		if (this.categories.containsKey(key) == true)
			return this.categories.get(key);
		return null;
	}

	/**
	 * Set the encodings
	 * 
	 * @param dictionaryFile
	 * @return
	 */
	public HashMap<Integer, HashMap<String, String>> setEncodings(String dictionaryFile) {

		HashMap<Integer, HashMap<String, String>> encodings = new HashMap<>();
		try {

			File input = new File(dictionaryFile);
			Document doc = Jsoup.parse(input, "UTF-8", "http://biobank.ctsu.ox.ac.uk/crystal/");

			// Find all tables
			Elements tables = doc.getElementsByTag("table");
			for (Element table : tables) {

				if (table.hasAttr("summary")) {
					HashMap<String, String> encodingAttributes = new HashMap<>();
					String dtype = table.attr("summary");
					String[] data = dtype.split(" ");
					if (data.length == 2) {

						int code_id = Integer.parseInt(data[1].trim());
						if (encodings.containsKey(code_id) == true)
							encodingAttributes = encodings.get(code_id);
						else {
							encodings.put(code_id, encodingAttributes);
						}
					}

					for (Node node : table.childNodes()) {

						if (Element.class.isInstance(node) == true) {

							Element entry = (Element) node;
							if (entry.tagName().contentEquals("tbody")) {

								for (Node row : entry.childNodes()) {
									if (Element.class.isInstance(row) == true) {
										Element elRow = (Element) row;
										if (elRow.tagName().contentEquals("tr")) {

											List<String> columns = new ArrayList<>();
											for (Node column : row.childNodes()) {
												if (Element.class.isInstance(column) == true) {
													Element elCol = (Element) column;
													if (elCol.tagName().contentEquals("td")) {
														// we need all of the text since there
														// is a link inside this field from the dictionary file
														columns.add(elCol.text());
													}
												}
											}

											if (columns.size() >= 3) {
												try {

													String code = columns.get(1).trim();

													// says not selectable, but we have patients with this code!
													code = code.replace("(not selectable)", "");

													String meaning = columns.get(2).trim();
													encodingAttributes.put(code, meaning);
												} catch (Exception e) {
													System.err.println("Error processing encoding: " + e.toString());
												}
											}

											// end row
										}
									}
								}

							}

						}
					}
				}
			}

		} catch (Exception e) {
			System.err.println("Error: " + e.toString());
		}

		return encodings;

	}

	public HashMap<String, Boolean> setAvailableFields(String dictionaryFile, HashMap<String, DataField> all_fields,
			HashMap<String, Boolean> ignore_fields) {

		boolean debug = false;
		HashMap<String, Boolean> availableFields = new HashMap<>();
		try {

			File input = new File(dictionaryFile);
			Document doc = Jsoup.parse(input, "UTF-8", "http://biobank.ctsu.ox.ac.uk/crystal/");

			// Find all tables
			Elements tables = doc.getElementsByTag("table");
			for (Element table : tables) {

				for (Node node : table.childNodes()) {

					if (Element.class.isInstance(node) == true) {

						Element entry = (Element) node;
						if (entry.tagName().contentEquals("tbody")) {

							for (Node row : entry.childNodes()) {
								if (Element.class.isInstance(row) == true) {
									Element elRow = (Element) row;
									if (elRow.tagName().contentEquals("tr")) {

										List<String> columns = new ArrayList<>();
										for (Node column : row.childNodes()) {
											if (Element.class.isInstance(column) == true) {
												Element elCol = (Element) column;
												if (elCol.tagName().contentEquals("td")) {
													// we need all of the text since there
													// is a link inside this field from the dictionary file
													columns.add(elCol.text());
												}
											}
										}

										if (columns.size() > 2) {
											// build/update cateogry data field
											int col_idx = Integer.parseInt(columns.get(0));
											String code = columns.get(1);
											DataDictionaryCode ddc = this.extractCodeInfo(code);
											if (ddc != null) {
												if (ignore_fields.containsKey(ddc.getCode()) == true) {
													if (debug) {
														System.err.println(
																"Ignoring code: " + code + " --> " + ddc.getCode());
													}
												} else {
													ddc.setCsvColumn(col_idx);
													if (all_fields.containsKey(ddc.getCode())) {
														// update CSV columns
														DataField field = all_fields.get(ddc.getCode());
														field.addCSVColumn(ddc);

														// update list
														availableFields.put(ddc.getCode(), true);
													} else {
														// only code not found is the 'eid' field
														System.err.println(
																"Code not found: " + code + " --> " + ddc.getCode());
													}
												}
											}

										}

										// end row
									}
								}
							}

						}
					}
				}
			}

		} catch (Exception e) {
			System.err.println("Error: " + e.toString());
		}

		return availableFields;
	}

	/**
	 * Extract a data dictionary code
	 * 
	 * @param code
	 * @return
	 */
	public DataDictionaryCode extractCodeInfo(String code) {

		// Default new DataDictionaryCode
		DataDictionaryCode ddc = new DataDictionaryCode();
		ddc.setArrayIndex(0);
		ddc.setInstanceId(0);

		if (code.contains(".")) {
			String[] elements = code.split("\\.");
			if (elements[0].contains("-")) {
				String[] codeInfo = elements[0].split("-");
				ddc.setCode(codeInfo[0]);
				ddc.setInstanceId(Integer.parseInt(codeInfo[1]));
				ddc.setArrayIndex(Integer.parseInt(elements[1]));
			} else {
				// System.err.println("Unexpected code format: " + code);
				return null;
			}

		} else {
			if (code.contains("-")) {
				String[] codeInfo = code.split("-");
				ddc.setCode(codeInfo[0]);
				ddc.setInstanceId(Integer.parseInt(codeInfo[1]));
			} else {
				// System.err.println("Unexpected code format: " + code);
				return null;
			}
		}

		return ddc;
	}

	public void setUkbbHierarchy(String cat_file) {
		try {

			// we have downloaded the file and will process it locally
			File input = new File(cat_file);
			Document doc = Jsoup.parse(input, "UTF-8", "http://biobank.ctsu.ox.ac.uk/crystal/");

			Elements content = doc.getElementsByTag("body");
			for (Element body : content) {

				for (Node node : body.childNodes()) {
					if (Element.class.isInstance(node) == true) {

						Element topOfList = (Element) node;
						List<Category> categories = this.procesListLevel(topOfList, 0);
						System.out.println("Top level categories: " + categories.size());
					}
				}

			}

			// print out the hierarchy in chid/parent form
			boolean debug = false;
			if (debug == true) {
				for (int refId : this.categories.keySet()) {
					Category c = this.categories.get(refId);
					String output = c.getRefId() + "\t" + c.getName();
					if (c.hasParent()) {
						output = output + "\t" + c.getParent().getRefId();
					}
					System.out.println(output);

				}
			}

		} catch (Exception e) {
			System.err.println("Error: " + e.toString());
		}
	}

	private List<Category> procesListLevel(Element topOfList, int level) {

		int current_level = level;

		// track all categories found
		List<Category> categories = new ArrayList<>();
		for (Node li : topOfList.childNodes()) {
			Category catEntry = null;
			catEntry = getCategoryEntry(li);

			// test for null
			if (catEntry != null) {

				// add to current level of categories
				categories.add(catEntry);

				// Add root categories
				if (current_level == 0)
					this.roots.add(catEntry);

				this.categories.put(catEntry.getRefId(), catEntry);

				String tabs = "";
				for (int idx = 0; idx < current_level; idx++)
					tabs = tabs + "\t";

				// System.out.println(tabs + catEntry.toString());

				for (Node lic : li.childNodes()) {
					if (Element.class.isInstance(lic) == true) {
						Element testElement = (Element) lic;
						if (testElement.tagName().contentEquals("ul")) {
							// sub list!
							// System.out.println("Sublist!");
							// recursive!
							List<Category> children = procesListLevel(testElement, current_level + 1);
							for (Category child : children) {
								// set parent child relations
								child.setParent(catEntry);
								catEntry.getChildren().add(child);
							}
						}
					}
				}
			}

		}
		return categories;
	}

	private Category getCategoryEntry(Node li) {
		if (Element.class.isInstance(li) == true) {

			Element liEntry = (Element) li;
			for (Node liNode : liEntry.childNodes()) {
				if (Element.class.isInstance(liNode) == true) {
					Element liSubEntry = (Element) liNode;
					if (liSubEntry.tagName().contentEquals("a")) {

						// link!
						// System.out.println("L1: " + liSubEntry.text());
						String label = liSubEntry.text();
						String url = liSubEntry.attr("href");
						int cat_id = getCategoryId(url);

						if (!StringUtils.isEmpty(label) && cat_id > 0) {
							Category catEntry = new Category(this.myConfig);
							catEntry.setReserveWords(this.reserveWords);
							catEntry.setRefId(cat_id);
							catEntry.setName(liSubEntry.text());
							return catEntry;
						}
					}
				}
			}

		}
		return null;
	}

	/**
	 * Set the root category for eid references
	 * 
	 * @param string
	 */
	public void setRootCategory(Category rootCategory) {

		// for generated root, add to the local hashmap
		if (this.categories.containsKey(rootCategory.getRefId()) == false)
			this.categories.put(rootCategory.getRefId(), rootCategory);

		System.out.println("Setting root category: " + rootCategory.getName());
		rootCategory.setRoot(true);
		this.rootCategory = rootCategory;

		// set root category for all other categories
		for (int catId : this.categories.keySet()) {
			Category cat = this.categories.get(catId);
			if (!cat.getSQLTableName().toLowerCase().trim()
					.contentEquals(rootCategory.getSQLTableName().toLowerCase().trim())) {
				cat.setRoot(false);
				if (this.rootCategory != null)
					cat.setRootCategory(this.rootCategory);
			}
		}

		return;

	}

	/**
	 * Get a category by name
	 * 
	 * @param catId
	 * @return
	 */
	public Category getCategory(String catId) {
		for (Category cat : this.getCategories().values())
			if (cat.getSQLTableName().contentEquals(catId))
				return cat;

		return null;
	}

	/**
	 * Remove the excluded tables from our parser's list of categories
	 * 
	 * @param excludeTables
	 */
	public void removeExcludedTables(List<String> excludeTables) {

		HashMap<Integer, Category> catMap = new HashMap<>();
		HashMap<String, Boolean> exclude = new HashMap<>();
		for (String tbl : excludeTables)
			exclude.put(tbl, true);

		for (int key : this.getCategories().keySet()) {
			Category cat = this.getCategories().get(key);
			String tbl = cat.getSQLTableName();
			if (exclude.containsKey(tbl) == false)
				catMap.put(key, cat);
		}

		this.setCategories(catMap);
		return;
	}

	/**
	 * Test if we have a category in our list
	 * @param categoryId
	 * @return
	 */
	public boolean hasCategory(int categoryId) {
		if (this.getCategories() != null) {
			return this.getCategories().containsKey(categoryId);
		}
		return false;
	}

	/**
	 * Update tables to rebuild
	 * 
	 * @param tablesToRebuild
	 */
	public void setRebuildTables(List<String> tablesToRebuild) {
		
		HashMap<Integer, Category> catMap = new HashMap<>();
		HashMap<String, Boolean> keepTables = new HashMap<>();
		for (String tbl : tablesToRebuild)
			keepTables.put(tbl, true);

		for (int key : this.getCategories().keySet()) {
			Category cat = this.getCategories().get(key);
			String tbl = cat.getSQLTableName();
			if (keepTables.containsKey(tbl) == true)
				catMap.put(key, cat);
		}

		catMap.put(1, this.rootCategory);
		this.setCategories(catMap);
		return;		
		
		// TODO Auto-generated method stub
		
	}

}
