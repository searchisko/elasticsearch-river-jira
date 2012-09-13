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
 * Interface for components used to preprocess issue data loaded from JIRA before indexed document is created from them.
 * <p>
 * Preprocessors may be configured in <code>index</code> section of river configuration:
 * 
 * <pre>
 *     ...
 *     "index" : {
 *         ...
 *         "preprocessors" : [
 *             { 
 *                 "name"     : "Status Normalizer",
 *                 "class"    : "org.jboss.elasticsearch.river.jira.preproc.StatusNormalizer",
 *                 "settings" : {
 *                     "some_setting_1" : "value1",
 *                     "some_setting_2" : "value2"
 *                 } 
 *             },
 *             { 
 *                 "name"     : "Issue type Normalizer",
 *                 "class"    : "org.jboss.elasticsearch.river.jira.preproc.IssueTypeNormalizer",
 *                 "settings" : {
 *                     "some_setting_1" : "value1",
 *                     "some_setting_2" : "value2"
 *                 } 
 *             }
 *         ]
 *      }
 * ...
 * 
 * <pre>
 * Class defined in <code>class</code> element must implement this interface. Name of preprocessor from <code>name</code> element and configuration structure stored in <code>settings</code> element is then passed to the {@link #init(String, Client, IJIRAIssueIndexStructureBuilder, Map)} method.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IssueDataPreprocessor {

  /**
   * Initialize preprocessor after created.
   * 
   * @param name name of preprocessor
   * @param client ElasticSearch client which can be used in this preprocessor to access data in ES cluster.
   * @param settings structure obtained from river configuration
   * @throws SettingsException use this exception in case on bad configuration for your implementation
   */
  void init(String name, Client client, Map<String, Object> settings) throws SettingsException;

  /**
   * Get name of this preprocessor.
   * 
   * @return name of this preprocessor.
   */
  String getName();

  /**
   * Preprocess data.
   * 
   * @param data to be preprocessed - may be changed during call!
   * @return preprocessed data - typically same object ad <code>data</code> parameter, but with changed structure.
   */
  Map<String, Object> preprocessData(Map<String, Object> data);

}
