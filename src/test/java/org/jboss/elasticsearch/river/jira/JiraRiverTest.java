/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.elasticsearch.common.settings.Settings;
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

  @Test
  public void getAllIndexedProjectsKeys_FromStaticConfig() throws Exception {
    Map<String, Object> settings = new HashMap<String, Object>();
    Map<String, Object> jiraSettings = new HashMap<String, Object>();
    settings.put("jira", jiraSettings);
    jiraSettings.put("urlBase", "https://issues.jboss.org");
    jiraSettings.put("projectKeysIndexed", "ORG, UUUU, PEM, SU07");

    Settings gs = mock(Settings.class);
    RiverSettings rs = new RiverSettings(gs, settings);
    JiraRiver tested = new JiraRiver(new RiverName("jira", "my_jira_river"), rs, null);
    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    tested.jiraClient = jiraClientMock;

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
    Map<String, Object> settings = new HashMap<String, Object>();
    Map<String, Object> jiraSettings = new HashMap<String, Object>();
    settings.put("jira", jiraSettings);
    jiraSettings.put("urlBase", "https://issues.jboss.org");
    jiraSettings.put("projectKeysExcluded", "");

    Settings gs = mock(Settings.class);
    RiverSettings rs = new RiverSettings(gs, settings);
    JiraRiver tested = new JiraRiver(new RiverName("jira", "my_jira_river"), rs, null);
    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    tested.jiraClient = jiraClientMock;
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
    Map<String, Object> settings = new HashMap<String, Object>();
    Map<String, Object> jiraSettings = new HashMap<String, Object>();
    settings.put("jira", jiraSettings);
    jiraSettings.put("urlBase", "https://issues.jboss.org");
    jiraSettings.put("projectKeysExcluded", "PEM,UUUU");

    Settings gs = mock(Settings.class);
    RiverSettings rs = new RiverSettings(gs, settings);
    JiraRiver tested = new JiraRiver(new RiverName("jira", "my_jira_river"), rs, null);
    IJIRAClient jiraClientMock = mock(IJIRAClient.class);
    tested.jiraClient = jiraClientMock;
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

}
