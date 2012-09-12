/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.testtools;

import java.util.Map;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.SettingsException;
import org.jboss.elasticsearch.river.jira.IJIRAIssueIndexStructureBuilder;
import org.jboss.elasticsearch.river.jira.preproc.IssueDataPreprocessorBase;

/**
 * Implementation of IssueDataPreprocessorBase for configuration loading tests.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IssueDataPreprocessorMock extends IssueDataPreprocessorBase {

  public Map<String, Object> settings = null;

  @Override
  public void init(String name, Client client, IJIRAIssueIndexStructureBuilder indexStructureBuilder,
      Map<String, Object> settings) throws SettingsException {
    super.init(name, client, indexStructureBuilder, settings);
    this.settings = settings;
  }

  @Override
  public Map<String, Object> preprocessData(String projectKey, Map<String, Object> issueData) {
    return issueData;
  }

}
