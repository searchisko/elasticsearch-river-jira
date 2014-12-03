/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import junit.framework.Assert;

import org.elasticsearch.Version;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.jboss.elasticsearch.river.jira.testtools.ESRealClientTestBase;
import org.jboss.elasticsearch.river.jira.testtools.IssueDataPreprocessorMock;
import org.jboss.elasticsearch.river.jira.testtools.MockThread;
import org.jboss.elasticsearch.river.jira.testtools.TestUtils;
import org.jboss.elasticsearch.tools.content.StructuredContentPreprocessor;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link JiraRiver}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiverTest extends ESRealClientTestBase {

	private static final String RIVER_NAME = "my_jira_river";

	@Test
	public void constructor_config() throws Exception {

		// case - exception if no jira URL base is defined
		try {
			prepareJiraRiverInstanceForTest(null, null, null, false);
			Assert.fail("No SettingsException thrown");
		} catch (SettingsException e) {
			// OK
		}
		try {
			prepareJiraRiverInstanceForTest("   ", null, null, false);
			Assert.fail("No SettingsException thrown");
		} catch (SettingsException e) {
			// OK
		}

		Map<String, Object> jiraSettings = new HashMap<String, Object>();
		Map<String, Object> toplevelSettingsAdd = new HashMap<String, Object>();

		// case - test default settings
		JiraRiver tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd,
				false);
		Assert.assertEquals(1, tested.maxIndexingThreads);
		Assert.assertEquals(5 * 60 * 1000, tested.indexUpdatePeriod);
		Assert.assertEquals(12 * 60 * 60 * 1000, tested.indexFullUpdatePeriod);
		Assert.assertNull(tested.indexFullUpdateCronExpression);
		Assert.assertEquals("my_jira_river", tested.indexName);
		Assert.assertEquals(JiraRiver.INDEX_ISSUE_TYPE_NAME_DEFAULT, tested.typeName);
		Assert.assertEquals(50, tested.jiraClient.getListJIRAIssuesMax());
		Assert.assertEquals("https://issues.jboss.org/rest/api/2/", tested.jiraClient.getJiraAPIUrlBase());

		Map<String, Object> indexSettings = new HashMap<String, Object>();
		toplevelSettingsAdd.put("index", indexSettings);
		tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd, false);
		Assert.assertEquals("my_jira_river", tested.indexName);
		Assert.assertEquals(JiraRiver.INDEX_ISSUE_TYPE_NAME_DEFAULT, tested.typeName);
		Assert.assertEquals(tested.jiraIssueIndexStructureBuilder, tested.jiraClient.getIndexStructureBuilder());

		// case - test river configuration reading
		jiraSettings.put("maxIndexingThreads", "5");
		jiraSettings.put("indexUpdatePeriod", "20m");
		jiraSettings.put("indexFullUpdatePeriod", "5h");
		jiraSettings.put("indexFullUpdateCronExpression", "* * 1 * * ?");
		jiraSettings.put("maxIssuesPerRequest", 20);
		jiraSettings.put("timeout", "5s");
		jiraSettings.put("jqlTimeZone", "Europe/Prague");
		jiraSettings.put("restApiVersion", "latest");
		indexSettings.put("index", "my_index_name");
		indexSettings.put("type", "type_test");
		tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd, false);

		Assert.assertEquals("https://issues.jboss.org/rest/api/latest/", tested.jiraClient.getJiraAPIUrlBase());
		Assert.assertEquals(5, tested.maxIndexingThreads);
		Assert.assertEquals(20 * 60 * 1000, tested.indexUpdatePeriod);
		Assert.assertEquals(5 * 60 * 60 * 1000, tested.indexFullUpdatePeriod);
		Assert.assertEquals("* * 1 * * ?", tested.indexFullUpdateCronExpression.toString());
		Assert.assertEquals("my_index_name", tested.indexName);
		Assert.assertEquals("type_test", tested.typeName);
		Assert.assertEquals(20, tested.jiraClient.getListJIRAIssuesMax());
		Assert.assertEquals(TimeZone.getTimeZone("Europe/Prague"),
				((JIRA5RestClient) tested.jiraClient).jqlDateFormat.getTimeZone());
		// assert index structure builder initialization
		Assert.assertEquals(tested.jiraIssueIndexStructureBuilder, tested.jiraClient.getIndexStructureBuilder());
		Assert.assertEquals(tested.indexName,
				((JIRA5RestIssueIndexStructureBuilder) tested.jiraIssueIndexStructureBuilder).indexName);
		Assert.assertEquals(tested.typeName,
				((JIRA5RestIssueIndexStructureBuilder) tested.jiraIssueIndexStructureBuilder).issueTypeName);
		Assert.assertEquals(tested.riverName().getName(),
				((JIRA5RestIssueIndexStructureBuilder) tested.jiraIssueIndexStructureBuilder).riverName);

		// case - invalid cron expression
		try {
			jiraSettings.put("indexFullUpdateCronExpression", "* * * * ? ?");
			tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd, false);
			Assert.fail("SettingsException expected");
		} catch (SettingsException e) {
			Assert
					.assertEquals(
							"Cron expression in indexFullUpdateCronExpression is invalid: '?' can only be specfied for Day-of-Month or Day-of-Week.",
							e.getMessage());
		}

	}

	@Test
	public void constructor_postprocessors() throws Exception {

		JiraRiver tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", null,
				Utils.loadJSONFromJarPackagedFile("/river_configuration_test_preprocessors.json"), false);

		List<StructuredContentPreprocessor> preprocs = ((JIRA5RestIssueIndexStructureBuilder) tested.jiraIssueIndexStructureBuilder).issueDataPreprocessors;
		Assert.assertEquals(2, preprocs.size());
		Assert.assertEquals("Status Normalizer", preprocs.get(0).getName());
		Assert.assertEquals("value1", ((IssueDataPreprocessorMock) preprocs.get(0)).settings.get("some_setting_1_1"));
		Assert.assertEquals("value2", ((IssueDataPreprocessorMock) preprocs.get(0)).settings.get("some_setting_1_2"));
		Assert.assertEquals("Issue type Normalizer", preprocs.get(1).getName());
		Assert.assertEquals("value1", ((IssueDataPreprocessorMock) preprocs.get(1)).settings.get("some_setting_2_1"));
		Assert.assertEquals("value2", ((IssueDataPreprocessorMock) preprocs.get(1)).settings.get("some_setting_2_2"));
	}

	@Test
	public void configure() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		try {
			tested.closed = false;
			tested.configure(null);
			Assert.fail("IllegalStateException must be thrown");
		} catch (IllegalStateException e) {
			// OK
		}
		// do not test configuration read here, it's tested in constructor tests
	}

	@Test
	public void start() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		try {
			tested.closed = false;
			tested.start();
			Assert.fail("IllegalStateException must be thrown");
		} catch (IllegalStateException e) {
			// OK
		}
		// do not test real start here because it's hardly testable
	}

	@Test
	public void close() throws Exception {

		// case - close all correctly
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		MockThread mockThread = new MockThread();
		tested.coordinatorThread = mockThread;
		tested.coordinatorInstance = mock(IJIRAProjectIndexerCoordinator.class);
		tested.closed = false;
		Assert.assertNotNull(tested.coordinatorThread);
		Assert.assertNotNull(tested.coordinatorInstance);
		JiraRiver.riverInstances.put(tested.riverName().getName(), tested);
		Assert.assertTrue(JiraRiver.riverInstances.containsKey(tested.riverName().getName()));

		tested.close();
		Assert.assertTrue(tested.isClosed());
		Assert.assertTrue(mockThread.interruptWasCalled);
		Assert.assertNull(tested.coordinatorThread);
		Assert.assertNull(tested.coordinatorInstance);
		Assert.assertFalse(JiraRiver.riverInstances.containsKey(tested.riverName().getName()));

		// case - no exception when coordinatorThread and coordinatorInstance is null
		tested = prepareJiraRiverInstanceForTest(null);
		JiraRiver.riverInstances.put(tested.riverName().getName(), tested);
		tested.coordinatorThread = null;
		tested.coordinatorInstance = null;
		tested.closed = false;
		Assert.assertNull(tested.coordinatorThread);
		Assert.assertNull(tested.coordinatorInstance);

		tested.close();
		Assert.assertTrue(tested.isClosed());
		Assert.assertNull(tested.coordinatorThread);
		Assert.assertNull(tested.coordinatorInstance);
		Assert.assertFalse(JiraRiver.riverInstances.containsKey(tested.riverName().getName()));

	}

	@Test
	public void stop() throws Exception {

		// case - close all correctly
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		MockThread mockThread = new MockThread();
		tested.coordinatorThread = mockThread;
		tested.coordinatorInstance = mock(IJIRAProjectIndexerCoordinator.class);
		tested.closed = false;
		Assert.assertNotNull(tested.coordinatorThread);
		Assert.assertNotNull(tested.coordinatorInstance);
		JiraRiver.riverInstances.put(tested.riverName().getName(), tested);
		Assert.assertTrue(JiraRiver.riverInstances.containsKey(tested.riverName().getName()));

		tested.stop(false);
		Assert.assertTrue(tested.isClosed());
		Assert.assertTrue(mockThread.interruptWasCalled);
		Assert.assertNull(tested.coordinatorThread);
		Assert.assertNull(tested.coordinatorInstance);
		Assert.assertTrue(JiraRiver.riverInstances.containsKey(tested.riverName().getName()));

		// case - no exception when coordinatorThread and coordinatorInstance is null
		tested = prepareJiraRiverInstanceForTest(null);
		JiraRiver.riverInstances.put(tested.riverName().getName(), tested);
		tested.coordinatorThread = null;
		tested.coordinatorInstance = null;
		tested.closed = false;
		Assert.assertNull(tested.coordinatorThread);
		Assert.assertNull(tested.coordinatorInstance);

		tested.stop(false);
		Assert.assertTrue(tested.isClosed());
		Assert.assertNull(tested.coordinatorThread);
		Assert.assertNull(tested.coordinatorInstance);
		Assert.assertTrue(JiraRiver.riverInstances.containsKey(tested.riverName().getName()));
	}

	@Test
	public void stop_permanent() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		try {
			tested.client = prepareESClientForUnitTest();
			indexCreate(tested.getRiverIndexName());

			// case - not permanent stop
			tested.closed = false;
			tested.stop(false);
			Assert.assertNull(tested.readDatetimeValue(null, JiraRiver.PERMSTOREPROP_RIVER_STOPPED_PERMANENTLY));
			Assert.assertTrue(tested.isClosed());

			// case - permanent stop
			tested.closed = false;
			tested.stop(true);
			Assert.assertNotNull(tested.readDatetimeValue(null, JiraRiver.PERMSTOREPROP_RIVER_STOPPED_PERMANENTLY));
			Assert.assertTrue(tested.isClosed());

		} finally {
			finalizeESClientForUnitTest();
		}
	}

	@Test
	public void reconfigure() throws Exception {

		// case - exception when not stopped
		{
			JiraRiver tested = prepareJiraRiverInstanceForTest(null);
			try {
				tested.closed = false;
				tested.reconfigure();
				Assert.fail("IllegalStateException must be thrown");
			} catch (IllegalStateException e) {
				// OK
			}
		}

		// case - config reload error because no document
		{
			JiraRiver tested = prepareJiraRiverInstanceForTest(null);
			try {
				tested.client = prepareESClientForUnitTest();
				tested.closed = true;
				indexCreate(tested.getRiverIndexName());
				tested.reconfigure();
				Assert.fail("IllegalStateException must be thrown");
			} catch (IllegalStateException e) {
				// OK
			} finally {
				finalizeESClientForUnitTest();
			}
		}

		// case - config reload performed
		{
			JiraRiver tested = prepareJiraRiverInstanceForTest(null);
			try {
				tested.client = prepareESClientForUnitTest();
				tested.closed = true;
				tested.client.prepareIndex(tested.getRiverIndexName(), tested.riverName().getName(), "_meta")
						.setSource(TestUtils.readStringFromClasspathFile("/river_reconfiguration_test.json")).execute().actionGet();

				tested.reconfigure();
				Assert.assertEquals("https://issues.jboss.org/rest/api/2/",
						((JIRA5RestClient) tested.jiraClient).jiraRestAPIUrlBase);
				Assert.assertEquals("my_jira_index_test", tested.indexName);
				Assert.assertEquals("jira_issue_test", tested.typeName);
				Assert.assertEquals("jira_river_activity_test", tested.activityLogIndexName);
				Assert.assertEquals("jira_river_indexupdate_test", tested.activityLogTypeName);
			} finally {
				finalizeESClientForUnitTest();
			}
		}
	}

	@Test
	public void restart() {
		// TODO unit test for river restart method
	}

	@Test
	public void getAllIndexedProjectsKeys_FromStaticConfig() throws Exception {
		Map<String, Object> jiraSettings = new HashMap<String, Object>();
		jiraSettings.put("projectKeysIndexed", "ORG, UUUU, PEM, SU07");

		JiraRiver tested = prepareJiraRiverInstanceForTest(jiraSettings);
		IJIRAClient jiraClientMock = tested.jiraClient;

		List<String> r = tested.getAllIndexedProjectsKeys();
		Assert.assertEquals(4, r.size());
		Assert.assertEquals("ORG", r.get(0));
		Assert.assertEquals("UUUU", r.get(1));
		Assert.assertEquals("PEM", r.get(2));
		Assert.assertEquals("SU07", r.get(3));
		Assert.assertEquals(Long.MAX_VALUE, tested.allIndexedProjectsKeysNextRefresh);
		verify(jiraClientMock, times(0)).getAllJIRAProjects();
	}

	@Test
	public void getAllIndexedProjectsKeys_FromJIRANoExcludes() throws Exception {
		Map<String, Object> jiraSettings = new HashMap<String, Object>();
		jiraSettings.put("projectKeysExcluded", "");

		JiraRiver tested = prepareJiraRiverInstanceForTest(jiraSettings);
		IJIRAClient jiraClientMock = tested.jiraClient;

		List<String> pl = Utils.parseCsvString("ORG,UUUU,PEM,SU07");
		when(jiraClientMock.getAllJIRAProjects()).thenReturn(pl);

		List<String> r = tested.getAllIndexedProjectsKeys();
		verify(jiraClientMock, times(1)).getAllJIRAProjects();
		Assert.assertEquals(4, r.size());
		Assert.assertEquals("ORG", r.get(0));
		Assert.assertEquals("UUUU", r.get(1));
		Assert.assertEquals("PEM", r.get(2));
		Assert.assertEquals("SU07", r.get(3));
		Assert
				.assertTrue(tested.allIndexedProjectsKeysNextRefresh <= (System.currentTimeMillis() + JiraRiver.JIRA_PROJECTS_REFRESH_TIME));
	}

	@Test
	public void getAllIndexedProjectsKeys_FromJIRAWithExcludes() throws Exception {
		Map<String, Object> jiraSettings = new HashMap<String, Object>();
		jiraSettings.put("projectKeysExcluded", "PEM,UUUU");

		JiraRiver tested = prepareJiraRiverInstanceForTest(jiraSettings);
		IJIRAClient jiraClientMock = tested.jiraClient;

		List<String> pl = Utils.parseCsvString("ORG,UUUU,PEM,SU07");
		when(jiraClientMock.getAllJIRAProjects()).thenReturn(pl);

		List<String> r = tested.getAllIndexedProjectsKeys();
		verify(jiraClientMock, times(1)).getAllJIRAProjects();
		Assert.assertEquals(2, r.size());
		Assert.assertEquals("ORG", r.get(0));
		Assert.assertEquals("SU07", r.get(1));
		Assert
				.assertTrue(tested.allIndexedProjectsKeysNextRefresh <= (System.currentTimeMillis() + JiraRiver.JIRA_PROJECTS_REFRESH_TIME));
	}

	@Test
	public void storeDatetimeValueBuildDocument() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);

		String dt = "2012-09-30T12:22:44.156Z";
		Date d = DateTimeUtils.parseISODateTime(dt);

		{
			Map<String, Object> out = TestUtils.getJSONMapFromString(tested.storeDatetimeValueBuildDocument(null,
					"my_property", d).string());
			Assert.assertEquals(2, out.size());
			Assert.assertEquals("my_property", out.get("propertyName"));
			Assert.assertEquals(d.getTime(), DateTimeUtils.parseISODateTime((String) out.get("value")).getTime());
		}

		{
			Map<String, Object> out = TestUtils.getJSONMapFromString(tested.storeDatetimeValueBuildDocument("AAA",
					"my_property", d).string());
			Assert.assertEquals(3, out.size());
			Assert.assertEquals("AAA", out.get("projectKey"));
			Assert.assertEquals("my_property", out.get("propertyName"));
			Assert.assertEquals(d.getTime(), DateTimeUtils.parseISODateTime((String) out.get("value")).getTime());
		}
	}

	@Test
	public void readAndStoreAndDeleteDatetimeValue() throws Exception {
		try {
			Client client = prepareESClientForUnitTest();

			JiraRiver tested = prepareJiraRiverInstanceForTest(null);
			tested.client = client;

			indexCreate("_river");

			Assert.assertFalse(tested.deleteDatetimeValue("ORG1", "testProperty_1_1"));

			tested
					.storeDatetimeValue("ORG1", "testProperty_1_1", DateTimeUtils.parseISODateTime("2012-09-03T18:12:45"), null);
			tested
					.storeDatetimeValue("ORG1", "testProperty_1_2", DateTimeUtils.parseISODateTime("2012-09-03T05:12:40"), null);
			tested
					.storeDatetimeValue("ORG2", "testProperty_1_1", DateTimeUtils.parseISODateTime("2012-09-02T08:12:30"), null);
			tested
					.storeDatetimeValue("ORG2", "testProperty_1_2", DateTimeUtils.parseISODateTime("2012-09-02T05:02:20"), null);

			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-03T18:12:45"),
					tested.readDatetimeValue("ORG1", "testProperty_1_1"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-03T05:12:40"),
					tested.readDatetimeValue("ORG1", "testProperty_1_2"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-02T08:12:30"),
					tested.readDatetimeValue("ORG2", "testProperty_1_1"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-02T05:02:20"),
					tested.readDatetimeValue("ORG2", "testProperty_1_2"));

			Assert.assertTrue(tested.deleteDatetimeValue("ORG1", "testProperty_1_1"));
			Assert.assertNull(tested.readDatetimeValue("ORG1", "testProperty_1_1"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-03T05:12:40"),
					tested.readDatetimeValue("ORG1", "testProperty_1_2"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-02T08:12:30"),
					tested.readDatetimeValue("ORG2", "testProperty_1_1"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-02T05:02:20"),
					tested.readDatetimeValue("ORG2", "testProperty_1_2"));

			Assert.assertTrue(tested.deleteDatetimeValue("ORG1", "testProperty_1_2"));
			Assert.assertNull(tested.readDatetimeValue("ORG1", "testProperty_1_2"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-02T08:12:30"),
					tested.readDatetimeValue("ORG2", "testProperty_1_1"));
			Assert.assertEquals(DateTimeUtils.parseISODateTime("2012-09-02T05:02:20"),
					tested.readDatetimeValue("ORG2", "testProperty_1_2"));

		} finally {
			finalizeESClientForUnitTest();
		}
	}

	@Test
	public void storeDatetimeValue_Bulk() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);

		BulkRequestBuilder esBulk = new BulkRequestBuilder(tested.client);
		tested.storeDatetimeValue("ORG", "prop", new Date(), esBulk);
		tested.storeDatetimeValue("ORG", "prop2", new Date(), esBulk);
		tested.storeDatetimeValue("ORG", "prop3", new Date(), esBulk);

		Assert.assertEquals(3, esBulk.numberOfActions());

	}

	@Test
	public void prepareValueStoreDocumentName() {
		Assert.assertEquals("_lastupdatedissue_ORG", JiraRiver.prepareValueStoreDocumentName("ORG", "lastupdatedissue"));
		Assert.assertEquals("_lastupdatedissue", JiraRiver.prepareValueStoreDocumentName(null, "lastupdatedissue"));
	}

	@Test
	public void prepareESBulkRequestBuilder() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		Client clientMock = tested.client;
		BulkRequestBuilder brb = new BulkRequestBuilder(clientMock);
		when(clientMock.prepareBulk()).thenReturn(brb);
		Assert.assertNotNull(tested.prepareESBulkRequestBuilder());
		verify(clientMock, times(1)).prepareBulk();
	}

	@Test
	public void reportIndexingFinished() throws Exception {
		IJIRAProjectIndexerCoordinator coordMock = mock(IJIRAProjectIndexerCoordinator.class);

		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		Client clientMock = tested.client;
		tested.coordinatorInstance = coordMock;

		// case - report correctly - no activity log
		{
			tested.reportIndexingFinished(new ProjectIndexingInfo("ORG", false, 10, 0, 0, null, true, 10, null));
			verify(coordMock, times(1)).reportIndexingFinished("ORG", true, false);
			Mockito.verifyZeroInteractions(clientMock);
		}
		{
			reset(coordMock);
			tested.reportIndexingFinished(new ProjectIndexingInfo("AAA", true, 10, 0, 0, null, false, 10, null));
			verify(coordMock, times(1)).reportIndexingFinished("AAA", false, true);
			Mockito.verifyZeroInteractions(clientMock);
		}

		// report correctly with activity log
		tested.activityLogIndexName = "alindex";
		tested.activityLogTypeName = "altype";
		{
			IndexRequestBuilder irb = Mockito.mock(IndexRequestBuilder.class);
			Mockito.when(irb.setSource(Mockito.any(XContentBuilder.class))).thenReturn(irb);
			@SuppressWarnings("unchecked")
			ListenableActionFuture<IndexResponse> laf = Mockito.mock(ListenableActionFuture.class);
			Mockito.when(irb.execute()).thenReturn(laf);
			when(clientMock.prepareIndex("alindex", "altype")).thenReturn(irb);
			tested.reportIndexingFinished(new ProjectIndexingInfo("ORG", false, 10, 0, 0, null, true, 10, null));
			Mockito.verify(irb).setSource(Mockito.any(XContentBuilder.class));
			Mockito.verify(irb).execute();
			Mockito.verify(laf).actionGet();
		}

		// case - no exception if coordinatorInstance is null
		tested = prepareJiraRiverInstanceForTest(null);
		tested.reportIndexingFinished(new ProjectIndexingInfo("ORG", false, 10, 0, 0, null, true, 10, null));

	}

	@Test
	public void prepareESScrollSearchRequestBuilder() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		Client clientMock = tested.client;

		SearchRequestBuilder srb = new SearchRequestBuilder(clientMock);
		when(clientMock.prepareSearch("myIndex")).thenReturn(srb);

		tested.prepareESScrollSearchRequestBuilder("myIndex");

		Assert.assertNotNull(srb.request().scroll());
		Assert.assertEquals(SearchType.SCAN, srb.request().searchType());
		verify(clientMock).prepareSearch("myIndex");
	}

	@Test
	public void getRiverOperationInfo_activityLogDisabled() throws Exception {

		JiraRiver tested = prepareJiraRiverInstanceForTest(null);

		IJIRAProjectIndexerCoordinator coordMock = mock(IJIRAProjectIndexerCoordinator.class);
		tested.coordinatorInstance = coordMock;

		List<ProjectIndexingInfo> currentIndexings = new ArrayList<ProjectIndexingInfo>();
		currentIndexings.add(new ProjectIndexingInfo("ORG", true, 256, 10, 0, DateTimeUtils
				.parseISODateTime("2012-09-27T09:21:25.422Z"), false, 0, null));
		currentIndexings.add(new ProjectIndexingInfo("AAA", false, 15, 0, 0, DateTimeUtils
				.parseISODateTime("2012-09-27T09:21:24.422Z"), false, 0, null));
		when(coordMock.getCurrentProjectIndexingInfo()).thenReturn(currentIndexings);

		tested.allIndexedProjectsKeysNextRefresh = Long.MAX_VALUE;
		tested.allIndexedProjectsKeys = new ArrayList<String>();
		tested.allIndexedProjectsKeys.add("ORG");
		tested.allIndexedProjectsKeys.add("AAA");
		tested.allIndexedProjectsKeys.add("JJJ");
		tested.allIndexedProjectsKeys.add("FFF");

		tested.lastProjectIndexingInfo.put("ORG",
				new ProjectIndexingInfo("ORG", true, 125, 10, 0, DateTimeUtils.parseISODateTime("2012-09-27T09:15:25.422Z"),
						true, 1500, null));
		tested.lastProjectIndexingInfo.put("JJJ",
				new ProjectIndexingInfo("JJJ", false, 12, 0, 0, DateTimeUtils.parseISODateTime("2012-09-27T09:12:25.422Z"),
						false, 1800, "JIRA timeout"));

		// case - nothing stored in audit log index - no exception!
		String info = tested.getRiverOperationInfo(new DiscoveryNode("My Node", "fsdfsdfxzd",
				DummyTransportAddress.INSTANCE, new HashMap<String, String>(), Version.CURRENT), DateTimeUtils
				.parseISODateTime("2012-09-27T09:21:26.422Z"));
		TestUtils.assertStringFromClasspathFile("/asserts/JiraRiver_getRiverOperationInfo_1.json", info);

	}

	@Test
	public void getRiverOperationInfo_activityLogEnabled() throws Exception {
		try {

			Client client = prepareESClientForUnitTest();

			JiraRiver tested = prepareJiraRiverInstanceForTest(null);
			tested.client = client;
			tested.activityLogIndexName = "activity_log_index";
			tested.activityLogTypeName = "jira_river_indexupdate";

			IJIRAProjectIndexerCoordinator coordMock = mock(IJIRAProjectIndexerCoordinator.class);
			tested.coordinatorInstance = coordMock;

			List<ProjectIndexingInfo> currentIndexings = new ArrayList<ProjectIndexingInfo>();
			currentIndexings.add(new ProjectIndexingInfo("ORG", true, 256, 10, 0, DateTimeUtils
					.parseISODateTime("2012-09-27T09:21:25.422Z"), false, 0, null));
			currentIndexings.add(new ProjectIndexingInfo("AAA", false, 15, 0, 0, DateTimeUtils
					.parseISODateTime("2012-09-27T09:21:24.422Z"), false, 0, null));
			when(coordMock.getCurrentProjectIndexingInfo()).thenReturn(currentIndexings);

			tested.allIndexedProjectsKeysNextRefresh = Long.MAX_VALUE;
			tested.allIndexedProjectsKeys = new ArrayList<String>();
			tested.allIndexedProjectsKeys.add("ORG");
			tested.allIndexedProjectsKeys.add("AAA");
			tested.allIndexedProjectsKeys.add("JJJ");
			tested.allIndexedProjectsKeys.add("FFF");

			tested.lastProjectIndexingInfo.put("ORG",
					new ProjectIndexingInfo("ORG", true, 125, 10, 0, DateTimeUtils.parseISODateTime("2012-09-27T09:15:25.422Z"),
							true, 1500, null));
			tested.lastProjectIndexingInfo.put("JJJ",
					new ProjectIndexingInfo("JJJ", false, 12, 0, 0, DateTimeUtils.parseISODateTime("2012-09-27T09:12:25.422Z"),
							false, 1800, "JIRA timeout"));

			// case - nothing stored in audit log index - no exception!
			String info = tested.getRiverOperationInfo(new DiscoveryNode("My Node", "fsdfsdfxzd",
					DummyTransportAddress.INSTANCE, new HashMap<String, String>(), Version.CURRENT), DateTimeUtils
					.parseISODateTime("2012-09-27T09:21:26.422Z"));
			TestUtils.assertStringFromClasspathFile("/asserts/JiraRiver_getRiverOperationInfo_1.json", info);

			// case - last indexed record into ES index for FFF project found
			indexCreate(tested.activityLogIndexName);
			client.admin().indices().preparePutMapping(tested.activityLogIndexName).setType(tested.activityLogTypeName)
					.setSource(TestUtils.readStringFromClasspathFile("/examples/jira_river_indexupdate.json")).execute()
					.actionGet();

			tested.writeActivityLogRecord(new ProjectIndexingInfo("FFF", false, 12, 0, 0, DateTimeUtils
					.parseISODateTime("2012-09-27T08:10:25.422Z"), true, 181, null));
			tested.writeActivityLogRecord(new ProjectIndexingInfo("FFF", false, 125, 0, 0, DateTimeUtils
					.parseISODateTime("2012-09-27T08:11:25.422Z"), true, 1810, null));
			tested.refreshSearchIndex(tested.activityLogIndexName);
			info = tested.getRiverOperationInfo(new DiscoveryNode("My Node", "fsdfsdfxzd", DummyTransportAddress.INSTANCE,
					new HashMap<String, String>(), Version.CURRENT), DateTimeUtils.parseISODateTime("2012-09-27T09:21:26.422Z"));
			TestUtils.assertStringFromClasspathFile("/asserts/JiraRiver_getRiverOperationInfo_2.json", info);

		} finally {
			finalizeESClientForUnitTest();
		}

	}

	@Test
	public void forceFullReindex() throws Exception {

		JiraRiver tested = prepareJiraRiverInstanceForTest(null);
		IJIRAProjectIndexerCoordinator coordinatorMock = mock(IJIRAProjectIndexerCoordinator.class);
		tested.coordinatorInstance = coordinatorMock;

		// case - all projects but no any exists
		{
			tested.allIndexedProjectsKeys = null;
			Assert.assertEquals("", tested.forceFullReindex(null));
			Mockito.verifyNoMoreInteractions(coordinatorMock);
		}

		// case - all projects and some exists
		{
			reset(coordinatorMock);
			tested.allIndexedProjectsKeys = new ArrayList<String>();
			tested.allIndexedProjectsKeys.add("ORG");
			tested.allIndexedProjectsKeys.add("AAA");
			Assert.assertEquals("ORG,AAA", tested.forceFullReindex(null));
			verify(coordinatorMock).forceFullReindex("ORG");
			verify(coordinatorMock).forceFullReindex("AAA");
			Mockito.verifyNoMoreInteractions(coordinatorMock);
		}

		// case - one project not exists
		{
			reset(coordinatorMock);
			Assert.assertNull(tested.forceFullReindex("BBB"));
			Mockito.verifyNoMoreInteractions(coordinatorMock);

		}

		// case - one project which exists
		{
			reset(coordinatorMock);
			Assert.assertEquals("ORG", tested.forceFullReindex("ORG"));
			verify(coordinatorMock).forceFullReindex("ORG");
			Mockito.verifyNoMoreInteractions(coordinatorMock);

			reset(coordinatorMock);
			Assert.assertEquals("AAA", tested.forceFullReindex("AAA"));
			verify(coordinatorMock).forceFullReindex("AAA");
			Mockito.verifyNoMoreInteractions(coordinatorMock);
		}
	}

	@Test
	public void createLogger() throws Exception {

		JiraRiver tested = prepareJiraRiverInstanceForTest(null);

		ESLogger logger = tested.createLogger(JIRAProjectIndexerCoordinator.class);
		Assert.assertNotNull(logger);
		Assert.assertEquals("org.elasticsearch.org.jboss.elasticsearch.river.jira.JIRAProjectIndexerCoordinator",
				logger.getName());
		Assert.assertEquals(" [jira][" + RIVER_NAME + "] ", logger.getPrefix());
	}

	@Test
	public void riverName() throws Exception {
		JiraRiver tested = prepareJiraRiverInstanceForTest(null);

		RiverName rn = tested.riverName();
		Assert.assertEquals(RIVER_NAME, rn.getName());
		Assert.assertEquals("jira", rn.getType());
	}

	/**
	 * Prepare {@link JiraRiver} instance for unit test, with Mockito moceked jiraClient and elasticSearchClient.
	 * 
	 * @param jiraSettingsAdd additional/optional config properties to be added into <code>jira</code> configuration node
	 * @return instance for tests
	 * @throws Exception from constructor
	 */
	protected JiraRiver prepareJiraRiverInstanceForTest(Map<String, Object> jiraSettingsAdd) throws Exception {
		return prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettingsAdd, null, true);
	}

	/**
	 * Prepare {@link JiraRiver} instance for unit test, with Mockito moceked jiraClient and elasticSearchClient.
	 * 
	 * @param urlBase parameter for jira settings
	 * @param jiraSettingsAdd additional/optional config properties to be added into <code>jira</code> configuration node
	 * @param toplevelSettingsAdd additional/optional config properties to be added into toplevel node. Do not add
	 *          <code>jira</code> here, will be ignored.
	 * @param jiraClientMock if set to true then Mockito mock instance is createdand set into {@link JiraRiver#jiraClient}
	 * @return instance for tests
	 * @throws Exception from constructor
	 */
	public static JiraRiver prepareJiraRiverInstanceForTest(String urlBase, Map<String, Object> jiraSettingsAdd,
			Map<String, Object> toplevelSettingsAdd, boolean jiraClientMock) throws Exception {
		Map<String, Object> settings = new HashMap<String, Object>();
		if (toplevelSettingsAdd != null)
			settings.putAll(toplevelSettingsAdd);
		if (urlBase != null || jiraSettingsAdd != null) {
			Map<String, Object> jiraSettings = new HashMap<String, Object>();
			settings.put("jira", jiraSettings);
			if (jiraSettingsAdd != null)
				jiraSettings.putAll(jiraSettingsAdd);
			jiraSettings.put("urlBase", urlBase);
		}

		Settings gs = mock(Settings.class);
		RiverSettings rs = new RiverSettings(gs, settings);
		Client clientMock = mock(Client.class);
		JiraRiver tested = new JiraRiver(new RiverName("jira", RIVER_NAME), rs, clientMock);
		if (jiraClientMock) {
			IJIRAClient jClientMock = mock(IJIRAClient.class);
			tested.jiraClient = jClientMock;
		}
		return tested;
	}

}
