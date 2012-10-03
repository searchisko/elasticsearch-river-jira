/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import org.elasticsearch.cluster.ClusterName;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseResponse;

/**
 * Response JiraRiver lifecycle command. All node responses are aggregated here.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleResponse extends JRMgmBaseResponse<NodeJRLifecycleResponse> {

  public JRLifecycleResponse() {

  }

  public JRLifecycleResponse(ClusterName clusterName, NodeJRLifecycleResponse[] nodes) {
    super(clusterName, nodes);
  }

  @Override
  protected NodeJRLifecycleResponse[] newNodeResponsesArray(int len) {
    return new NodeJRLifecycleResponse[len];
  }

  @Override
  protected NodeJRLifecycleResponse newNodeResponse() {
    return new NodeJRLifecycleResponse();
  }

}
