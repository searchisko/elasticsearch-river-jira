/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import static org.elasticsearch.rest.RestStatus.OK;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseActionListener;
import org.jboss.elasticsearch.river.jira.mgm.RestJRMgmBaseAction;

/**
 * REST action handler for Jira river get state operation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class RestJRLifecycleAction extends RestJRMgmBaseAction {

  @Inject
  protected RestJRLifecycleAction(Settings settings, Client client, RestController controller) {
    super(settings, client);
    String baseUrl = baseRestMgmUrl();
    controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.POST, baseUrl + "stop", this);
    controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.POST, baseUrl + "restart", this);
  }

  @Override
  public void handleRequest(final RestRequest restRequest, final RestChannel restChannel) {

    JRLifecycleCommand command = JRLifecycleCommand.RESTART;
    if (restRequest.path().endsWith("stop"))
      command = JRLifecycleCommand.STOP;

    JRLifecycleRequest actionRequest = new JRLifecycleRequest(restRequest.param("riverName"), command);

    client.execute(JRLifecycleAction.INSTANCE, actionRequest,
        new JRMgmBaseActionListener<JRLifecycleRequest, JRLifecycleResponse, NodeJRLifecycleResponse>(actionRequest,
            restRequest, restChannel) {

          @Override
          protected void handleJiraRiverResponse(NodeJRLifecycleResponse nodeInfo) throws Exception {
            restChannel.sendResponse(new XContentRestResponse(restRequest, OK, buildMessageDocument(restRequest,
                "Command successful")));
          }

        });
  }
}
