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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JIRA5RestIssueIndexStructureBuilder}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestIssueIndexStructureBuilderTest {

  @Test
  public void configuration_read_ok() {

    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name",
        "type_name", loadTestSettings("/index_structure_configuration_test_ok.json"));
    Assert.assertEquals("river_name", tested.riverName);
    Assert.assertEquals("index_name", tested.indexName);
    Assert.assertEquals("type_name", tested.typeName);
    Assert.assertEquals("river_name", tested.indexFieldForRiverName);
    Assert.assertEquals("jira_project_key", tested.indexFieldForProjectKey);

    Assert.assertEquals(6, tested.fieldsConfig.size());
    assertFieldConfiguration(tested.fieldsConfig, "issue_key", "key", null);
    assertFieldConfiguration(tested.fieldsConfig, "link", "self", null);
    assertFieldConfiguration(tested.fieldsConfig, "reporter", "fields.reporter", "user2");
    assertFieldConfiguration(tested.fieldsConfig, "assignee", "fields.assignee", "user2");
    assertFieldConfiguration(tested.fieldsConfig, "fix_versions", "fields.fixVersions", "name2");
    assertFieldConfiguration(tested.fieldsConfig, "components", "fields.components", "name2");

    Assert.assertEquals(2, tested.filtersConfig.size());
    Assert.assertTrue(tested.filtersConfig.containsKey("user2"));
    Assert.assertTrue(tested.filtersConfig.containsKey("name2"));

    Map<String, String> userFilter = tested.filtersConfig.get("user2");
    Assert.assertEquals(2, userFilter.size());
    Assert.assertEquals("username2", userFilter.get("name"));
    Assert.assertEquals("display_name2", userFilter.get("displayName"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadTestSettings(String file) {
    return (Map<String, Object>) Utils.loadJSONFromJarPackagedFile(file).get("index");
  }

  @Test
  public void configuration_read_validation() {

    try {
      new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
          loadTestSettings("/index_structure_configuration_test_err_nojirafield.json"));
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("'jira_field' is not defined in 'index/fields/reporter'", e.getMessage());
    }

    try {
      new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
          loadTestSettings("/index_structure_configuration_test_err_emptyjirafield.json"));
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("'jira_field' is not defined in 'index/fields/link'", e.getMessage());
    }

    try {
      new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
          loadTestSettings("/index_structure_configuration_test_err_unknownvaluefilter.json"));
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals(
          "Filter definition not found for filter name 'name3' defined in 'index/fields/fix_versions/value_filter'",
          e.getMessage());
    }

    // TODO UNITTEST
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void configuration_defaultLoading() {

    assertDefaultConfigurationLoaded(new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
        null));

    Map<String, Object> settings = new HashMap<String, Object>();
    assertDefaultConfigurationLoaded(new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
        settings));

    settings.put(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDS, new HashMap());
    settings.put(JIRA5RestIssueIndexStructureBuilder.CONFIG_FILTERS, new HashMap());
    settings.put(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDRIVERNAME, " ");
    assertDefaultConfigurationLoaded(new JIRA5RestIssueIndexStructureBuilder("river_name", "index_name", "type_name",
        settings));

  }

  private void assertDefaultConfigurationLoaded(JIRA5RestIssueIndexStructureBuilder tested) {
    Assert.assertEquals("river_name", tested.riverName);
    Assert.assertEquals("index_name", tested.indexName);
    Assert.assertEquals("type_name", tested.typeName);
    Assert.assertEquals("source", tested.indexFieldForRiverName);
    Assert.assertEquals("project_key", tested.indexFieldForProjectKey);

    Assert.assertEquals(15, tested.fieldsConfig.size());
    assertFieldConfiguration(tested.fieldsConfig, "project_name", "fields.project.name", null);
    assertFieldConfiguration(tested.fieldsConfig, "assignee", "fields.assignee", "user");
    assertFieldConfiguration(tested.fieldsConfig, "fix_versions", "fields.fixVersions", "name");

    Assert.assertEquals(2, tested.filtersConfig.size());
    Assert.assertTrue(tested.filtersConfig.containsKey("user"));
    Assert.assertTrue(tested.filtersConfig.containsKey("name"));

    Map<String, String> userFilter = tested.filtersConfig.get("user");
    Assert.assertEquals(3, userFilter.size());
    Assert.assertEquals("username", userFilter.get("name"));
    Assert.assertEquals("email_address", userFilter.get("emailAddress"));
    Assert.assertEquals("display_name", userFilter.get("displayName"));
  }

  private void assertFieldConfiguration(Map<String, Map<String, String>> fieldsConfig, String indexFieldName,
      String jiraFieldName, String filter) {
    Assert.assertTrue(fieldsConfig.containsKey(indexFieldName));
    Map<String, String> field = fieldsConfig.get(indexFieldName);
    Assert.assertEquals(jiraFieldName, field.get(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDS_JIRAFIELD));
    Assert.assertEquals(filter, field.get(JIRA5RestIssueIndexStructureBuilder.CONFIG_FIELDS_VALUEFILTER));
  }

  @Test
  public void getRequiredJIRACallIssueFields() {
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null, null);

    // case - mandatory fields in set
    List<String> fp = Utils.parseCsvString(tested.getRequiredJIRACallIssueFields());
    Assert.assertTrue(fp.contains("updated"));
    Assert.assertTrue(fp.contains("project"));

    // assert other fields from default configuration
    Assert.assertTrue(fp.contains("issuetype"));
    Assert.assertTrue(fp.contains("summary"));
    Assert.assertTrue(fp.contains("status"));
    Assert.assertTrue(fp.contains("created"));
    Assert.assertTrue(fp.contains("updated"));
    Assert.assertTrue(fp.contains("resolutiondate"));
    Assert.assertTrue(fp.contains("description"));
    Assert.assertTrue(fp.contains("labels"));
    Assert.assertTrue(fp.contains("assignee"));
    Assert.assertTrue(fp.contains("reporter"));
    Assert.assertTrue(fp.contains("components"));
    Assert.assertTrue(fp.contains("fixVersions"));

  }

  @Test
  public void toJson() {
    // TODO UNITTEST
  }

  @Test
  public void addValueToTheIndex() throws Exception {
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null, null);

    XContentGenerator xContentGeneratorMock = mock(XContentGenerator.class);
    XContentBuilder out = XContentBuilder.builder(preparexContentMock(xContentGeneratorMock));

    // case - no exception if values parameter is null
    tested.addValueToTheIndex(out, "testfield", "testpath", null, null);
    verify(xContentGeneratorMock, times(0)).writeFieldName(Mockito.anyString());

    // case - no exception if value is not found
    reset(xContentGeneratorMock);
    Map<String, Object> values = new HashMap<String, Object>();
    tested.addValueToTheIndex(out, "testfield", "testpath", values, null);
    verify(xContentGeneratorMock, times(0)).writeFieldName(Mockito.anyString());

    // case - get correctly value from first level of nesting, no filtering on null filter
    reset(xContentGeneratorMock);
    values.put("myKey", "myValue");
    values.put("myKey2", "myValue2");
    tested.addValueToTheIndex(out, "testfield", "myKey2", values, null);
    verify(xContentGeneratorMock, times(1)).writeFieldName(Mockito.anyString());
    verify(xContentGeneratorMock).writeFieldName("testfield");
    verify(xContentGeneratorMock).writeString("myValue2");
    Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

    // case - get correctly value from deeper level of nesting, no filtering with empty filter
    reset(xContentGeneratorMock);
    values.put("myKey", "myValue");
    values.put("myKey2", "myValue2");
    Map<String, Object> parent3 = new HashMap<String, Object>();
    values.put("parent3", parent3);
    parent3.put("myKey3", "myValue3");
    Map<String, String> filter = new HashMap<String, String>();

    tested.addValueToTheIndex(out, "testfield3", "parent3.myKey3", values, filter);
    verify(xContentGeneratorMock, times(1)).writeFieldName(Mockito.anyString());
    verify(xContentGeneratorMock).writeFieldName("testfield3");
    verify(xContentGeneratorMock).writeString("myValue3");
    Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

    // case - no error when filter on filtering unsupported value
    reset(xContentGeneratorMock);
    values.clear();
    values.put("myKey", "myValue");
    values.put("myKey2", "myValue2");
    filter.put("myKeyFilter", "myKeyFilter");
    tested.addValueToTheIndex(out, "testfield", "myKey2", values, filter);
    verify(xContentGeneratorMock).writeFieldName("testfield");
    verify(xContentGeneratorMock).writeString("myValue2");
    Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

    // case - get correctly value from first level of nesting, filtering on Map
    reset(xContentGeneratorMock);
    values.clear();
    values.put("myKey", "myValue");
    values.put("myKey2", "myValue2");
    parent3 = new HashMap<String, Object>();
    values.put("parent3", parent3);
    parent3.put("myKey3", "myValue3");
    parent3.put("myKey4", "myValue4");

    filter.clear();
    filter.put("myKey3", "myKey1");

    tested.addValueToTheIndex(out, "testfield", "parent3", values, filter);

    verify(xContentGeneratorMock).writeFieldName("testfield");
    verify(xContentGeneratorMock).writeStartObject();
    verify(xContentGeneratorMock).writeFieldName("myKey1");
    verify(xContentGeneratorMock).writeString("myValue3");
    verify(xContentGeneratorMock).writeEndObject();

    Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

    // case - filtering on List of Maps
    reset(xContentGeneratorMock);
    values.clear();
    values.put("myKey", "myValue");
    values.put("myKey2", "myValue2");

    List<Object> parent3list = new ArrayList<Object>();
    values.put("parent3", parent3list);

    Map<String, Object> obj31 = new HashMap<String, Object>();
    parent3list.add(obj31);
    obj31.put("myKey3", "myValue31");
    obj31.put("myKey4", "myValue41");

    Map<String, Object> obj32 = new HashMap<String, Object>();
    parent3list.add(obj32);
    obj32.put("myKey3", "myValue32");
    obj32.put("myKey4", "myValue42");

    filter.clear();
    filter.put("myKey3", "myKey3");

    tested.addValueToTheIndex(out, "testfield", "parent3", values, filter);

    verify(xContentGeneratorMock).writeFieldName("testfield");
    verify(xContentGeneratorMock, times(1)).writeStartArray();
    verify(xContentGeneratorMock, times(2)).writeStartObject();
    verify(xContentGeneratorMock, times(2)).writeFieldName("myKey3");
    verify(xContentGeneratorMock).writeString("myValue31");
    verify(xContentGeneratorMock).writeString("myValue32");
    verify(xContentGeneratorMock, times(2)).writeEndObject();
    verify(xContentGeneratorMock, times(1)).writeEndArray();

    Mockito.verifyNoMoreInteractions(xContentGeneratorMock);

  }

  @Test
  public void addValueToTheIndexField() throws Exception {
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null, null);

    XContentGenerator xContentGeneratorMock = mock(XContentGenerator.class);
    XContentBuilder out = XContentBuilder.builder(preparexContentMock(xContentGeneratorMock));

    // case - string field
    tested.addValueToTheIndexField(out, "test", "testvalue");
    verify(xContentGeneratorMock).writeFieldName("test");
    verify(xContentGeneratorMock).writeString("testvalue");

    // case - integer field
    reset(xContentGeneratorMock);
    tested.addValueToTheIndexField(out, "testint", new Integer(10));
    verify(xContentGeneratorMock).writeFieldName("testint");
    verify(xContentGeneratorMock).writeNumber(10);

    // case - nothing added if value is null
    reset(xContentGeneratorMock);
    tested.addValueToTheIndexField(out, "testnull", null);
    Mockito.verifyZeroInteractions(xContentGeneratorMock);
  }

  @Test
  public void getJiraCallFieldName() {
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(null));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("  "));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("self"));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("key"));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields"));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields."));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.  "));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.."));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields..."));
    Assert.assertNull(JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields..something"));

    Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.summary"));
    Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.summary."));
    Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName("fields.summary.name"));
    Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(" fields.summary "));
    Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(" fields.summary. "));
    Assert.assertEquals("summary", JIRA5RestIssueIndexStructureBuilder.getJiraCallFieldName(" fields.summary.name "));
  }

  /**
   * Prepare {@link XContent} mock to be used for {@link XContentBuilder} test instance creation.
   * 
   * @param xContentGeneratorMock to be returned from XContent mock
   * @return XContent mock instance
   * @throws IOException
   */
  protected XContent preparexContentMock(XContentGenerator xContentGeneratorMock) throws IOException {
    XContent xContentMock = mock(XContent.class);
    when(xContentMock.createGenerator(Mockito.any(OutputStream.class))).thenReturn(xContentGeneratorMock);
    return xContentMock;
  }
}
