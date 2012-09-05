/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.common.settings.SettingsException;

/**
 * Mode of JIRA issue comments indexing. Used to configure {@link IJIRAIssueIndexStructureBuilder} implementations.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public enum IssueCommentIndexingMode {

  /**
   * Comments are not placed into search index.
   */
  NONE("none"),

  /**
   * Comments are placed into search index as array in same document where issue is stored.
   */
  EMBEDDED("embedded"),

  /**
   * Comments are placed into search index in separate document with parent-child relation to issue document.
   */
  CHILD("child"),

  /**
   * Comments are placed into search index in separate document with issue key only in one field (no any other link to
   * issue document).
   */
  STANDALONE("standalone");

  private String configValue;

  private IssueCommentIndexingMode(String configValue) {
    this.configValue = configValue;
  }

  /**
   * Get value used to represent this value in configuration.
   * 
   * @return configuration value
   */
  public String getConfigValue() {
    return configValue;
  }

  /**
   * Get enum value based on String value read from configuration file.
   * 
   * @param value to be parsed
   * @return Enum value, never null, default is used if value is null or empty.
   * @throws SettingsException for bad value
   */
  public static IssueCommentIndexingMode parseConfiguration(String value) throws SettingsException {
    if (Utils.isEmpty(value)) {
      return EMBEDDED;
    }

    if (NONE.getConfigValue().equalsIgnoreCase(value)) {
      return NONE;
    } else if (CHILD.getConfigValue().equalsIgnoreCase(value)) {
      return CHILD;
    } else if (STANDALONE.getConfigValue().equalsIgnoreCase(value)) {
      return STANDALONE;
    } else if (EMBEDDED.getConfigValue().equalsIgnoreCase(value)) {
      return EMBEDDED;
    } else {
      throw new SettingsException("unsupported value for issue comments indexing mode: " + value);
    }
  }

}
