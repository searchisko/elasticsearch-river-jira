/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.river.RiverIndexName;

/**
 * Base for REST action handlers for JiraRiver management operations.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class RestJRMgmBaseAction extends BaseRestHandler {

  protected RestJRMgmBaseAction(Settings settings, Client client) {
    super(settings, client);
  }

  /**
   * Prepare base REST URL for JIRA river management operations. <code>riverName</code> request parameter is defined
   * here.
   * 
   * @return base REST management url ending by <code>/</code>
   */
  protected String baseRestMgmUrl() {
    return "/" + RiverIndexName.Conf.indexName(settings) + "/{riverName}/_mgm/";
  }

}
