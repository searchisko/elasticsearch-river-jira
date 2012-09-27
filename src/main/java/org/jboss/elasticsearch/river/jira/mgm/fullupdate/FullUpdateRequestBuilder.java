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
 * Request builder for full reindex.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateRequestBuilder extends BaseRequestBuilder<FullUpdateRequest, FullUpdateResponse> {

  protected FullUpdateRequestBuilder(Client client) {
    super(client, new FullUpdateRequest());
  }

  @Override
  protected void doExecute(ActionListener<FullUpdateResponse> listener) {
    client.execute(FullUpdateAction.INSTANCE, request, listener);
  }

}
