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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

/**
 * Unit test for {@link JIRAProjectIndexer}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexerTest {

  /**
   * Main method used to run integration tests with real JIRA call.
   * 
   * @param args not used
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    IJIRAClient jiraClient = new JIRA5RestClient("https://issues.jboss.org", null, null, 7000);

    JIRAProjectIndexer tested = new JIRAProjectIndexer("ORG", jiraClient, null);
    tested.run();
  }

  @Test
  public void constructor() {
    IJIRAClient jiraClient = new JIRA5RestClient("https://issues.jboss.org", null, null, 7000);
    JIRAProjectIndexer tested = new JIRAProjectIndexer("ORG", jiraClient, null);
    Assert.assertEquals("ORG", tested.projectKey);
    Assert.assertEquals(jiraClient, tested.jiraClient);
  }

  @Test
  public void processUpdate_Basic() throws Exception {

    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    JIRAProjectIndexer tested = new JIRAProjectIndexer("ORG", jiraClientMock, null);

    List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();

    // test case with empty result list
    when(jiraClientMock.getJIRAChangedIssues("ORG", 0, null, null)).thenReturn(
        new ChangedIssuesResults(issues, 0, 50, 0));
    Assert.assertEquals(0, tested.processUpdate());
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 0, null, null);
    // TODO UNITTEST assert results and persistence of latestUpdate field

    // test case with one "page" of results
    reset(jiraClientMock);
    addIssueMock(issues, "ORG-45", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues, "ORG-46", "2012-08-14T08:01:00.000-0400");
    addIssueMock(issues, "ORG-47", "2012-08-14T08:02:00.000-0400");
    when(jiraClientMock.getJIRAChangedIssues("ORG", 0, null, null)).thenReturn(
        new ChangedIssuesResults(issues, 0, 50, 3));
    Assert.assertEquals(3, tested.processUpdate());
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 0, null, null);
    // TODO UNITTEST assert results and persistence of latestUpdate field

  }

  @Test
  public void processUpdate_PagedByDate() throws Exception {

    // test case with more than one "page" of results with different updated dates
    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    JIRAProjectIndexer tested = new JIRAProjectIndexer("ORG", jiraClientMock, null);

    List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();
    addIssueMock(issues, "ORG-45", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues, "ORG-46", "2012-08-14T08:01:00.000-0400");
    addIssueMock(issues, "ORG-47", "2012-08-14T08:02:00.000-0400");
    Date after2 = ISODateTimeFormat.dateTimeParser().parseDateTime("2012-08-14T08:02:00.000-0400").toDate();
    List<Map<String, Object>> issues2 = new ArrayList<Map<String, Object>>();
    addIssueMock(issues2, "ORG-481", "2012-08-14T08:03:00.000-0400");
    addIssueMock(issues2, "ORG-49", "2012-08-14T08:04:00.000-0400");
    addIssueMock(issues2, "ORG-154", "2012-08-14T08:05:00.000-0400");
    Date after3 = ISODateTimeFormat.dateTimeParser().parseDateTime("2012-08-14T08:05:00.000-0400").toDate();
    List<Map<String, Object>> issues3 = new ArrayList<Map<String, Object>>();
    addIssueMock(issues3, "ORG-4", "2012-08-14T08:06:00.000-0400");
    addIssueMock(issues3, "ORG-91", "2012-08-14T08:07:00.000-0400");
    when(jiraClientMock.getJIRAChangedIssues("ORG", 0, null, null)).thenReturn(
        new ChangedIssuesResults(issues, 0, 3, 8));
    when(jiraClientMock.getJIRAChangedIssues("ORG", 0, after2, null)).thenReturn(
        new ChangedIssuesResults(issues2, 0, 3, 5));
    when(jiraClientMock.getJIRAChangedIssues("ORG", 0, after3, null)).thenReturn(
        new ChangedIssuesResults(issues3, 0, 3, 2));
    Assert.assertEquals(8, tested.processUpdate());
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 0, null, null);
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 0, after2, null);
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 0, after3, null);
    // TODO UNITTEST assert results and persistence of latestUpdate field
  }

  @Test
  public void processUpdate_PagedByStartAt() throws Exception {

    // test case with more than one "page" of results with same updated dates so pagination in JIRA is used
    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    JIRAProjectIndexer tested = new JIRAProjectIndexer("ORG", jiraClientMock, null);

    List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();
    addIssueMock(issues, "ORG-45", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues, "ORG-46", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues, "ORG-47", "2012-08-14T08:00:00.000-0400");
    List<Map<String, Object>> issues2 = new ArrayList<Map<String, Object>>();
    addIssueMock(issues2, "ORG-481", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues2, "ORG-49", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues2, "ORG-154", "2012-08-14T08:00:00.000-0400");
    List<Map<String, Object>> issues3 = new ArrayList<Map<String, Object>>();
    addIssueMock(issues3, "ORG-4", "2012-08-14T08:00:00.000-0400");
    addIssueMock(issues3, "ORG-91", "2012-08-14T08:00:00.000-0400");
    when(jiraClientMock.getJIRAChangedIssues("ORG", 0, null, null)).thenReturn(
        new ChangedIssuesResults(issues, 0, 3, 8));
    when(jiraClientMock.getJIRAChangedIssues("ORG", 3, null, null)).thenReturn(
        new ChangedIssuesResults(issues2, 3, 3, 8));
    when(jiraClientMock.getJIRAChangedIssues("ORG", 6, null, null)).thenReturn(
        new ChangedIssuesResults(issues3, 6, 3, 8));
    Assert.assertEquals(8, tested.processUpdate());
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 0, null, null);
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 3, null, null);
    verify(jiraClientMock, times(1)).getJIRAChangedIssues("ORG", 6, null, null);
    // TODO UNITTEST assert results and persistence of latestUpdate field

  }

  /**
   * Add issue info structure into list of issues. Used to build mock {@link ChangedIssuesResults} instances.
   * 
   * @param issues list of issues to add issue into
   * @param key of issue
   * @param updated field of JIRA issue with format: 2009-03-23T08:38:52.000-0400
   */
  protected void addIssueMock(List<Map<String, Object>> issues, String key, String updated) {
    Map<String, Object> issue = new HashMap<String, Object>();
    issues.add(issue);
    issue.put("key", key);
    Map<String, Object> fields = new HashMap<String, Object>();
    issue.put("fields", fields);
    fields.put("updated", updated);
  }

}
