/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.jboss.elasticsearch.river.jira.testtools.ESRealClientTestBase;
import org.jboss.elasticsearch.river.jira.testtools.MockThread;
import org.jboss.elasticsearch.river.jira.testtools.TestUtils;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JiraRiver}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiverTest extends ESRealClientTestBase {

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
    Assert.assertEquals("my_jira_river", tested.indexName);
    Assert.assertEquals(JiraRiver.INDEX_ISSUE_TYPE_NAME_DEFAULT, tested.typeName);
    Assert.assertEquals(50, tested.jiraClient.getListJIRAIssuesMax());

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
    jiraSettings.put("maxIssuesPerRequest", 20);
    jiraSettings.put("timeout", "5s");
    jiraSettings.put("jqlTimeZone", "Europe/Prague");
    indexSettings.put("index", "my_index_name");
    indexSettings.put("type", "type_test");
    tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd, false);

    Assert.assertEquals(5, tested.maxIndexingThreads);
    Assert.assertEquals(20 * 60 * 1000, tested.indexUpdatePeriod);
    Assert.assertEquals(5 * 60 * 60 * 1000, tested.indexFullUpdatePeriod);
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
        ((JIRA5RestIssueIndexStructureBuilder) tested.jiraIssueIndexStructureBuilder).typeName);
    Assert.assertEquals(tested.riverName().getName(),
        ((JIRA5RestIssueIndexStructureBuilder) tested.jiraIssueIndexStructureBuilder).riverName);

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
  public void parseTimeValue() {
    Map<String, Object> jiraSettings = new HashMap<String, Object>();

    // test defaults
    Assert.assertEquals(0, JiraRiver.parseTimeValue(jiraSettings, "nonexist", 1250, null));
    Assert.assertEquals(12, JiraRiver.parseTimeValue(null, "nonexist", 12, TimeUnit.MILLISECONDS));
    Assert.assertEquals(1250, JiraRiver.parseTimeValue(jiraSettings, "nonexist", 1250, TimeUnit.MILLISECONDS));

    // test correct values parsing
    jiraSettings.put("mstest", "250");
    jiraSettings.put("mstest2", "255ms");
    jiraSettings.put("secondtest", "250s");
    jiraSettings.put("minutetest", "50m");
    jiraSettings.put("hourtest", "2h");
    jiraSettings.put("daytest", "2d");
    jiraSettings.put("weektest", "2w");
    jiraSettings.put("zerotest", "0");
    jiraSettings.put("negativetest", "-1");
    Assert.assertEquals(250, JiraRiver.parseTimeValue(jiraSettings, "mstest", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(255, JiraRiver.parseTimeValue(jiraSettings, "mstest2", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(250 * 1000, JiraRiver.parseTimeValue(jiraSettings, "secondtest", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(50 * 60 * 1000,
        JiraRiver.parseTimeValue(jiraSettings, "minutetest", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(2 * 24 * 60 * 60 * 1000,
        JiraRiver.parseTimeValue(jiraSettings, "daytest", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(2 * 7 * 24 * 60 * 60 * 1000,
        JiraRiver.parseTimeValue(jiraSettings, "weektest", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(0, JiraRiver.parseTimeValue(jiraSettings, "zerotest", 1250, TimeUnit.MILLISECONDS));
    Assert.assertEquals(-1, JiraRiver.parseTimeValue(jiraSettings, "negativetest", 1250, TimeUnit.MILLISECONDS));

    // test error handling
    jiraSettings.put("errortest", "w");
    jiraSettings.put("errortest2", "ahojs");
    try {
      JiraRiver.parseTimeValue(jiraSettings, "errortest", 1250, TimeUnit.MILLISECONDS);
      Assert.fail("ElasticSearchParseException must be thrown");
    } catch (ElasticSearchParseException e) {
      // ok
    }
    try {
      JiraRiver.parseTimeValue(jiraSettings, "errortest2", 1250, TimeUnit.MILLISECONDS);
      Assert.fail("ElasticSearchParseException must be thrown");
    } catch (ElasticSearchParseException e) {
      // ok
    }
  }

  @Test
  public void readAndStoreDatetimeValue() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver tested = prepareJiraRiverInstanceForTest(null);
      tested.client = client;

      tested.storeDatetimeValue("ORG1", "testProperty_1_1", Utils.parseISODateTime("2012-09-03T18:12:45"), null);
      tested.storeDatetimeValue("ORG1", "testProperty_1_2", Utils.parseISODateTime("2012-09-03T05:12:40"), null);
      tested.storeDatetimeValue("ORG2", "testProperty_1_1", Utils.parseISODateTime("2012-09-02T08:12:30"), null);
      tested.storeDatetimeValue("ORG2", "testProperty_1_2", Utils.parseISODateTime("2012-09-02T05:02:20"), null);

      Assert.assertEquals(Utils.parseISODateTime("2012-09-03T18:12:45"),
          tested.readDatetimeValue("ORG1", "testProperty_1_1"));
      Assert.assertEquals(Utils.parseISODateTime("2012-09-03T05:12:40"),
          tested.readDatetimeValue("ORG1", "testProperty_1_2"));
      Assert.assertEquals(Utils.parseISODateTime("2012-09-02T08:12:30"),
          tested.readDatetimeValue("ORG2", "testProperty_1_1"));
      Assert.assertEquals(Utils.parseISODateTime("2012-09-02T05:02:20"),
          tested.readDatetimeValue("ORG2", "testProperty_1_2"));

    } finally {
      finalizeESClientForUnitTest();
    }
  }

  @Test
  public void storeDatetimeValue_Bulk() throws Exception {
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);

    BulkRequestBuilder esBulk = new BulkRequestBuilder(null);
    tested.storeDatetimeValue("ORG", "prop", new Date(), esBulk);
    tested.storeDatetimeValue("ORG", "prop2", new Date(), esBulk);
    tested.storeDatetimeValue("ORG", "prop3", new Date(), esBulk);

    Assert.assertEquals(3, esBulk.numberOfActions());

  }

  @Test
  public void prepareValueStoreDocumentName() {
    Assert.assertEquals("_lastupdatedissue_ORG", JiraRiver.prepareValueStoreDocumentName("ORG", "lastupdatedissue"));
  }

  @Test
  public void prepareESBulkRequestBuilder() throws Exception {
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);
    Client clientMock = tested.client;
    when(clientMock.prepareBulk()).thenReturn(new BulkRequestBuilder(null));
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
      tested.reportIndexingFinished("ORG", true, false, 10, 0, null, 10, null);
      verify(coordMock, times(1)).reportIndexingFinished("ORG", true, false);
      Mockito.verifyZeroInteractions(clientMock);
    }
    {
      reset(coordMock);
      tested.reportIndexingFinished("AAA", false, true, 0, 0, null, 10, null);
      verify(coordMock, times(1)).reportIndexingFinished("AAA", false, true);
      Mockito.verifyZeroInteractions(clientMock);
    }

    // report correctly with activity log
    tested.activityLogIndexName = "alindex";
    tested.activityLogTypeName = "altype";
    {
      IndexRequestBuilder irb = new IndexRequestBuilder(null);
      when(clientMock.prepareIndex("alindex", "altype")).thenReturn(irb);
      tested.reportIndexingFinished("AAA", false, true, 0, 0, null, 10, null);
      Assert.assertNotNull(irb.request().source());
    }

    // case - no exception if coordinatorInstance is null
    tested = prepareJiraRiverInstanceForTest(null);
    tested.reportIndexingFinished("ORG", true, false, 0, 0, null, 10, null);

  }

  @Test
  public void prepareUpdateActivityLogDocument() throws Exception {
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);

    TestUtils.assertStringFromClasspathFile(
        "/asserts/prepareUpdateActivityLogDocument_1.json",
        tested.prepareUpdateActivityLogDocument("ORG", true, true, 10, 1,
            Utils.parseISODateTime("2012-09-10T12:55:58Z"), 1250, null).string());

    TestUtils.assertStringFromClasspathFile(
        "/asserts/prepareUpdateActivityLogDocument_2.json",
        tested.prepareUpdateActivityLogDocument("ORG", false, false, 5, 0,
            Utils.parseISODateTime("2012-09-10T12:56:50Z"), 125, "Error message").string());

  }

  @Test
  public void close() throws Exception {

    // case - close all correctly
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);
    MockThread mockThread = new MockThread();
    tested.coordinatorThread = mockThread;
    tested.coordinatorInstance = mock(IJIRAProjectIndexerCoordinator.class);
    Assert.assertFalse(tested.isClosed());
    Assert.assertNotNull(tested.coordinatorThread);
    Assert.assertNotNull(tested.coordinatorInstance);

    tested.close();
    Assert.assertTrue(tested.isClosed());
    Assert.assertTrue(mockThread.interruptWasCalled);
    Assert.assertNull(tested.coordinatorThread);
    Assert.assertNull(tested.coordinatorInstance);

    // case - no exception when coordinatorThread and coordinatorInstance is null
    tested = prepareJiraRiverInstanceForTest(null);
    tested.coordinatorThread = null;
    tested.coordinatorInstance = null;
    Assert.assertFalse(tested.isClosed());
    Assert.assertNull(tested.coordinatorThread);
    Assert.assertNull(tested.coordinatorInstance);

    tested.close();
    Assert.assertTrue(tested.isClosed());
    Assert.assertNull(tested.coordinatorThread);
    Assert.assertNull(tested.coordinatorInstance);

  }

  @Test
  public void prepareESScrollSearchRequestBuilder() throws Exception {
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);
    Client clientMock = tested.client;

    SearchRequestBuilder srb = new SearchRequestBuilder(null);
    when(clientMock.prepareSearch("myIndex")).thenReturn(srb);

    tested.prepareESScrollSearchRequestBuilder("myIndex");

    Assert.assertNotNull(srb.request().scroll());
    Assert.assertEquals(SearchType.SCAN, srb.request().searchType());
    verify(clientMock).prepareSearch("myIndex");
    Mockito.verifyNoMoreInteractions(clientMock);

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
    JiraRiver tested = new JiraRiver(new RiverName("jira", "my_jira_river"), rs, clientMock);
    if (jiraClientMock) {
      IJIRAClient jClientMock = mock(IJIRAClient.class);
      tested.jiraClient = jClientMock;
    }
    return tested;
  }

}
