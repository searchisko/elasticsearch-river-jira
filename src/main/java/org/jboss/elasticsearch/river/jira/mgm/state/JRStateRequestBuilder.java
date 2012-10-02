/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.BaseRequestBuilder;
import org.elasticsearch.client.Client;

/**
 * Request builder to get state of some jira river.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateRequestBuilder extends BaseRequestBuilder<JRStateRequest, JRStateResponse> {

  public JRStateRequestBuilder(Client client) {
    super(client, new JRStateRequest());
  }

  /**
   * Set name of river to get state for.
   * 
   * @param riverName name of river to force full index update for
   * @return builder for chaining
   */
  public JRStateRequestBuilder setRiverName(String riverName) {
    this.request.setRiverName(riverName);
    return this;
  }

  @Override
  protected void doExecute(ActionListener<JRStateResponse> listener) {
    client.execute(JRStateAction.INSTANCE, request, listener);
  }

}
