/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.preproc;

import java.util.Map;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.SettingsException;
import org.jboss.elasticsearch.river.jira.Utils;

/**
 * Abstract base class for {@link IssueDataPreprocessor} implementations.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class IssueDataPreprocessorBase implements IssueDataPreprocessor {

  protected ESLogger logger = null;

  protected String name;
  protected Client client;

  @Override
  public void init(String name, Client client, Map<String, Object> settings) throws SettingsException {
    logger = Loggers.getLogger(getClass(), name);
    this.name = name;
    this.client = client;
    init(settings);
  }

  /**
   * Init your instance settings. You can use instance fields to access other services.
   * 
   * @param settings to init from
   * @throws SettingsException use this exception in case on bad configuration for your implementation
   */
  public abstract void init(Map<String, Object> settings) throws SettingsException;

  /**
   * Validate configuration string is not null or empty. Useful for your {@link #init(Map)} implementation.
   * 
   * @param value to check
   * @param configFieldName name of field in preprocessor settings structure. Used for error message.
   * @throws SettingsException thrown if value is null or empty
   */
  protected void validateConfigurationStringNotEmpty(String value, String configFieldName) throws SettingsException {
    if (Utils.isEmpty(value)) {
      throw new SettingsException("Missing or empty 'settings/" + configFieldName + "' configuration value for '"
          + name + "' preprocessor");
    }
  }

  @Override
  public String getName() {
    return name;
  }

}
