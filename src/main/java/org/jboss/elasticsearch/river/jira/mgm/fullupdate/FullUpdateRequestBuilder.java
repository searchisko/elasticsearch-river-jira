/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.BaseRequestBuilder;
import org.elasticsearch.client.Client;

/**
 * Request builder to force full index update for some jira river and some or all projects in it.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateRequestBuilder extends BaseRequestBuilder<FullUpdateRequest, FullUpdateResponse> {

  public FullUpdateRequestBuilder(Client client) {
    super(client, new FullUpdateRequest());
  }

  /**
   * Set name of river to force full index update for.
   * 
   * @param riverName name of river to force full index update for
   * @return builder for chaining
   */
  public FullUpdateRequestBuilder setRiverName(String riverName) {
    this.request.setRiverName(riverName);
    return this;
  }

  /**
   * Set JIRA project key to force full index update for. If not specified then full update is forced for all projects
   * managed by given jira river.
   * 
   * @param projectKey to force full index update for
   * @return builder for chaining
   */
  public FullUpdateRequestBuilder setProjectKey(String projectKey) {
    this.request.setProjectKey(projectKey);
    return this;
  }

  @Override
  protected void doExecute(ActionListener<FullUpdateResponse> listener) {
    if (request.getRiverName() == null)
      throw new IllegalArgumentException("riverName must be provided for request");
    client.execute(FullUpdateAction.INSTANCE, request, listener);
  }

}
