/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.testtools;

import java.util.Map;

import org.elasticsearch.common.settings.SettingsException;
import org.jboss.elasticsearch.tools.content.StructuredContentPreprocessorBase;

/**
 * Implementation of IssueDataPreprocessorBase for configuration loading tests.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IssueDataPreprocessorMock extends StructuredContentPreprocessorBase {

  public Map<String, Object> settings = null;

  @Override
  public void init(Map<String, Object> settings) throws SettingsException {
    this.settings = settings;
  }

  @Override
  public Map<String, Object> preprocessData(Map<String, Object> issueData) {
    return issueData;
  }

}
