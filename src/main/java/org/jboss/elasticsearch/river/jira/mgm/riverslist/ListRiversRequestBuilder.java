/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.BaseRequestBuilder;
import org.elasticsearch.client.Client;

/**
 * Request builder to get lit of all jira rivers in cluster.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ListRiversRequestBuilder extends BaseRequestBuilder<ListRiversRequest, ListRiversResponse> {

  public ListRiversRequestBuilder(Client client) {
    super(client, new ListRiversRequest());
  }

  @Override
  protected void doExecute(ActionListener<ListRiversResponse> listener) {
    client.execute(ListRiversAction.INSTANCE, request, listener);
  }

}
