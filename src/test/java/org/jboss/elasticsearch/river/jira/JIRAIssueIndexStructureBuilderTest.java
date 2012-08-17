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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class JIRAIssueIndexStructureBuilderTest {

  @Test
  public void getRequiredJIRAIssueFields() {
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null);

    // case - mandatory fields in set
    List<String> fp = Utils.parseCsvString(tested.getRequiredJIRAIssueFields());
    Assert.assertTrue(fp.contains("key"));
    Assert.assertTrue(fp.contains("updated"));
    Assert.assertEquals(JIRA5RestIssueIndexStructureBuilder.DEFAULT_JIRA_FIELD_SET.size() + 2, fp.size());
    Assert.assertTrue(fp.containsAll(JIRA5RestIssueIndexStructureBuilder.DEFAULT_JIRA_FIELD_SET));

  }

  @Test
  public void addValueToTheIndex() throws Exception {
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null);

    XContentGenerator xContentGeneratorMock = mock(XContentGenerator.class);
    XContentBuilder out = XContentBuilder.builder(preparexContentMock(xContentGeneratorMock));

    // case - no exception if values parameter is null
    tested.addValueToTheIndex(out, "testfield", "testpath", null);
    verify(xContentGeneratorMock, times(0)).writeFieldName(Mockito.anyString());

    // case - no exception if value is not found
    reset(xContentGeneratorMock);
    Map<String, Object> values = new HashMap<String, Object>();
    tested.addValueToTheIndex(out, "testfield", "testpath", values);
    verify(xContentGeneratorMock, times(0)).writeFieldName(Mockito.anyString());

    // case - get correctly value from first level of nesting, no filtering on null filter
    reset(xContentGeneratorMock);
    values.put("myKey", "myValue");
    values.put("myKey2", "myValue2");
    tested.addValueToTheIndex(out, "testfield", "myKey2", values);
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
    Set<String> filter = new HashSet<String>();

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
    filter.add("myKeyFilter");
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
    filter.add("myKey3");

    tested.addValueToTheIndex(out, "testfield", "parent3", values, filter);

    verify(xContentGeneratorMock).writeFieldName("testfield");
    verify(xContentGeneratorMock).writeStartObject();
    verify(xContentGeneratorMock).writeFieldName("myKey3");
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
    filter.add("myKey3");

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
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null);

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
