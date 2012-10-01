/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import org.elasticsearch.cluster.ClusterName;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseResponse;

/**
 * Response for Full reindex request. All node responses are agregated here.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateResponse extends JRMgmBaseResponse<NodeFullUpdateResponse> {

  public FullUpdateResponse() {

  }

  public FullUpdateResponse(ClusterName clusterName, NodeFullUpdateResponse[] nodes) {
    super(clusterName, nodes);
  }

  @Override
  protected NodeFullUpdateResponse[] newNodeResponsesArray(int len) {
    return new NodeFullUpdateResponse[len];
  }

  @Override
  protected NodeFullUpdateResponse newNodeResponse() {
    return new NodeFullUpdateResponse();
  }

}
