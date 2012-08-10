/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.httpclient.NameValuePair;
import org.elasticsearch.common.settings.SettingsException;
import org.junit.Test;

/**
 * Unit test for {@link JIRA5RestClient}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestClientTest {

  /**
   * URL used for JIRA5RestClient constructor in unit tests.
   */
  protected static final String TEST_JIRA_URL = "https://issues.jboss.org";

  /**
   * Date formatter used to prepare {@link Date} instances for tests
   */
  protected SimpleDateFormat JQL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  /**
   * Main method used to run integration tests with real JIRA call.
   * 
   * @param args not used
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

    JIRA5RestClient tested = new JIRA5RestClient("https://issues.jboss.org", null, null);

    // List<String> projects = tested.getAllJIRAProjects();
    // System.out.println(projects);

    ChangedIssuesResults ret = tested.getJIRAChangedIssues("ORG", 0, null, null);
    System.out.println(ret);
  }

  @Test
  public void constructor() {
    try {
      new JIRA5RestClient(null, null, null);
      Assert.fail("SettingsException not thrown");
    } catch (SettingsException e) {
      // OK
    }
    try {
      new JIRA5RestClient("  ", null, null);
      Assert.fail("SettingsException not thrown");
    } catch (SettingsException e) {
      // OK
    }
    try {
      new JIRA5RestClient("nonsenseUrl", null, null);
      Assert.fail("SettingsException not thrown");
    } catch (SettingsException e) {
      // OK
    }

    JIRA5RestClient tested = new JIRA5RestClient("http://issues.jboss.org", null, null);
    Assert.assertEquals(JIRA5RestClient.prepareAPIURLFromBaseURL("http://issues.jboss.org"), tested.jiraRestAPIUrlBase);
    tested = new JIRA5RestClient(TEST_JIRA_URL, null, null);
    Assert.assertEquals(JIRA5RestClient.prepareAPIURLFromBaseURL(TEST_JIRA_URL), tested.jiraRestAPIUrlBase);
    Assert.assertFalse(tested.isAuthConfigured);

    tested = new JIRA5RestClient(TEST_JIRA_URL, "", "pwd");
    Assert.assertFalse(tested.isAuthConfigured);

    tested = new JIRA5RestClient(TEST_JIRA_URL, "uname", "pwd");
    Assert.assertTrue(tested.isAuthConfigured);
  }

  @Test
  public void getAllJIRAProjects() throws Exception {

    JIRA5RestClient tested = new JIRA5RestClient(TEST_JIRA_URL, null, null) {
      @Override
      protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {
        Assert.assertEquals("project", restOperation);
        Assert.assertNull(params);
        return ("[{\"key\": \"ORG\", \"name\": \"ORG project\"},{\"key\": \"PPP\"}]").getBytes("UTF-8");
      };

    };

    List<String> ret = tested.getAllJIRAProjects();
    Assert.assertNotNull(ret);
    Assert.assertEquals(2, ret.size());
    Assert.assertTrue(ret.contains("ORG"));
    Assert.assertTrue(ret.contains("PPP"));
  }

  @Test
  public void getJIRAChangedIssues() throws Exception {
    final Date ua = new Date();
    final Date ub = new Date();

    JIRA5RestClient tested = new JIRA5RestClient(TEST_JIRA_URL, null, null) {
      @Override
      protected byte[] performJIRAChangedIssuesREST(String projectKey, int startAt, Date updatedAfter,
          Date updatedBefore) throws Exception {
        Assert.assertEquals("ORG", projectKey);
        Assert.assertEquals(ua, updatedAfter);
        Assert.assertEquals(ub, updatedBefore);
        Assert.assertEquals(10, startAt);
        return "{\"startAt\": 5, \"maxResults\" : 10, \"total\" : 50, \"issues\" : [{\"key\" : \"ORG-45\"}]}"
            .getBytes("UTF-8");
      };
    };

    ChangedIssuesResults ret = tested.getJIRAChangedIssues("ORG", 10, ua, ub);
    Assert.assertEquals(5, ret.getStartAt());
    Assert.assertEquals(10, ret.getMaxResults());
    Assert.assertEquals(50, ret.getTotal());
    Assert.assertNotNull(ret.getIssues());
    Assert.assertEquals(1, ret.getIssuesCount());
  }

  @Test
  public void performJIRAChangedIssuesREST() throws Exception {
    final Date ua = new Date();
    final Date ub = new Date();

    JIRA5RestClient tested = new JIRA5RestClient(TEST_JIRA_URL, null, null) {
      @Override
      protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {
        Assert.assertEquals("search", restOperation);
        Assert.assertNotNull(params);
        String mr = "-1";
        String fields = "";
        String startAt = "";
        for (NameValuePair param : params) {
          if (param.getName().equals("maxResults")) {
            mr = param.getValue();
          } else if (param.getName().equals("jql")) {
            Assert.assertEquals("JQL string", param.getValue());
          } else if (param.getName().equals("fields")) {
            fields = param.getValue();
          } else if (param.getName().equals("startAt")) {
            startAt = param.getValue();
          }
        }

        if ("-1".equals(mr)) {
          Assert.assertEquals(3, params.size());
        } else {
          Assert.assertEquals(4, params.size());
        }

        return ("{\"maxResults\": " + mr + ", \"startAt\": " + startAt + ", \"fields\" : \"" + fields + "\" }")
            .getBytes("UTF-8");
      };

      @Override
      protected String prepareJIRAChangedIssuesJQL(String projectKey, Date updatedAfter, Date updatedBefore) {
        Assert.assertEquals("ORG", projectKey);
        Assert.assertEquals(ua, updatedAfter);
        Assert.assertEquals(ub, updatedBefore);
        return "JQL string";
      }
    };

    byte[] ret = tested.performJIRAChangedIssuesREST("ORG", 10, ua, ub);
    Assert
        .assertEquals(
            "{\"maxResults\": -1, \"startAt\": 10, \"fields\" : \"key,created,updated,reporter,assignee,summary,description\" }",
            new String(ret, "UTF-8"));

    tested.listJIRAIssuesMax = 10;
    ret = tested.performJIRAChangedIssuesREST("ORG", 20, ua, ub);
    Assert
        .assertEquals(
            "{\"maxResults\": 10, \"startAt\": 20, \"fields\" : \"key,created,updated,reporter,assignee,summary,description\" }",
            new String(ret, "UTF-8"));

  }

  @Test
  public void prepareAPIURLFromBaseURL() {
    Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL(null));
    Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL(""));
    Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL("  "));
    Assert.assertEquals("http://issues.jboss.org/rest/api/2/",
        JIRA5RestClient.prepareAPIURLFromBaseURL("http://issues.jboss.org"));
    Assert.assertEquals("https://issues.jboss.org/rest/api/2/",
        JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org/"));
  }

  @Test
  public void formatJQLDate() throws Exception {
    JIRA5RestClient tested = new JIRA5RestClient(TEST_JIRA_URL, null, null);
    Assert.assertNull(tested.formatJQLDate(null));
    Assert.assertEquals("2012-08-10 10:52", tested.formatJQLDate(JQL_DATE_FORMAT.parse("2012-08-10 10:52")));
    Assert.assertEquals("2012-08-10 22:52", tested.formatJQLDate(JQL_DATE_FORMAT.parse("2012-08-10 22:52")));
  }

  @Test
  public void prepareJIRAChangedIssuesJQL() throws Exception {
    JIRA5RestClient tested = new JIRA5RestClient(TEST_JIRA_URL, null, null);
    try {
      tested.prepareJIRAChangedIssuesJQL(null, null, null);
      Assert.fail("IllegalArgumentException not thrown if project key is missing");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      tested.prepareJIRAChangedIssuesJQL("  ", null, null);
      Assert.fail("IllegalArgumentException not thrown if project key is missing");
    } catch (IllegalArgumentException e) {
      // OK
    }
    Assert.assertEquals("project='ORG' ORDER BY updated ASC", tested.prepareJIRAChangedIssuesJQL("ORG", null, null));
    Assert.assertEquals("project='ORG' and updatedDate >= \"2012-08-10 22:52\" ORDER BY updated ASC",
        tested.prepareJIRAChangedIssuesJQL("ORG", JQL_DATE_FORMAT.parse("2012-08-10 22:52"), null));
    Assert.assertEquals("project='ORG' and updatedDate <= \"2012-08-10 22:55\" ORDER BY updated ASC",
        tested.prepareJIRAChangedIssuesJQL("ORG", null, JQL_DATE_FORMAT.parse("2012-08-10 22:55")));
    Assert
        .assertEquals(
            "project='ORG' and updatedDate >= \"2012-08-10 22:52\" and updatedDate <= \"2012-08-10 22:55\" ORDER BY updated ASC",
            tested.prepareJIRAChangedIssuesJQL("ORG", JQL_DATE_FORMAT.parse("2012-08-10 22:52"),
                JQL_DATE_FORMAT.parse("2012-08-10 22:55")));

  }

}
