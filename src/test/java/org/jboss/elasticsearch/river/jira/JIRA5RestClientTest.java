/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

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

  @Test
  public void constructor() {
    try {
      new JIRA5RestClient(null);
      Assert.fail("SettingsException not thrown");
    } catch (SettingsException e) {
      // OK
    }
    try {
      new JIRA5RestClient("  ");
      Assert.fail("SettingsException not thrown");
    } catch (SettingsException e) {
      // OK
    }
    try {
      new JIRA5RestClient("nonsenseUrl");
      Assert.fail("SettingsException not thrown");
    } catch (SettingsException e) {
      // OK
    }

    JIRA5RestClient tested = new JIRA5RestClient("http://issues.jboss.org");
    Assert.assertEquals(JIRA5RestClient.prepareAPIURLFromBaseURL("http://issues.jboss.org"), tested.jiraRestAPIUrlBase);
    tested = new JIRA5RestClient("https://issues.jboss.org");
    Assert
        .assertEquals(JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org"), tested.jiraRestAPIUrlBase);

  }

  @Test
  public void prepareAPIURLFromBaseURL() {
    Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL(null));
    Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL(""));
    Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL("  "));
    Assert.assertEquals("https://issues.jboss.org/rest/api/2/",
        JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org"));
    Assert.assertEquals("https://issues.jboss.org/rest/api/2/",
        JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org/"));
  }

  @Test
  public void getJIRAChangedIssues() throws Exception {
    JIRA5RestClient tested = new JIRA5RestClient("https://issues.jboss.org");
    List<Map<String, Object>> ret = tested.getJIRAChangedIssues("ORG");
    System.out.println(ret);
  }

}
