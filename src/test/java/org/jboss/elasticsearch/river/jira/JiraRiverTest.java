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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.junit.Test;

/**
 * Unit test for {@link JiraRiver}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiverTest {

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
    Assert.assertEquals("my_jira_river", tested.indexName);
    Assert.assertEquals(JiraRiver.INDEX_TYPE_NAME_DEFAULT, tested.typeName);
    Assert.assertEquals(50, tested.jiraClient.getListJIRAIssuesMax());

    Map<String, Object> indexSettings = new HashMap<String, Object>();
    toplevelSettingsAdd.put("index", indexSettings);
    tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd, false);
    Assert.assertEquals("my_jira_river", tested.indexName);
    Assert.assertEquals(JiraRiver.INDEX_TYPE_NAME_DEFAULT, tested.typeName);

    // case - test river configuration reading
    jiraSettings.put("maxIndexingThreads", "5");
    jiraSettings.put("indexUpdatePeriod", "20");
    jiraSettings.put("maxIssuesPerRequest", 20);
    jiraSettings.put("timeout", 5000);
    indexSettings.put("index", "my_index_name");
    indexSettings.put("type", "type_test");
    tested = prepareJiraRiverInstanceForTest("https://issues.jboss.org", jiraSettings, toplevelSettingsAdd, false);

    Assert.assertEquals(5, tested.maxIndexingThreads);
    Assert.assertEquals(20 * 60 * 1000, tested.indexUpdatePeriod);
    Assert.assertEquals("my_index_name", tested.indexName);
    Assert.assertEquals("type_test", tested.typeName);
    Assert.assertEquals(20, tested.jiraClient.getListJIRAIssuesMax());

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

    List<String> pl = JiraRiver.parseCsvString("ORG,UUUU,PEM,SU07");
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

    List<String> pl = JiraRiver.parseCsvString("ORG,UUUU,PEM,SU07");
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
  public void readDatetimeValue() {
    // UNITTEST hard to do due ElasticSearch Client calls
  }

  @Test
  public void storeDatetimeValue() {
    // UNITTEST hard to do due ElasticSearch Client calls
  }

  @Test
  public void prepareValueStoreDocumentName() {
    Assert.assertEquals("_lastupdatedissue_ORG", JiraRiver.prepareValueStoreDocumentName("ORG", "lastupdatedissue"));
  }

  @Test
  public void getESBulkRequestBuilder() throws Exception {
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);
    Client clientMock = tested.client;
    when(clientMock.prepareBulk()).thenReturn(new BulkRequestBuilder(null));
    Assert.assertNotNull(tested.getESBulkRequestBuilder());
    verify(clientMock, times(1)).prepareBulk();
  }

  @Test
  public void reportIndexingFinished() throws Exception {
    IJIRAProjectIndexerCoordinator coordMock = mock(IJIRAProjectIndexerCoordinator.class);

    // case - report correctly
    JiraRiver tested = prepareJiraRiverInstanceForTest(null);
    tested.coordinatorInstance = coordMock;

    tested.reportIndexingFinished("ORG", true, 0, 10, null);
    verify(coordMock, times(1)).reportIndexingFinished("ORG", true);

    reset(coordMock);
    tested.reportIndexingFinished("AAA", false, 0, 10, null);
    verify(coordMock, times(1)).reportIndexingFinished("AAA", false);

    // case - no exception if coordinatorInstance is null
    tested = prepareJiraRiverInstanceForTest(null);
    tested.reportIndexingFinished("ORG", true, 0, 10, null);

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
  protected JiraRiver prepareJiraRiverInstanceForTest(String urlBase, Map<String, Object> jiraSettingsAdd,
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

  @Test
  public void parseCsvString() {
    Assert.assertNull(JiraRiver.parseCsvString(null));
    Assert.assertNull(JiraRiver.parseCsvString(""));
    Assert.assertNull(JiraRiver.parseCsvString("    "));
    Assert.assertNull(JiraRiver.parseCsvString("  ,, ,   ,   "));
    List<String> r = JiraRiver.parseCsvString(" ORG ,UUUU, , PEM  , ,SU07  ");
    Assert.assertEquals(4, r.size());
    Assert.assertEquals("ORG", r.get(0));
    Assert.assertEquals("UUUU", r.get(1));
    Assert.assertEquals("PEM", r.get(2));
    Assert.assertEquals("SU07", r.get(3));
  }

}
