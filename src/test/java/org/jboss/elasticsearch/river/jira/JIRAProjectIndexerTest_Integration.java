/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * jUnit test for {@link JIRAProjectIndexer} which tests search index update processes against embedded inmemmory
 * elastic search node.<br>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexerTest_Integration extends ESRealClientTestBase {

  private static final String CFG_RIVER_NAME = "jira_river";
  private static final String CFG_INDEX_NAME = CFG_RIVER_NAME;
  private static final String CFG_TYPE_ISSUE = "jira_issue";
  private static final String CFG_TYPE_ISSUE_COMMENT = "jira_issue_comment";

  private static final String PROJECT_KEY = "ORG";

  @Test
  public void incrementalUpdateCommentsEMBEDDED() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = prepareJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
      prepareIndexStructures(client);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);

      ChangedIssuesResults changedIssues = prepareChangedIssues("ORG-1501", "ORG-1513", "ORG-1514");
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate), null))
          .thenReturn(changedIssues);

      tested.run();

      Assert.assertEquals(3, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(false, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate2 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:27:25.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate2, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT);
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun1, "ORG-1501", "ORG-1513", "ORG-1514");

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock, structureBuilder);

      changedIssues = prepareChangedIssues("ORG-1501-updated");
      when(
          jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate2), null))
          .thenReturn(changedIssues);

      tested.run();

      Assert.assertEquals(1, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(false, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate3 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:28:21.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate3, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT);
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1501");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1513", "ORG-1514");

    } finally {
      finalizeESClientForUnitTest();
    }
  }

  @Test
  public void incrementalUpdateCommentsCHILD() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = prepareJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.CHILD;

      prepareIndexStructures(client);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);

      ChangedIssuesResults changedIssues = prepareChangedIssues("ORG-1501", "ORG-1513", "ORG-1514");
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate), null))
          .thenReturn(changedIssues);

      tested.run();

      Assert.assertEquals(3, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(false, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate2 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:27:25.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate2, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "12714153", "12714252", "12716241");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun1, "ORG-1501", "ORG-1513", "ORG-1514");

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock, structureBuilder);

      changedIssues = prepareChangedIssues("ORG-1501-updated");
      when(
          jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate2), null))
          .thenReturn(changedIssues);

      tested.run();

      Assert.assertEquals(1, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(false, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate3 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:28:21.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate3, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "12714153", "12714252", "12716241", "12714253");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1501");
      // note comment "12714252" was removed and "12714253" was added in "ORG-1501-updated", but incremental update do
      // not remove comments from index
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE_COMMENT, dateStartRun2, "12714153", "12714253");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1513", "ORG-1514");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE_COMMENT, dateStartRun2, "12716241", "12714252");

    } finally {
      finalizeESClientForUnitTest();
    }

  }

  @Test
  public void fullUpdateCommentsCHILD() {
    // TODO UNITTEST
  }

  @Test
  public void fullUpdateCommentsEMBEDDED() {
    // TODO UNITTEST
  }

  /**
   * Assert documents with given id's exists in {@value #CFG_INDEX_NAME} search index and no any other exists here.
   * 
   * @param client to be used
   * @param documentType type of document to check
   * @param documentIds list of document id's to check
   * 
   */
  protected void assertDocumentsInIndex(Client client, String documentType, String... documentIds) {

    SearchRequestBuilder srb = client.prepareSearch(CFG_INDEX_NAME).setTypes(documentType)
        .setQuery(QueryBuilders.matchAllQuery());

    assertImplSearchResults(client, srb, documentIds);
  }

  private void assertImplSearchResults(Client client, SearchRequestBuilder srb, String... documentIds) {
    client.admin().indices().prepareRefresh(CFG_INDEX_NAME).execute().actionGet();

    SearchResponse resp = srb.execute().actionGet();
    List<String> expected = Arrays.asList(documentIds);
    Assert.assertEquals("Documents number is wrong", expected.size(), resp.hits().getTotalHits());
    for (SearchHit hit : resp.hits().getHits()) {
      Assert.assertTrue("Document list can't contain document with id " + hit.id(), expected.contains(hit.id()));
    }
  }

  /**
   * Assert documents with given id's exists in {@value #CFG_INDEX_NAME} search index and was NOT updated after given
   * bound date (so was updated before).
   * 
   * @param client to be used
   * @param documentType type of document to check
   * @param boundDate bound date for check
   * @param documentIds list of document id's to check
   * 
   */
  protected void assertDocumentsUpdatedBeforeDate(Client client, String documentType, Date boundDate,
      String... documentIds) {
    assertImplDocumentsUpdatedDate(client, documentType, boundDate, true, documentIds);
  }

  /**
   * Assert documents with given id's exists in {@value #CFG_INDEX_NAME} search index and was updated after given bound
   * date.
   * 
   * @param client to be used
   * @param documentType type of document to check
   * @param boundDate bound date for check
   * @param documentIds list of document id's to check
   * 
   */
  protected void assertDocumentsUpdatedAfterDate(Client client, String documentType, Date boundDate,
      String... documentIds) {
    assertImplDocumentsUpdatedDate(client, documentType, boundDate, false, documentIds);
  }

  private void assertImplDocumentsUpdatedDate(Client client, String documentType, Date boundDate, boolean beforeDate,
      String... documentIds) {
    FilterBuilder filterTime = null;
    if (beforeDate) {
      filterTime = FilterBuilders.rangeFilter("_timestamp").lt(boundDate);
    } else {
      filterTime = FilterBuilders.rangeFilter("_timestamp").gte(boundDate);
    }
    SearchRequestBuilder srb = client.prepareSearch(CFG_INDEX_NAME).setTypes(documentType)
        .setQuery(QueryBuilders.matchAllQuery()).setFilter(filterTime);

    assertImplSearchResults(client, srb, documentIds);
  }

  protected ChangedIssuesResults prepareChangedIssues(String... issueKeys) throws IOException {
    List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
    if (issueKeys != null) {
      for (String key : issueKeys) {
        list.add(TestUtils.readJiraJsonIssueDataFromClasspathFile(key));
      }
    }
    return new ChangedIssuesResults(list, 0, 50, list.size());
  }

  protected JiraRiver prepareJiraRiverInstanceForTest(Client client) throws Exception {
    Map<String, Object> settings = new HashMap<String, Object>();
    Settings gs = mock(Settings.class);
    RiverSettings rs = new RiverSettings(gs, settings);
    JiraRiver tested = new JiraRiver(new RiverName("jira", CFG_RIVER_NAME), rs);
    tested.client = client;
    IJIRAClient jClientMock = mock(IJIRAClient.class);
    tested.jiraClient = jClientMock;
    return tested;
  }

  protected void prepareIndexStructures(Client client) throws Exception {
    client.admin().indices().prepareCreate(CFG_INDEX_NAME).execute().actionGet();
    client.admin().indices().preparePutMapping(CFG_INDEX_NAME).setType(CFG_TYPE_ISSUE)
        .setSource(TestUtils.readStringFromClasspathFile("/templates/jira_issue.json")).execute().actionGet();
    client.admin().indices().preparePutMapping(CFG_INDEX_NAME).setType(CFG_TYPE_ISSUE_COMMENT)
        .setSource(TestUtils.readStringFromClasspathFile("/templates/jira_issue_comment.json")).execute().actionGet();
  }

}
