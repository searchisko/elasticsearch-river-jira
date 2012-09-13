/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.preproc;

import java.util.Collection;
import java.util.Map;

import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.jboss.elasticsearch.river.jira.Utils;

/**
 * Issue data preprocessor which allows to map simple value from JIRA to other value over configured Map structure.
 * Optional default value can be used for values not found in map. Example of configuration for this preprocessor:
 * 
 * <pre>
 * { 
 *     "name"     : "Status Normalizer",
 *     "class"    : "org.jboss.elasticsearch.river.jira.preproc.SimpleValueMapMapper",
 *     "settings" : {
 *         "source_field"  : "fields.status.name",
 *         "target_field"  : "dcp_issue_status",
 *         "value_default" : "In Progress",
 *         "value_mapping" : {
 *             "Open"      : "Open",
 *             "Resolved"  : "Closed",
 *             "Closed"    : "Closed"                     
 *         }
 *     } 
 * }
 * </pre>
 * 
 * Options are:
 * <ul>
 * <li><code>source_field</code> - source field in JIRA issue data. Dot notation for nested values can be used here (see
 * {@link XContentMapValues#extractValue(String, Map)}).
 * <li><code>target_field</code> - target field in JIRA issue data to store mapped value into.
 * <li><code>value_default</code> - optional default value used if Map do not provide mapping. If not set then target
 * field is leaved empty for values not found in mapping. You can use <code>{original}</code> value here which means
 * that original value from JIRA may be placed to target field if not found in Map structure.
 * <li><code>value_mapping</code> - Map structure for value mapping. Key id value from source field, Value is value for
 * target field.
 * </ul>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class SimpleValueMapMapper extends IssueDataPreprocessorBase {

  protected static final String CFG_SOURCE_FIELD = "source_field";
  protected static final String CFG_TARGET_FIELD = "target_field";
  protected static final String CFG_VALUE_DEFAULT = "value_default";
  protected static final String CFG_VALUE_MAPPING = "value_mapping";

  /**
   * value for {@link #defaultValue} meaning than original value from JIRA can be used as default.
   */
  public static final String DEFAULT_VALUE_ORIGINAL = "{original}";

  String fieldSource;
  String fieldTarget;
  String defaultValue = null;
  boolean defaultValueOriginal = false;
  Map<String, String> valueMap = null;

  @SuppressWarnings("unchecked")
  @Override
  public void init(Map<String, Object> settings) throws SettingsException {
    if (settings == null) {
      throw new SettingsException("'settings' section is not defined for preprocessor " + name);
    }
    fieldSource = XContentMapValues.nodeStringValue(settings.get(CFG_SOURCE_FIELD), null);
    validateConfigurationStringNotEmpty(fieldSource, CFG_SOURCE_FIELD);
    fieldTarget = XContentMapValues.nodeStringValue(settings.get(CFG_TARGET_FIELD), null);
    validateConfigurationStringNotEmpty(fieldTarget, CFG_TARGET_FIELD);
    defaultValue = Utils.trimToNull(XContentMapValues.nodeStringValue(settings.get(CFG_VALUE_DEFAULT), null));
    defaultValueOriginal = DEFAULT_VALUE_ORIGINAL.equals(defaultValue);
    valueMap = (Map<String, String>) settings.get(CFG_VALUE_MAPPING);
    if (valueMap == null || valueMap.isEmpty()) {
      logger.warn("'settings/" + CFG_VALUE_MAPPING + "' is not defined for preprocessor '{}'", name);
    }
  }

  @Override
  public Map<String, Object> preprocessData(Map<String, Object> data) {
    if (data == null)
      return null;

    Object v = null;
    if (fieldSource.contains(".")) {
      v = XContentMapValues.extractValue(fieldSource, data);
    } else {
      v = data.get(fieldSource);
    }

    if (v == null) {
      putDefaultValue(data, null);
    } else if (v instanceof Map || v instanceof Collection || v.getClass().isArray()) {
      logger.warn("issue value for field '" + fieldSource
          + "' is not simple value (but is List or Array) to be processed by '" + name + "' preprocessor");
    } else {
      String origValue = v.toString();
      String newVal = null;
      if (valueMap != null && !Utils.isEmpty(origValue))
        newVal = valueMap.get(origValue);
      if (newVal != null) {
        putTargetValue(data, newVal);
      } else {
        putDefaultValue(data, origValue);
      }
    }
    return data;
  }

  private void putDefaultValue(Map<String, Object> issueData, String originalValue) {
    if (defaultValue != null && !defaultValueOriginal) {
      putTargetValue(issueData, defaultValue);
    } else if (defaultValueOriginal && originalValue != null) {
      putTargetValue(issueData, originalValue);
    }
  }

  protected void putTargetValue(Map<String, Object> issueData, String value) {
    Utils.putValueIntoMapOfMaps(issueData, fieldTarget, value);
  }
}
