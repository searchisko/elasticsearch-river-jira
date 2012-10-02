/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.Client;

/**
 * JIRA River get state info action implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateAction extends Action<JRStateRequest, JRStateResponse, JRStateRequestBuilder> {

  public static final JRStateAction INSTANCE = new JRStateAction();
  public static final String NAME = "jira_river/state";

  protected JRStateAction() {
    super(NAME);
  }

  @Override
  public JRStateRequestBuilder newRequestBuilder(Client client) {
    return new JRStateRequestBuilder(client);
  }

  @Override
  public JRStateResponse newResponse() {
    return new JRStateResponse();
  }

}
