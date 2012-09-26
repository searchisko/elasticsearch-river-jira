/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullreindex;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.Client;

/**
 * Full reindex action implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullReindexAction extends Action<FullReindexRequest, FullReindexResponse, FullReindexRequestBuilder> {

  public static final FullReindexAction INSTANCE = new FullReindexAction();
  public static final String NAME = "jira_river/force_full_update";

  protected FullReindexAction() {
    super(NAME);
  }

  @Override
  public FullReindexRequestBuilder newRequestBuilder(Client client) {
    return new FullReindexRequestBuilder(client);
  }

  @Override
  public FullReindexResponse newResponse() {
    return new FullReindexResponse();
  }

}
