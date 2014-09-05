/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.testtools;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.jboss.elasticsearch.river.jira.Utils;
import org.junit.Assert;
import org.junit.ComparisonFailure;

/**
 * Helper methods for Unit tests.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class TestUtils {

	private static ObjectMapper mapper;
	static {
		mapper = new ObjectMapper();
		mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	private static JsonNode toJsonNode(String source) {
		JsonNode node = null;
		try {
			node = mapper.readValue(source, JsonNode.class);
		} catch (IOException e) {
			Assert.fail("Exception while parsing!: " + e);
		}
		return node;
	}

	/**
	 * Assert two strings with JSON to be equal.
	 * 
	 * @param expected
	 * @param actual
	 */
	public static void assertJsonEqual(String expected, String actual) {
		if (!toJsonNode(expected).equals(toJsonNode(actual))) {
			throw new ComparisonFailure("JSON's are not equal", expected, actual);
		}
	}

	/**
	 * Assert passed string is same as content of given file loaded from classpath.
	 * 
	 * @param expectedFilePath path to file inside classpath
	 * @param actual content to assert
	 * @throws IOException
	 */
	public static void assertStringFromClasspathFile(String expectedFilePath, String actual) throws IOException {
		Assert.assertEquals(readStringFromClasspathFile(expectedFilePath), actual);
	}

	/**
	 * Read JIRA JSON issue data for tests. Loaded from folder <code>/src/test/resources/jira_issue_json/</code>.
	 * 
	 * @param key of issue to load data for
	 * @return Map of Maps structure with issue data
	 * @throws IOException
	 */
	public static Map<String, Object> readJiraJsonIssueDataFromClasspathFile(String key) throws IOException {
		return Utils.loadJSONFromJarPackagedFile("/jira_issue_json/" + key + ".json");
	}

	/**
	 * Read file from classpath into String. UTF-8 encoding expected.
	 * 
	 * @param filePath in classpath to read data from.
	 * @return file content.
	 * @throws IOException
	 */
	public static String readStringFromClasspathFile(String filePath) throws IOException {
		StringWriter stringWriter = new StringWriter();
		IOUtils.copy(TestUtils.class.getResourceAsStream(filePath), stringWriter, "UTF-8");
		return stringWriter.toString();
	}

	/**
	 * Read JSON from string representation.
	 * 
	 * @param jsonString to parse
	 * @return parsed JSON
	 * @throws SettingsException
	 */
	public static Map<String, Object> getJSONMapFromString(String jsonString) throws IOException {
		XContentParser parser = null;
		try {
			parser = XContentFactory.xContent(XContentType.JSON).createParser(jsonString);
			return parser.mapAndClose();
		} finally {
			if (parser != null)
				parser.close();
		}
	}

}
