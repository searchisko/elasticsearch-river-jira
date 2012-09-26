/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullreindex;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodesOperationResponse;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Response for Full reindex request. All node responses are agregated here.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullReindexResponse extends NodesOperationResponse<NodeFullReindexResponse> {

  public FullReindexResponse() {

  }

  public FullReindexResponse(ClusterName clusterName, NodeFullReindexResponse[] nodes) {
    super(clusterName, nodes);
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    nodes = new NodeFullReindexResponse[in.readVInt()];
    for (int i = 0; i < nodes.length; i++) {
      nodes[i] = NodeFullReindexResponse.readNodeInfo(in);
    }
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeVInt(nodes.length);
    for (NodeFullReindexResponse node : nodes) {
      node.writeTo(out);
    }
  }

  /**
   * Get response from node where river was found.
   * 
   * @return response from node with river or null if no river was found in any node.
   */
  public NodeFullReindexResponse getSuccessNodeResponse() {
    if (nodes == null || nodes.length == 0)
      return null;

    for (NodeFullReindexResponse resp : nodes) {
      if (resp.riverFound) {
        return resp;
      }
    }
    return null;
  }

}
