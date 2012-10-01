/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import static org.elasticsearch.rest.RestStatus.OK;

import java.io.IOException;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.rest.action.support.RestXContentBuilder;
import org.elasticsearch.river.RiverIndexName;
import org.jboss.elasticsearch.river.jira.Utils;

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

    final boolean isProjectKeyRequest = !Utils.isEmpty(projectKey);

    FullUpdateRequest req = new FullUpdateRequest(riverName, projectKey);

    client.execute(FullUpdateAction.INSTANCE, req, new ActionListener<FullUpdateResponse>() {

      @Override
      public void onResponse(FullUpdateResponse response) {
        try {

          NodeFullUpdateResponse nodeInfo = response.getSuccessNodeResponse();
          if (nodeInfo == null) {
            channel.sendResponse(new XContentRestResponse(request, RestStatus.NOT_FOUND, buildMessageDocument(request,
                "No JiraRiver found for name: " + riverName)));
          } else {
            if (isProjectKeyRequest && !nodeInfo.projectFound) {
              channel.sendResponse(new XContentRestResponse(request, RestStatus.NOT_FOUND, buildMessageDocument(
                  request, "Project '" + projectKey + "' is not indexed by JiraRiver with name: " + riverName)));
            } else {
              channel.sendResponse(new XContentRestResponse(request, OK, buildMessageDocument(request,
                  "Scheduled full reindex for JIRA projects: " + nodeInfo.reindexedProjectNames)));
            }
          }
        } catch (Exception e) {
          onFailure(e);
        }
      }

      @Override
      public void onFailure(Throwable e) {
        try {
          channel.sendResponse(new XContentThrowableRestResponse(request, e));
        } catch (IOException e1) {
          logger.error("Failed to send failure response", e1);
        }
      }

      /**
       * Build response document with only one field called <code>message</code>.
       * 
       * @param request to build document for
       * @param message to be placed in document
       * @return document with message
       * @throws IOException
       */
      protected XContentBuilder buildMessageDocument(RestRequest request, String message) throws IOException {
        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
        builder.startObject();
        builder.field("message", message);
        builder.endObject();
        return builder;
      }

    });
  }

}
