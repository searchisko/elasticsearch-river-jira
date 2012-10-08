/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import org.elasticsearch.cluster.ClusterName;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseResponse;

/**
 * Response with list names of all Jira Rivers running in ES cluster.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ListRiversResponse extends JRMgmBaseResponse<NodeListRiversResponse> {

  public ListRiversResponse() {

  }

  public ListRiversResponse(ClusterName clusterName, NodeListRiversResponse[] nodes) {
    super(clusterName, nodes);
  }

  @Override
  protected NodeListRiversResponse[] newNodeResponsesArray(int len) {
    return new NodeListRiversResponse[len];
  }

  @Override
  protected NodeListRiversResponse newNodeResponse() {
    return new NodeListRiversResponse();
  }

}
