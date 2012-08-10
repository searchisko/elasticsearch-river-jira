/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

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
    List<Map<String, Object>> ret = tested.getJIRAChangedIssues("ORG", null, null);
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
