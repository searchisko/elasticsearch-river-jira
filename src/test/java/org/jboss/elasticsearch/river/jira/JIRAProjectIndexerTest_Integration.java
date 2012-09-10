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
import org.jboss.elasticsearch.river.jira.testtools.ESRealClientTestBase;
import org.jboss.elasticsearch.river.jira.testtools.TestUtils;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * jUnit test for {@link JIRAProjectIndexer} which tests search index update processes against embedded inmemory elastic
 * search node.<br>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexerTest_Integration extends ESRealClientTestBase {

  private static final String CFG_RIVER_NAME = "jira_river";
  private static final String CFG_INDEX_NAME = CFG_RIVER_NAME;
  private static final String CFG_TYPE_ISSUE = "jira_issue";
  private static final String CFG_TYPE_ISSUE_COMMENT = "jira_issue_comment";

  private static final String CFG_TYPE_ACTIVITY = "jira_river_indexupdate";
  private static final String CFG_INDEX_NAME_ACTIVITY = "activity_index";

  private static final String PROJECT_KEY = "ORG";

  @Test
  public void incrementalUpdateCommentsEMBEDDED() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = initJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;
      jiraRiverMock.activityLogIndexName = CFG_INDEX_NAME_ACTIVITY;
      jiraRiverMock.activityLogTypeName = CFG_TYPE_ACTIVITY;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
      initIndexStructures(client, structureBuilder.commentIndexingMode);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate), null))
          .thenReturn(prepareChangedIssuesJIRACallResults("ORG-1501", "ORG-1513", "ORG-1514"));

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
      assertNumDocumentsInIndex(client, CFG_INDEX_NAME_ACTIVITY, CFG_TYPE_ACTIVITY, 1);

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock, structureBuilder);
      when(
          jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate2), null))
          .thenReturn(prepareChangedIssuesJIRACallResults("ORG-1501-updated"));

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

      assertNumDocumentsInIndex(client, CFG_INDEX_NAME_ACTIVITY, CFG_TYPE_ACTIVITY, 2);

    } finally {
      finalizeESClientForUnitTest();
    }
  }

  @Test
  public void incrementalUpdateCommentsCHILD() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = initJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.CHILD;
      initIndexStructures(client, structureBuilder.commentIndexingMode);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate), null))
          .thenReturn(prepareChangedIssuesJIRACallResults("ORG-1501", "ORG-1513", "ORG-1514"));

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
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1513");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1514", "12716241");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1501", "12714153", "12714252");

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, false, jClientMock, jiraRiverMock, structureBuilder);
      when(
          jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, Utils.roundDateToMinutePrecise(lastIssueUpdatedDate2), null))
          .thenReturn(prepareChangedIssuesJIRACallResults("ORG-1501-updated"));

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
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1513");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1514", "12716241");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1501", "12714153", "12714252", "12714253");

    } finally {
      finalizeESClientForUnitTest();
    }

  }

  @Test
  public void fullUpdateCommentsEMBEDDED() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = initJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.EMBEDDED;
      initIndexStructures(client, structureBuilder.commentIndexingMode);
      initDocumentsForProjectAAA(jiraRiverMock, structureBuilder);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, true, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, null, null)).thenReturn(
          prepareChangedIssuesJIRACallResults("ORG-1501", "ORG-1513", "ORG-1514"));

      tested.run();

      Assert.assertEquals(3, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(true, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate2 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:27:25.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate2, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514", "AAA-1", "AAA-2");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT);
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun1, "ORG-1501", "ORG-1513", "ORG-1514");

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, true, jClientMock, jiraRiverMock, structureBuilder);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, null, null)).thenReturn(
          prepareChangedIssuesJIRACallResults("ORG-1513", "ORG-1501-updated"));

      tested.run();

      Assert.assertEquals(2, tested.updatedCount);
      Assert.assertEquals(1, tested.deleteCount);
      Assert.assertEquals(true, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate3 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:28:21.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate3, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "AAA-1", "AAA-2");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT);
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1501", "ORG-1513");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE, dateStartRun2, "AAA-1", "AAA-2");

    } finally {
      finalizeESClientForUnitTest();
    }
  }

  @Test
  public void fullUpdateCommentsCHILD() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = initJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.CHILD;
      initIndexStructures(client, structureBuilder.commentIndexingMode);
      initDocumentsForProjectAAA(jiraRiverMock, structureBuilder);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, true, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, null, null)).thenReturn(
          prepareChangedIssuesJIRACallResults("ORG-1501", "ORG-1513", "ORG-1514"));

      tested.run();

      Assert.assertEquals(3, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(true, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate2 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:27:25.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate2, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514", "AAA-1", "AAA-2");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "12714153", "12714252", "12716241");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun1, "ORG-1501", "ORG-1513", "ORG-1514");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1513");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1514", "12716241");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1501", "12714153", "12714252");

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, true, jClientMock, jiraRiverMock, structureBuilder);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, null, null)).thenReturn(
          prepareChangedIssuesJIRACallResults("ORG-1513", "ORG-1501-updated"));

      tested.run();

      Assert.assertEquals(2, tested.updatedCount);
      Assert.assertEquals(1, tested.deleteCount);
      Assert.assertEquals(2, tested.deleteCommentsCount);
      Assert.assertEquals(true, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate3 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:28:21.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate3, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "AAA-1", "AAA-2");
      // note comment "12714252" was removed and "12714253" was added in "ORG-1501-updated"
      // comment 12716241 removed due "ORG-1514" remove
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "12714153", "12714253");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1501", "ORG-1513");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE_COMMENT, dateStartRun2, "12714153", "12714253");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE, dateStartRun2, "AAA-1", "AAA-2");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE_COMMENT, dateStartRun2);
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1513");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1501", "12714153", "12714253");

    } finally {
      finalizeESClientForUnitTest();
    }

  }

  @Test
  public void fullUpdateCommentsSTANDALONE() throws Exception {
    try {
      Client client = prepareESClientForUnitTest();

      JiraRiver jiraRiverMock = initJiraRiverInstanceForTest(client);
      IJIRAClient jClientMock = jiraRiverMock.jiraClient;

      JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(CFG_RIVER_NAME,
          CFG_INDEX_NAME, CFG_TYPE_ISSUE, "http://issues.jboss.org", null);
      structureBuilder.commentIndexingMode = IssueCommentIndexingMode.STANDALONE;
      initIndexStructures(client, structureBuilder.commentIndexingMode);
      initDocumentsForProjectAAA(jiraRiverMock, structureBuilder);

      // run 1 to insert documents
      Date dateStartRun1 = new Date();
      JIRAProjectIndexer tested = new JIRAProjectIndexer(PROJECT_KEY, true, jClientMock, jiraRiverMock,
          structureBuilder);
      Date lastIssueUpdatedDate = Utils.parseISODateTime("2012-09-06T02:26:53.000-0400");
      tested.storeLastIssueUpdatedDate(null, PROJECT_KEY, lastIssueUpdatedDate);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, null, null)).thenReturn(
          prepareChangedIssuesJIRACallResults("ORG-1501", "ORG-1513", "ORG-1514"));

      tested.run();

      Assert.assertEquals(3, tested.updatedCount);
      Assert.assertEquals(0, tested.deleteCount);
      Assert.assertEquals(true, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate2 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:27:25.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate2, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "ORG-1514", "AAA-1", "AAA-2");
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "12714153", "12714252", "12716241");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun1, "ORG-1501", "ORG-1513", "ORG-1514");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1513");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1514");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1501");

      // run 2 to update one document
      Thread.sleep(100);
      Date dateStartRun2 = new Date();
      Mockito.reset(jClientMock);
      tested = new JIRAProjectIndexer(PROJECT_KEY, true, jClientMock, jiraRiverMock, structureBuilder);
      when(jClientMock.getJIRAChangedIssues(PROJECT_KEY, 0, null, null)).thenReturn(
          prepareChangedIssuesJIRACallResults("ORG-1513", "ORG-1501-updated"));

      tested.run();

      Assert.assertEquals(2, tested.updatedCount);
      Assert.assertEquals(1, tested.deleteCount);
      Assert.assertEquals(2, tested.deleteCommentsCount);
      Assert.assertEquals(true, tested.fullUpdate);
      Assert.assertNotNull(tested.startTime);

      Date lastIssueUpdatedDate3 = Utils.parseISODateWithMinutePrecise("2012-09-06T03:28:21.000-0400");
      Assert.assertEquals(lastIssueUpdatedDate3, tested.readLastIssueUpdatedDate(PROJECT_KEY));

      assertDocumentsInIndex(client, CFG_TYPE_ISSUE, "ORG-1501", "ORG-1513", "AAA-1", "AAA-2");
      // note comment "12714252" was removed and "12714253" was added in "ORG-1501-updated"
      // comment 12716241 removed due "ORG-1514" remove
      assertDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "12714153", "12714253");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE, dateStartRun2, "ORG-1501", "ORG-1513");
      assertDocumentsUpdatedAfterDate(client, CFG_TYPE_ISSUE_COMMENT, dateStartRun2, "12714153", "12714253");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE, dateStartRun2, "AAA-1", "AAA-2");
      assertDocumentsUpdatedBeforeDate(client, CFG_TYPE_ISSUE_COMMENT, dateStartRun2);
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1513");
      assertChildDocumentsInIndex(client, CFG_TYPE_ISSUE_COMMENT, "ORG-1501");

    } finally {
      finalizeESClientForUnitTest();
    }

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

  /**
   * Assert child documents with given id's exists in {@value #CFG_INDEX_NAME} search index for given parent, and no any
   * other exists here.
   * 
   * @param client to be used
   * @param documentType type of document to check
   * @param parentDocumentId id of parent to check childs for
   * @param childDocumentIds list of document id's to check
   * 
   */
  protected void assertChildDocumentsInIndex(Client client, String documentType, String parentDocumentId,
      String... childDocumentIds) {

    SearchRequestBuilder srb = client.prepareSearch(CFG_INDEX_NAME).setTypes(documentType)
        .setQuery(QueryBuilders.matchAllQuery());
    srb.setFilter(FilterBuilders.termFilter("_parent", parentDocumentId));

    assertImplSearchResults(client, srb, childDocumentIds);
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
   * Assert number of documents of given type in search index.
   * 
   * @param client to be used
   * @param indexName name of index to check documents in
   * @param documentType type of document to check
   * @param expectedNum expected number of documents
   * 
   */
  protected void assertNumDocumentsInIndex(Client client, String indexName, String documentType, int expectedNum) {

    client.admin().indices().prepareRefresh(indexName).execute().actionGet();

    SearchRequestBuilder srb = client.prepareSearch(indexName).setTypes(documentType)
        .setQuery(QueryBuilders.matchAllQuery());

    SearchResponse resp = srb.execute().actionGet();
    Assert.assertEquals("Documents number is wrong", expectedNum, resp.hits().getTotalHits());
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

  protected ChangedIssuesResults prepareChangedIssuesJIRACallResults(String... issueKeys) throws IOException {
    List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
    if (issueKeys != null) {
      for (String key : issueKeys) {
        list.add(TestUtils.readJiraJsonIssueDataFromClasspathFile(key));
      }
    }
    return new ChangedIssuesResults(list, 0, 50, list.size());
  }

  protected JiraRiver initJiraRiverInstanceForTest(Client client) throws Exception {
    Map<String, Object> settings = new HashMap<String, Object>();
    Settings gs = mock(Settings.class);
    RiverSettings rs = new RiverSettings(gs, settings);
    JiraRiver tested = new JiraRiver(new RiverName("jira", CFG_RIVER_NAME), rs);
    tested.client = client;
    IJIRAClient jClientMock = mock(IJIRAClient.class);
    tested.jiraClient = jClientMock;
    return tested;
  }

  protected void initIndexStructures(Client client, IssueCommentIndexingMode commentMode) throws Exception {
    client.admin().indices().prepareCreate(CFG_INDEX_NAME).execute().actionGet();
    client.admin().indices().prepareCreate(CFG_INDEX_NAME_ACTIVITY).execute().actionGet();
    client.admin().indices().preparePutMapping(CFG_INDEX_NAME).setType(CFG_TYPE_ISSUE)
        .setSource(TestUtils.readStringFromClasspathFile("/mappings/jira_issue.json")).execute().actionGet();
    if (commentMode.isExtraDocumentIndexed()) {
      String commentMappingFilePath = "/mappings/jira_issue_comment.json";
      if (commentMode == IssueCommentIndexingMode.CHILD)
        commentMappingFilePath = "/mappings/jira_issue_comment-child.json";

      client.admin().indices().preparePutMapping(CFG_INDEX_NAME).setType(CFG_TYPE_ISSUE_COMMENT)
          .setSource(TestUtils.readStringFromClasspathFile(commentMappingFilePath)).execute().actionGet();
    }
  }

  /**
   * Adds two issues from <code>AAA</code> project into search index for tests - keys <code>AAA-1</code> and
   * <code>AAA-2</code>
   * 
   * @param jiraRiverMock to be used
   * @param structureBuilder to be used
   * @throws Exception
   */
  protected void initDocumentsForProjectAAA(JiraRiver jiraRiverMock,
      JIRA5RestIssueIndexStructureBuilder structureBuilder) throws Exception {
    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    JIRAProjectIndexer tested = new JIRAProjectIndexer("AAA", true, jiraClientMock, jiraRiverMock, structureBuilder);
    ChangedIssuesResults changedIssues = prepareChangedIssuesJIRACallResults("AAA-1", "AAA-2");
    when(jiraClientMock.getJIRAChangedIssues("AAA", 0, null, null)).thenReturn(changedIssues);
    tested.run();
    jiraRiverMock.refreshSearchIndex(CFG_INDEX_NAME);
  }

}
