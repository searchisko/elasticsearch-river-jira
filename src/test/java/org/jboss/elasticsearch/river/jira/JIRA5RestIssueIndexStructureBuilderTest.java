/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentGenerator;
import org.jboss.elasticsearch.river.jira.testtools.TestUtils;
import org.jboss.elasticsearch.tools.content.StructuredContentPreprocessor;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link JIRA5RestIssueIndexStructureBuilder}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 * @author Lukáš Vlček (lvlcek@redhat.com)
 */
public class JIRA5RestIssueIndexStructureBuilderTest {

	private static ObjectMapper mapper;

	private JsonNode toJsonNode(String source) {
		JsonNode node = null;
		try {
			node = mapper.readValue(source, JsonNode.class);
		} catch (IOException e) {
			fail("Exception while parsing!: " + e);
		}
		return node;
	}

	@BeforeClass
	public static void setUp() {
		mapper = new ObjectMapper();
		mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Test
	public void configuration_read_ok() {

		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name",
				"type_name", "http://issues-stg.jboss.org", loadTestSettings("/index_structure_configuration_test_ok.json"));
		Assert.assertEquals("river_name", tested.riverName);
		Assert.assertEquals("index_name", tested.indexName);
		Assert.assertEquals("type_name", tested.issueTypeName);
		Assert.assertEquals("document_id", tested.jiraFieldForIssueDocumentId);
		Assert.assertEquals("river_name", tested.indexFieldForRiverName);
		Assert.assertEquals("link", tested.indexFieldForJiraURL);
		Assert.assertEquals("http://issues-stg.jboss.org/browse/", tested.jiraIssueShowUrlBase);
		Assert.assertEquals("jira_project_key", tested.indexFieldForProjectKey);
		Assert.assertEquals("jira_issue_key", tested.indexFieldForIssueKey);
		Assert.assertEquals(IssueCommentIndexingMode.CHILD, tested.commentIndexingMode);
		Assert.assertEquals("all_comments", tested.indexFieldForComments);
		Assert.assertEquals("jira_issue_comment_type", tested.commentTypeName);

		Assert.assertEquals(IssueCommentIndexingMode.CHILD, tested.changelogIndexingMode);
		Assert.assertEquals("all_changelogs", tested.indexFieldForChangelogs);
		Assert.assertEquals("jira_issue_change_type", tested.changelogTypeName);

		Assert.assertEquals(5, tested.fieldsConfig.size());
		assertFieldConfiguration(tested.fieldsConfig, "created", "fields.created", null);
		assertFieldConfiguration(tested.fieldsConfig, "reporter", "fields.reporter", "user2");
		assertFieldConfiguration(tested.fieldsConfig, "assignee", "fields.assignee", "user2");
		assertFieldConfiguration(tested.fieldsConfig, "fix_versions", "fields.fixVersions", "name2");
		assertFieldConfiguration(tested.fieldsConfig, "components", "fields.components", "name2");

		Assert.assertEquals(2, tested.filtersConfig.size());
		Assert.assertTrue(tested.filtersConfig.containsKey("user2"));
		Assert.assertTrue(tested.filtersConfig.containsKey("name2"));

		Map<String, String> userFilter = tested.filtersConfig.get("user2");
		Assert.assertEquals(2, userFilter.size());
		Assert.assertEquals("username2", userFilter.get("name"));
		Assert.assertEquals("display_name2", userFilter.get("displayName"));

		Assert.assertEquals(4, tested.commentFieldsConfig.size());
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_body", "body", null);
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_author2", "author", "user2");
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_updater", "updateAuthor", "user2");
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_created", "created", null);

		Assert.assertEquals(3, tested.changelogFieldsConfig.size());
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_id", "id", null);
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_author2", "author", "user2");
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_items", "items", null);

	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> loadTestSettings(String file) {
		return (Map<String, Object>) Utils.loadJSONFromJarPackagedFile(file).get("index");
	}

	@Test
	public void configuration_read_validation() {

		try {
			new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name", "http://issues-stg.jboss.org/",
					loadTestSettings("/index_structure_configuration_test_err_nojirafield.json"));
			Assert.fail("SettingsException must be thrown");
		} catch (SettingsException e) {
			System.out.println(e.getMessage());
			Assert.assertEquals("'jira_field' is not defined in 'index/fields/reporter'", e.getMessage());
		}

		try {
			new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name", "http://issues-stg.jboss.org",
					loadTestSettings("/index_structure_configuration_test_err_emptyjirafield.json"));
			Assert.fail("SettingsException must be thrown");
		} catch (SettingsException e) {
			System.out.println(e.getMessage());
			Assert.assertEquals("'jira_field' is not defined in 'index/fields/link'", e.getMessage());
		}

		try {
			new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name", "http://issues-stg.jboss.org/",
					loadTestSettings("/index_structure_configuration_test_err_unknownvaluefilter.json"));
			Assert.fail("SettingsException must be thrown");
		} catch (SettingsException e) {
			System.out.println(e.getMessage());
			Assert.assertEquals(
					"Filter definition not found for filter name 'name3' defined in 'index/fields/fix_versions/value_filter'",
					e.getMessage());
		}

		try {
			new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name", "http://issues-stg.jboss.org/",
					loadTestSettings("/index_structure_configuration_test_err_nojirafieldcomment.json"));
			Assert.fail("SettingsException must be thrown");
		} catch (SettingsException e) {
			System.out.println(e.getMessage());
			Assert.assertEquals("'jira_field' is not defined in 'index/comment_fields/comment_author'", e.getMessage());
		}

	}

	@SuppressWarnings("rawtypes")
	@Test
	public void configuration_defaultLoading() {

		assertDefaultConfigurationLoaded(new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
				"http://issues-stg.jboss.org", null));

		Map<String, Object> settings = new HashMap<String, Object>();
		assertDefaultConfigurationLoaded(new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
				"http://issues-stg.jboss.org", settings));

		settings.put(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDS, new HashMap());
		settings.put(JIRA5RestIssueIndexStructureBuilder.CONFIG_FILTERS, new HashMap());
		settings.put(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDRIVERNAME, " ");
		assertDefaultConfigurationLoaded(new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
				"http://issues-stg.jboss.org/", settings));

	}

	private void assertDefaultConfigurationLoaded(JIRA5RestIssueIndexStructureBuilder tested) {
		Assert.assertEquals("river_name", tested.riverName);
		Assert.assertEquals("index_name", tested.indexName);
		Assert.assertEquals("type_name", tested.issueTypeName);
		Assert.assertEquals("source", tested.indexFieldForRiverName);
		Assert.assertEquals("project_key", tested.indexFieldForProjectKey);
		Assert.assertEquals("issue_key", tested.indexFieldForIssueKey);
		Assert.assertEquals("document_url", tested.indexFieldForJiraURL);
		Assert.assertEquals("http://issues-stg.jboss.org/browse/", tested.jiraIssueShowUrlBase);

		Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED, tested.commentIndexingMode);
		Assert.assertEquals("comments", tested.indexFieldForComments);
		Assert.assertEquals("jira_issue_comment", tested.commentTypeName);

		Assert.assertEquals(IssueCommentIndexingMode.NONE, tested.changelogIndexingMode);
		Assert.assertEquals("changelogs", tested.indexFieldForChangelogs);
		Assert.assertEquals("jira_issue_change", tested.changelogTypeName);

		Assert.assertEquals(13, tested.fieldsConfig.size());
		assertFieldConfiguration(tested.fieldsConfig, "project_name", "fields.project.name", null);
		assertFieldConfiguration(tested.fieldsConfig, "assignee", "fields.assignee", "user");
		assertFieldConfiguration(tested.fieldsConfig, "fix_versions", "fields.fixVersions", "name");

		Assert.assertEquals(2, tested.filtersConfig.size());
		Assert.assertTrue(tested.filtersConfig.containsKey("user"));
		Assert.assertTrue(tested.filtersConfig.containsKey("name"));

		Map<String, String> userFilter = tested.filtersConfig.get("user");
		Assert.assertEquals(3, userFilter.size());
		Assert.assertEquals("username", userFilter.get("name"));
		Assert.assertEquals("email_address", userFilter.get("emailAddress"));
		Assert.assertEquals("display_name", userFilter.get("displayName"));

		Assert.assertEquals(6, tested.commentFieldsConfig.size());
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_id", "id", null);
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_body", "body", null);
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_author", "author", "user");
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_updater", "updateAuthor", "user");
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_updated", "updated", null);
		assertFieldConfiguration(tested.commentFieldsConfig, "comment_created", "created", null);

		Assert.assertEquals(4, tested.changelogFieldsConfig.size());
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_id", "id", null);
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_items", "items", null);
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_author", "author", "user");
		assertFieldConfiguration(tested.changelogFieldsConfig, "change_created", "created", null);

	}

	private void assertFieldConfiguration(Map<String, Map<String, String>> fieldsConfig, String indexFieldName,
			String jiraFieldName, String filter) {
		Assert.assertTrue(fieldsConfig.containsKey(indexFieldName));
		Map<String, String> field = fieldsConfig.get(indexFieldName);
		Assert.assertEquals(jiraFieldName, field.get(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDS_JIRAFIELD));
		Assert.assertEquals(filter, field.get(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDS_VALUEFILTER));
	}

	@Test
	public void addIssueDataPreprocessor() {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null,
				"http://issues-stg.jboss.org/", null);

		// case - not NPE
		tested.addIssueDataPreprocessor(null);

		// case - preprocessors adding
		tested.addIssueDataPreprocessor(mock(StructuredContentPreprocessor.class));
		Assert.assertEquals(1, tested.issueDataPreprocessors.size());

		tested.addIssueDataPreprocessor(mock(StructuredContentPreprocessor.class));
		tested.addIssueDataPreprocessor(mock(StructuredContentPreprocessor.class));
		tested.addIssueDataPreprocessor(mock(StructuredContentPreprocessor.class));
		Assert.assertEquals(4, tested.issueDataPreprocessors.size());

	}

	@Test
	public void preprocessIssueData() {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null,
				"http://issues-stg.jboss.org/", null);

		Map<String, Object> issue = null;
		// case - no NPE and change when no preprocessors defined and issue data are null
		Assert.assertNull(tested.preprocessIssueData("ORG", issue));

		// case - no NPE and change when no preprocessors defined and issue data are notnull
		{
			issue = new HashMap<String, Object>();
			issue.put(JIRA5RestIssueIndexStructureBuilder.JF_KEY, "ORG-1545");

			Map<String, Object> ret = tested.preprocessIssueData("ORG", issue);
			Assert.assertEquals(issue, ret);
			Assert.assertEquals(1, ret.size());
			Assert.assertEquals("ORG-1545", ret.get(JIRA5RestIssueIndexStructureBuilder.JF_KEY));
		}

		// case - all preprocessors called
		{
			StructuredContentPreprocessor idp1 = mock(StructuredContentPreprocessor.class);
			StructuredContentPreprocessor idp2 = mock(StructuredContentPreprocessor.class);
			when(idp1.preprocessData(issue)).thenAnswer(new Answer<Map<String, Object>>() {
				@SuppressWarnings("unchecked")
				@Override
				public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
					Map<String, Object> ret = (Map<String, Object>) invocation.getArguments()[0];
					ret.put("idp1", "called");
					return ret;
				}
			});
			when(idp2.preprocessData(issue)).thenAnswer(new Answer<Map<String, Object>>() {
				@SuppressWarnings("unchecked")
				@Override
				public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
					Map<String, Object> ret = (Map<String, Object>) invocation.getArguments()[0];
					ret.put("idp2", "called");
					return ret;
				}
			});

			tested.addIssueDataPreprocessor(idp1);
			tested.addIssueDataPreprocessor(idp2);
			Map<String, Object> ret = tested.preprocessIssueData("ORG", issue);
			Assert.assertEquals(issue, ret);
			Assert.assertEquals(3, ret.size());
			Assert.assertEquals("ORG-1545", ret.get(JIRA5RestIssueIndexStructureBuilder.JF_KEY));
			Assert.assertEquals("called", ret.get("idp1"));
			Assert.assertEquals("called", ret.get("idp2"));
		}
	}

	@Test
	public void getRequiredJIRACallIssueFields() {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null,
				"http://issues-stg.jboss.org/", null);
		tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
		tested.changelogIndexingMode = IssueCommentIndexingMode.NONE;
		tested.prepareJiraCallFieldSet();

		// case - mandatory fields in set
		String s = tested.getRequiredJIRACallIssueFields();
		List<String> fp = Utils.parseCsvString(s);

		Assert.assertEquals(13, fp.size());
		Assert.assertTrue(fp.contains("updated"));
		Assert.assertTrue(fp.contains("project"));

		// assert other fields from default configuration
		Assert.assertTrue(fp.contains("issuetype"));
		Assert.assertTrue(fp.contains("summary"));
		Assert.assertTrue(fp.contains("status"));
		Assert.assertTrue(fp.contains("created"));
		Assert.assertTrue(fp.contains("resolutiondate"));
		Assert.assertTrue(fp.contains("description"));
		Assert.assertTrue(fp.contains("labels"));
		Assert.assertTrue(fp.contains("assignee"));
		Assert.assertTrue(fp.contains("reporter"));
		Assert.assertTrue(fp.contains("components"));
		Assert.assertTrue(fp.contains("fixVersions"));
		Assert.assertFalse(fp.contains("comment"));

		// assert expands
		Assert.assertEquals("", tested.getRequiredJIRACallIssueExpands());

		// case - comments enabled
		tested.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
		tested.prepareJiraCallFieldSet();
		fp = Utils.parseCsvString(tested.getRequiredJIRACallIssueFields());
		Assert.assertEquals(14, fp.size());
		Assert.assertTrue(fp.contains("comment"));
		tested.commentIndexingMode = IssueCommentIndexingMode.CHILD;
		tested.prepareJiraCallFieldSet();
		fp = Utils.parseCsvString(tested.getRequiredJIRACallIssueFields());
		Assert.assertEquals(14, fp.size());
		Assert.assertTrue(fp.contains("comment"));
		tested.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
		tested.prepareJiraCallFieldSet();
		fp = Utils.parseCsvString(tested.getRequiredJIRACallIssueFields());
		Assert.assertEquals(14, fp.size());
		Assert.assertTrue(fp.contains("comment"));

		// case - changelog enabled
		tested.changelogIndexingMode = IssueCommentIndexingMode.EMBEDDED;
		tested.prepareJiraCallFieldSet();
		Assert.assertEquals("changelog", tested.getRequiredJIRACallIssueExpands());
		tested.changelogIndexingMode = IssueCommentIndexingMode.CHILD;
		tested.prepareJiraCallFieldSet();
		Assert.assertEquals("changelog", tested.getRequiredJIRACallIssueExpands());
		tested.changelogIndexingMode = IssueCommentIndexingMode.STANDALONE;
		tested.prepareJiraCallFieldSet();
		Assert.assertEquals("changelog", tested.getRequiredJIRACallIssueExpands());

	}

	@Test
	public void constructJIRAIssueShowUrlBase() {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder();

		tested.constructJIRAIssueShowUrlBase("http://issues.jboss.org");
		Assert.assertEquals("http://issues.jboss.org/browse/", tested.jiraIssueShowUrlBase);

		tested.constructJIRAIssueShowUrlBase("http://jira.jboss.org/");
		Assert.assertEquals("http://jira.jboss.org/browse/", tested.jiraIssueShowUrlBase);

	}

	@Test
	public void prepareJIRAGUIUrl() {

		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder();
		tested.constructJIRAIssueShowUrlBase("http://issues.jboss.org");

		Assert.assertEquals("http://issues.jboss.org/browse/ORG-1250", tested.prepareJIRAGUIUrl("ORG-1250", null));

		Assert
				.assertEquals(
						"http://issues.jboss.org/browse/ORG-1250?focusedCommentId=1234&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-1234",
						tested.prepareJIRAGUIUrl("ORG-1250", "1234"));

	}

	@Test
	public void buildSearchForIndexedDocumentsNotUpdatedAfter() throws IOException {

		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_jira", "search_index",
				"issue_type", "http://issues-stg.jboss.org/", null);
		tested.commentTypeName = "comment_type";
		tested.changelogTypeName = "changelog_type";

		tested.changelogIndexingMode = IssueCommentIndexingMode.NONE;
		// case - comments NONE
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
			SearchRequestBuilder srb = new SearchRequestBuilder(Mockito.mock(Client.class));
			tested.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, "ORG",
					DateTimeUtils.parseISODateTime("2012-09-06T12:22:19Z"));
			Assert.assertArrayEquals(new String[] { "issue_type" }, srb.request().types());
			assertTrue(
					"Should equals: " + srb.toString(),
					toJsonNode(srb.toString()).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/buildSearchForIndexedDocumentsNotUpdatedAfter.json"))));
		}

		// case - comments EMBEDDED
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
			SearchRequestBuilder srb = new SearchRequestBuilder(Mockito.mock(Client.class));
			tested.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, "ORG",
					DateTimeUtils.parseISODateTime("2012-09-06T12:22:19Z"));
			Assert.assertArrayEquals(new String[] { "issue_type" }, srb.request().types());
			assertTrue(
					"Should equals",
					toJsonNode(srb.toString()).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/buildSearchForIndexedDocumentsNotUpdatedAfter.json"))));
		}

		// case - comments CHILD, changelog EMBEDDED
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.CHILD;
			tested.changelogIndexingMode = IssueCommentIndexingMode.EMBEDDED;
			SearchRequestBuilder srb = new SearchRequestBuilder(Mockito.mock(Client.class));
			tested.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, "ORG",
					DateTimeUtils.parseISODateTime("2012-09-06T12:22:19Z"));
			Assert.assertArrayEquals(new String[] { "issue_type", "comment_type" }, srb.request().types());
			assertTrue(
					"Should equals",
					toJsonNode(srb.toString()).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/buildSearchForIndexedDocumentsNotUpdatedAfter.json"))));
		}

		// case - comments STANDALONE, changelog CHILD
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.CHILD;
			SearchRequestBuilder srb = new SearchRequestBuilder(Mockito.mock(Client.class));
			tested.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, "ORG",
					DateTimeUtils.parseISODateTime("2012-09-06T12:22:19Z"));
			Assert.assertArrayEquals(new String[] { "issue_type", "comment_type", "changelog_type" }, srb.request().types());
			assertTrue(
					"Should equals",
					toJsonNode(srb.toString()).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/buildSearchForIndexedDocumentsNotUpdatedAfter.json"))));
		}

		// case - comments NONE, changelog STANDALONE
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.STANDALONE;
			SearchRequestBuilder srb = new SearchRequestBuilder(Mockito.mock(Client.class));
			tested.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, "ORG",
					DateTimeUtils.parseISODateTime("2012-09-06T12:22:19Z"));
			Assert.assertArrayEquals(new String[] { "issue_type", "changelog_type" }, srb.request().types());
			assertTrue(
					"Should equals",
					toJsonNode(srb.toString()).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/buildSearchForIndexedDocumentsNotUpdatedAfter.json"))));
		}

	}

	@Test
	public void prepareIssueIndexedDocument() throws Exception {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_jira", "search_index",
				"issue_type", "http://issues-stg.jboss.org/", null);

		// case - no comments nor changelogs
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.NONE;

			String res = tested.prepareIssueIndexedDocument("ORG",
					TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501")).string();
			assertTrue(
					"Should equals",
					toJsonNode(res).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/prepareIssueIndexedDocument_ORG-1501_NOCOMMENT.json"))));
		}

		// case - comments and changelogs as CHILD so not in this document
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.CHILD;
			tested.changelogIndexingMode = IssueCommentIndexingMode.CHILD;

			String res = tested.prepareIssueIndexedDocument("ORG",
					TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501")).string();
			assertTrue(
					"Should equals",
					toJsonNode(res).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/prepareIssueIndexedDocument_ORG-1501_NOCOMMENT.json"))));
		}

		// case - comments and changelog as STANDALONE so not in this document
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.STANDALONE;

			String res = tested.prepareIssueIndexedDocument("ORG",
					TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501")).string();
			assertTrue(
					"Should equals",
					toJsonNode(res).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/prepareIssueIndexedDocument_ORG-1501_NOCOMMENT.json"))));
		}

		// case - comments as EMBEDDED so present in this document
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
			tested.changelogIndexingMode = IssueCommentIndexingMode.NONE;

			String res = tested.prepareIssueIndexedDocument("ORG",
					TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501")).string();
			assertTrue(
					"Should equals",
					toJsonNode(res).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/prepareIssueIndexedDocument_ORG-1501_COMMENTS.json"))));
		}

		// case - changelog as EMBEDDED so present in this document
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.EMBEDDED;

			String res = tested.prepareIssueIndexedDocument("ORG",
					TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501")).string();
			assertTrue(
					"Should equals",
					toJsonNode(res).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/prepareIssueIndexedDocument_ORG-1501_CHANGELOGS.json"))));
		}

		// case - comments and changelog as EMBEDDED but not in source so no present in this document
		{
			tested.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
			tested.changelogIndexingMode = IssueCommentIndexingMode.EMBEDDED;

			String res = tested.prepareIssueIndexedDocument("ORG",
					TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1513")).string();
			assertTrue(
					"Should equals",
					toJsonNode(res).equals(
							toJsonNode(TestUtils
									.readStringFromClasspathFile("/asserts/prepareIssueIndexedDocument_ORG-1513_NOCOMMENTS.json"))));
		}

	}

	@Test
	public void prepareCommentIndexedDocument() throws Exception {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_jira", "search_index",
				"issue_type", "http://issues-stg.jboss.org/", null);
		Map<String, Object> issue = TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501");
		List<Map<String, Object>> comments = tested.extractIssueComments(issue);

		String res = tested.prepareCommentIndexedDocument("ORG", "ORG-1501", comments.get(0)).string();
		assertTrue(
				"Should equals",
				toJsonNode(res)
						.equals(
								toJsonNode(TestUtils
										.readStringFromClasspathFile("/asserts/prepareCommentIndexedDocument_ORG-1501_1.json"))));
	}

	@Test
	public void prepareChangelogIndexedDocument() throws Exception {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_jira", "search_index",
				"issue_type", "http://issues-stg.jboss.org/", null);
		Map<String, Object> issue = TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501");
		List<Map<String, Object>> changelogs = tested.extractIssueChangelogs(issue);

		String res = tested.prepareChangelogIndexedDocument("ORG", "ORG-1501", changelogs.get(0)).string();
		System.out.print(res);

		assertTrue(
				"Should equals",
				toJsonNode(res)
						.equals(
								toJsonNode(TestUtils
										.readStringFromClasspathFile("/asserts/prepareChagelogIndexedDocument_ORG-1501_1.json"))));
	}

	@Test
	public void prepareIssueDocumentId() {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_jira", "search_index",
				"issue_type", "http://issues-stg.jboss.org/", null);
		tested.jiraFieldForIssueDocumentId = null;

		Map<String, Object> issue = new HashMap<String, Object>();
		issue.put("key", "ORG-15");
		issue.put("key2", "ORG-16");
		Utils.putValueIntoMapOfMaps(issue, "key3.value", "ORG-17");

		// case - default key used if jiraFieldForIssueDocumentId is not configured
		Assert.assertEquals("ORG-15", tested.prepareIssueDocumentId(issue));

		// case - default key used if jiraFieldForIssueDocumentId is configured but value is not provided
		tested.jiraFieldForIssueDocumentId = "key_unknown";
		Assert.assertEquals("ORG-15", tested.prepareIssueDocumentId(issue));

		// case - simple notation on jiraFieldForIssueDocumentId
		tested.jiraFieldForIssueDocumentId = "key2";
		Assert.assertEquals("ORG-16", tested.prepareIssueDocumentId(issue));

		// case - dot notation on jiraFieldForIssueDocumentId
		tested.jiraFieldForIssueDocumentId = "key3.value";
		Assert.assertEquals("ORG-17", tested.prepareIssueDocumentId(issue));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void indexIssue() throws Exception {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_jira", "search_index",
				"issue_type", "http://issues-stg.jboss.org/", null);

		Client client = Mockito.mock(Client.class);

		// case - comments NONE
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.NONE;
			tested.indexIssue(esBulk, "ORG-1501", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501"));
			Assert.assertEquals(1, esBulk.request().numberOfActions());
		}

		// case - comments and changelog EMBEDDED
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
			tested.changelogIndexingMode = IssueCommentIndexingMode.EMBEDDED;
			tested.indexIssue(esBulk, "ORG-1501", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501"));
			Assert.assertEquals(1, esBulk.request().numberOfActions());
		}

		// case - comments CHILD
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.CHILD;
			tested.changelogIndexingMode = IssueCommentIndexingMode.NONE;
			tested.indexIssue(esBulk, "ORG-1501", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501"));
			Assert.assertEquals(3, esBulk.request().numberOfActions());
		}

		// case - changelog CHILD
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.NONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.CHILD;
			tested.indexIssue(esBulk, "ORG-1501", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501"));
			Assert.assertEquals(3, esBulk.request().numberOfActions());
		}

		// case - both comments and changelog STANDALONE with comments in issue
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.STANDALONE;
			tested.indexIssue(esBulk, "ORG-1501", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-1501"));
			Assert.assertEquals(5, esBulk.request().numberOfActions());
		}

		// case - comments and changelogs STANDALONE without comments in issue
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
			tested.changelogIndexingMode = IssueCommentIndexingMode.STANDALONE;
			tested.indexIssue(esBulk, "ORG-15013", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-15013"));
			Assert.assertEquals(1, esBulk.request().numberOfActions());
		}

		// case - preprocessor called
		{
			BulkRequestBuilder esBulk = new BulkRequestBuilder(client);
			tested.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
			StructuredContentPreprocessor idp1 = mock(StructuredContentPreprocessor.class);
			when(idp1.preprocessData(Mockito.anyMap())).thenAnswer(new Answer<Map<String, Object>>() {
				@Override
				public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
					return (Map<String, Object>) invocation.getArguments()[0];
				}
			});
			tested.addIssueDataPreprocessor(idp1);

			tested.indexIssue(esBulk, "ORG-15013", TestUtils.readJiraJsonIssueDataFromClasspathFile("ORG-15013"));
			Assert.assertEquals(1, esBulk.request().numberOfActions());
			verify(idp1, times(1)).preprocessData(Mockito.anyMap());
		}

	}

	@Test
	public void addValueToTheIndex() throws Exception {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null,
				"http://issues-stg.jboss.org/", null);

		XContentGenerator xContentGeneratorMock = mock(XContentGenerator.class);
		XContentBuilder out = XContentBuilder.builder(preparexContentMock(xContentGeneratorMock));

		// case - no exception if values parameter is null
		tested.addValueToTheIndex(out, "testfield", "testpath", null, (Map<String, String>) null);
		verify(xContentGeneratorMock, times(0)).writeFieldName(Mockito.anyString());

		// case - no exception if value is not found
		reset(xContentGeneratorMock);
		Map<String, Object> values = new HashMap<String, Object>();
		tested.addValueToTheIndex(out, "testfield", "testpath", values, (Map<String, String>) null);
		verify(xContentGeneratorMock, times(0)).writeFieldName(Mockito.anyString());

		// case - get correctly value from first level of nesting, no filtering on null filter
		reset(xContentGeneratorMock);
		values.put("myKey", "myValue");
		values.put("myKey2", "myValue2");
		tested.addValueToTheIndex(out, "testfield", "myKey2", values, (Map<String, String>) null);
		verify(xContentGeneratorMock, times(1)).writeFieldName(Mockito.anyString());
		verify(xContentGeneratorMock).writeFieldName("testfield");
		verify(xContentGeneratorMock).writeString("myValue2");
		Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

		// case - get correctly value from deeper level of nesting, no filtering with empty filter
		reset(xContentGeneratorMock);
		values.put("myKey", "myValue");
		values.put("myKey2", "myValue2");
		Map<String, Object> parent3 = new HashMap<String, Object>();
		values.put("parent3", parent3);
		parent3.put("myKey3", "myValue3");
		Map<String, String> filter = new HashMap<String, String>();

		tested.addValueToTheIndex(out, "testfield3", "parent3.myKey3", values, filter);
		verify(xContentGeneratorMock, times(1)).writeFieldName(Mockito.anyString());
		verify(xContentGeneratorMock).writeFieldName("testfield3");
		verify(xContentGeneratorMock).writeString("myValue3");
		Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

		// case - no error when filter on filtering unsupported value
		reset(xContentGeneratorMock);
		values.clear();
		values.put("myKey", "myValue");
		values.put("myKey2", "myValue2");
		filter.put("myKeyFilter", "myKeyFilter");
		tested.addValueToTheIndex(out, "testfield", "myKey2", values, filter);
		verify(xContentGeneratorMock).writeFieldName("testfield");
		verify(xContentGeneratorMock).writeString("myValue2");
		Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

		// case - get correctly value from first level of nesting, filtering on Map
		reset(xContentGeneratorMock);
		values.clear();
		values.put("myKey", "myValue");
		values.put("myKey2", "myValue2");
		parent3 = new HashMap<String, Object>();
		values.put("parent3", parent3);
		parent3.put("myKey3", "myValue3");
		parent3.put("myKey4", "myValue4");

		filter.clear();
		filter.put("myKey3", "myKey1");

		tested.addValueToTheIndex(out, "testfield", "parent3", values, filter);

		verify(xContentGeneratorMock).writeFieldName("testfield");
		verify(xContentGeneratorMock).writeStartObject();
		verify(xContentGeneratorMock).writeFieldName("myKey1");
		verify(xContentGeneratorMock).writeString("myValue3");
		verify(xContentGeneratorMock).writeEndObject();

		Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

		// case - filtering on List of Maps
		reset(xContentGeneratorMock);
		values.clear();
		values.put("myKey", "myValue");
		values.put("myKey2", "myValue2");

		List<Object> parent3list = new ArrayList<Object>();
		values.put("parent3", parent3list);

		Map<String, Object> obj31 = new HashMap<String, Object>();
		parent3list.add(obj31);
		obj31.put("myKey3", "myValue31");
		obj31.put("myKey4", "myValue41");

		Map<String, Object> obj32 = new HashMap<String, Object>();
		parent3list.add(obj32);
		obj32.put("myKey3", "myValue32");
		obj32.put("myKey4", "myValue42");

		filter.clear();
		filter.put("myKey3", "myKey3");

		tested.addValueToTheIndex(out, "testfield", "parent3", values, filter);

		verify(xContentGeneratorMock).writeFieldName("testfield");
		verify(xContentGeneratorMock, times(1)).writeStartArray();
		verify(xContentGeneratorMock, times(2)).writeStartObject();
		verify(xContentGeneratorMock, times(2)).writeFieldName("myKey3");
		verify(xContentGeneratorMock).writeString("myValue31");
		verify(xContentGeneratorMock).writeString("myValue32");
		verify(xContentGeneratorMock, times(2)).writeEndObject();
		verify(xContentGeneratorMock, times(1)).writeEndArray();

		Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

	}

	@Test
	public void addValueToTheIndexField() throws Exception {
		JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null,
				"http://issues-stg.jboss.org/", null);

		XContentGenerator xContentGeneratorMock = mock(XContentGenerator.class);
		XContentBuilder out = XContentBuilder.builder(preparexContentMock(xContentGeneratorMock));

		// case - string field
		tested.addValueToTheIndexField(out, "test", "testvalue");
		verify(xContentGeneratorMock).writeFieldName("test");
		verify(xContentGeneratorMock).writeString("testvalue");

		// case - integer field
		reset(xContentGeneratorMock);
		tested.addValueToTheIndexField(out, "testint", new Integer(10));
		verify(xContentGeneratorMock).writeFieldName("testint");
		verify(xContentGeneratorMock).writeNumber(10);

		// case - nothing added if value is null
		reset(xContentGeneratorMock);
		tested.addValueToTheIndexField(out, "testnull", null);
		Mockito.verifyZeroInteractions(xContentGeneratorMock);
	}

	@Test
	public void getJiraCallFieldName() {
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(null));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("  "));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("self"));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("key"));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields"));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields."));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.  "));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.."));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields..."));
		Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields..something"));

		Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.summary"));
		Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.summary."));
		Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.summary.name"));
		Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(" fields.summary "));
		Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(" fields.summary. "));
		Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(" fields.summary.name "));
	}

	/**
	 * Prepare {@link XContent} mock to be used for {@link XContentBuilder} test instance creation.
	 * 
	 * @param xContentGeneratorMock to be returned from XContent mock
	 * @return XContent mock instance
	 * @throws IOException
	 */
	protected XContent preparexContentMock(XContentGenerator xContentGeneratorMock) throws IOException {
		XContent xContentMock = mock(XContent.class);
		when(xContentMock.createGenerator(Mockito.any(OutputStream.class))).thenReturn(xContentGeneratorMock);
		return xContentMock;
	}
}
