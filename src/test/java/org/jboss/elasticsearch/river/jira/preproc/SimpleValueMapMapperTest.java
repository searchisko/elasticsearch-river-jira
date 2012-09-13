/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.preproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.jboss.elasticsearch.river.jira.Utils;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link SimpleValueMapMapper}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class SimpleValueMapMapperTest {

  @Test
  public void init_settingerrors() {
    SimpleValueMapMapper tested = new SimpleValueMapMapper();
    Client client = Mockito.mock(Client.class);
    Map<String, Object> settings = null;

    // case - settings mandatory
    try {
      tested.init("Test mapper", client, settings);
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      Assert.assertEquals("'settings' section is not defined for preprocessor Test mapper", e.getMessage());
    }

    // case - source_field mandatory
    settings = new HashMap<String, Object>();
    try {
      tested.init("Test mapper", client, settings);
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      Assert
          .assertEquals("Missing or empty 'settings/source_field' configuration value for 'Test mapper' preprocessor",
              e.getMessage());
    }

    // case - target_field mandatory
    settings.put(SimpleValueMapMapper.CFG_SOURCE_FIELD, "source");
    try {
      tested.init("Test mapper", client, settings);
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      Assert
          .assertEquals("Missing or empty 'settings/target_field' configuration value for 'Test mapper' preprocessor",
              e.getMessage());
    }

    // case - no more mandatory setting fields
    settings.put(SimpleValueMapMapper.CFG_TARGET_FIELD, "target");
    tested.init("Test mapper", client, settings);
  }

  @Test
  public void init() {
    SimpleValueMapMapper tested = new SimpleValueMapMapper();
    Client client = Mockito.mock(Client.class);

    // case - mandatory fields only
    Map<String, Object> settings = new HashMap<String, Object>();
    settings.put(SimpleValueMapMapper.CFG_SOURCE_FIELD, "source");
    settings.put(SimpleValueMapMapper.CFG_TARGET_FIELD, "target");

    tested.init("Test mapper", client, settings);
    Assert.assertEquals("Test mapper", tested.getName());
    Assert.assertEquals(client, tested.client);
    Assert.assertEquals("source", tested.fieldSource);
    Assert.assertEquals("target", tested.fieldTarget);
    Assert.assertNull(tested.defaultValue);
    Assert.assertFalse(tested.defaultValueOriginal);
    Assert.assertNull(tested.valueMap);

    // case - default value original
    settings.put(SimpleValueMapMapper.CFG_VALUE_DEFAULT, SimpleValueMapMapper.DEFAULT_VALUE_ORIGINAL);
    tested.init("Test mapper", client, settings);
    Assert.assertEquals("Test mapper", tested.getName());
    Assert.assertEquals(client, tested.client);
    Assert.assertEquals("source", tested.fieldSource);
    Assert.assertEquals("target", tested.fieldTarget);
    Assert.assertEquals(SimpleValueMapMapper.DEFAULT_VALUE_ORIGINAL, tested.defaultValue);
    Assert.assertTrue(tested.defaultValueOriginal);
    Assert.assertNull(tested.valueMap);

    // case - some default value and mapping here
    settings.put(SimpleValueMapMapper.CFG_VALUE_DEFAULT, "Default");
    Map<String, String> mapping = new HashMap<String, String>();
    mapping.put("orig", "new");
    settings.put(SimpleValueMapMapper.CFG_VALUE_MAPPING, mapping);
    tested.init("Test mapper", client, settings);
    Assert.assertEquals("Test mapper", tested.getName());
    Assert.assertEquals(client, tested.client);
    Assert.assertEquals("source", tested.fieldSource);
    Assert.assertEquals("target", tested.fieldTarget);
    Assert.assertEquals("Default", tested.defaultValue);
    Assert.assertFalse(tested.defaultValueOriginal);
    Assert.assertNotNull(tested.valueMap);
    Assert.assertEquals(1, tested.valueMap.size());

  }

  @SuppressWarnings("unchecked")
  @Test
  public void preprocessData() {
    Client client = Mockito.mock(Client.class);

    SimpleValueMapMapper tested = new SimpleValueMapMapper();
    tested.init("Test mapper", client,
        Utils.loadJSONFromJarPackagedFile("/preproc/SimpleValueMapMapper_preprocessData.json"));

    // case - not NPE
    tested.preprocessData(null);

    // case - use default if input data are null
    {
      Map<String, Object> values = new HashMap<String, Object>();
      tested.preprocessData(values);
      Assert.assertEquals("default", values.get("target"));
    }

    // case - use default if input data not in map
    {
      Map<String, Object> values = new HashMap<String, Object>();
      values.put("source", "");
      tested.preprocessData(values);
      Assert.assertEquals("default", values.get("target"));
      values.clear();
      values.put("source", "unknown");
      tested.preprocessData(values);
      Assert.assertEquals("default", values.get("target"));
    }

    // case - correct mapping if input data are in map, dot notation for source field
    tested.fieldSource = "source.level1";
    {
      Map<String, Object> values = new HashMap<String, Object>();
      Map<String, Object> source = new HashMap<String, Object>();
      source.put("level1", "orig1");
      values.put("source", source);
      tested.preprocessData(values);
      Assert.assertEquals("new1", values.get("target"));

      // case value is replaced by new one
      source.put("level1", "orig2");
      tested.preprocessData(values);
      Assert.assertEquals("new2", values.get("target"));
    }

    // case - default set to original marker
    tested.fieldSource = "source";
    tested.defaultValueOriginal = true;
    {
      Map<String, Object> values = new HashMap<String, Object>();
      values.put("source", "unknown");
      tested.preprocessData(values);
      Assert.assertEquals("unknown", values.get("target"));
    }

    // case - default not set so nothing in target field
    tested.defaultValueOriginal = false;
    tested.defaultValue = null;
    {
      Map<String, Object> values = new HashMap<String, Object>();
      values.put("source", "unknown");
      tested.preprocessData(values);
      Assert.assertNull(values.get("target"));
    }

    // case - bad value type in source field, so nothing in target, and WARN in log (not asserted)
    tested.defaultValue = "default";
    {
      Map<String, Object> values = new HashMap<String, Object>();
      values.put("source", new HashMap<String, Object>());
      tested.preprocessData(values);
      Assert.assertNull(values.get("target"));

      values.clear();
      values.put("source", new ArrayList<Object>());
      tested.preprocessData(values);
      Assert.assertNull(values.get("target"));

      values.clear();
      values.put("source", new String[] {});
      tested.preprocessData(values);
      Assert.assertNull(values.get("target"));
    }

    // case - dot notation on target field, map exists on target first level
    tested.fieldTarget = "target.value";
    {
      Map<String, Object> values = new HashMap<String, Object>();
      Map<String, Object> target = new HashMap<String, Object>();
      values.put("target", target);
      values.put("source", "orig1");

      tested.preprocessData(values);
      Assert.assertNotNull(values.get("target"));
      Assert.assertEquals("new1", ((Map<String, String>) values.get("target")).get("value"));
    }

    // case - dot notation on target field, map do not exists on any target level
    tested.fieldTarget = "target.value.level2.level3";
    {
      Map<String, Object> values = new HashMap<String, Object>();
      values.put("source", "orig1");

      tested.preprocessData(values);
      Assert.assertNotNull(values.get("target"));
      Assert.assertEquals("new1", XContentMapValues.extractValue(tested.fieldTarget, values));
    }

  }

}
