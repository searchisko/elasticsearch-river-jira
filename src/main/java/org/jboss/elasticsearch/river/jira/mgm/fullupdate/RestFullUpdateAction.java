/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import static org.elasticsearch.rest.RestStatus.OK;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.river.RiverIndexName;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseActionListener;

/**
 * REST action handler for force full index update operation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class RestFullUpdateAction extends BaseRestHandler {

  @Inject
  protected RestFullUpdateAction(Settings settings, Client client, RestController controller) {
    super(settings, client);
    String baseUrl = baseRestMgmUrl();
    controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.POST, baseUrl + "fullupdate", this);
    controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.POST, baseUrl + "fullupdate/{projectKey}",
        this);
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

  @Override
  public void handleRequest(final RestRequest request, final RestChannel channel) {

    final String riverName = request.param("riverName");
    final String projectKey = request.param("projectKey");

    FullUpdateRequest req = new FullUpdateRequest(riverName, projectKey);

    client.execute(FullUpdateAction.INSTANCE, req,
        new JRMgmBaseActionListener<FullUpdateRequest, FullUpdateResponse, NodeFullUpdateResponse>(req, request,
            channel) {

          @Override
          protected void handleJiraRiverResponse(NodeFullUpdateResponse nodeInfo) throws Exception {
            if (actionRequest.isProjectKeyRequest() && !nodeInfo.projectFound) {
              restChannel.sendResponse(new XContentRestResponse(restRequest, RestStatus.NOT_FOUND, buildMessageDocument(
                  restRequest, "Project '" + projectKey + "' is not indexed by JiraRiver with name: " + riverName)));
            } else {
              restChannel.sendResponse(new XContentRestResponse(restRequest, OK, buildMessageDocument(restRequest,
                  "Scheduled full reindex for JIRA projects: " + nodeInfo.reindexedProjectNames)));
            }
          }

        });
  }

}
