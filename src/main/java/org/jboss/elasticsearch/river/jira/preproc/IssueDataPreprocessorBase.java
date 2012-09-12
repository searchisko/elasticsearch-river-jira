/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.preproc;

import java.util.Map;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.SettingsException;
import org.jboss.elasticsearch.river.jira.IJIRAIssueIndexStructureBuilder;

/**
 * Abstract base class for {@link IssueDataPreprocessor} implementations.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class IssueDataPreprocessorBase implements IssueDataPreprocessor {

  protected String name;
  protected Client client;
  protected IJIRAIssueIndexStructureBuilder indexStructureBuilder;

  @Override
  public void init(String name, Client client, IJIRAIssueIndexStructureBuilder indexStructureBuilder,
      Map<String, Object> settings) throws SettingsException {
    this.name = name;
    this.client = client;
    this.indexStructureBuilder = indexStructureBuilder;
  }

  @Override
  public String getName() {
    return name;
  }

}
