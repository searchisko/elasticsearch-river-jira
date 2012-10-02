/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import org.elasticsearch.cluster.ClusterName;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseResponse;

/**
 * Response JiraRiver state information. All node responses are agregated here.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateResponse extends JRMgmBaseResponse<NodeJRStateResponse> {

  public JRStateResponse() {

  }

  public JRStateResponse(ClusterName clusterName, NodeJRStateResponse[] nodes) {
    super(clusterName, nodes);
  }

  @Override
  protected NodeJRStateResponse[] newNodeResponsesArray(int len) {
    return new NodeJRStateResponse[len];
  }

  @Override
  protected NodeJRStateResponse newNodeResponse() {
    return new NodeJRStateResponse();
  }

}
