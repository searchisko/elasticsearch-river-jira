/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodesOperationResponse;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Base for action responses from to the Jira River. All node responses are aggregated here.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class JRMgmBaseResponse<T extends NodeJRMgmBaseResponse> extends NodesOperationResponse<T> {

  public JRMgmBaseResponse() {

  }

  public JRMgmBaseResponse(ClusterName clusterName, T[] nodes) {
    super(clusterName, nodes);
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    nodes = newNodeResponsesArray(in.readVInt());
    for (int i = 0; i < nodes.length; i++) {
      T node = newNodeResponse();
      node.readFrom(in);
      nodes[i] = node;
    }
  }

  protected abstract T[] newNodeResponsesArray(int len);

  protected abstract T newNodeResponse();

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeVInt(nodes.length);
    for (T node : nodes) {
      node.writeTo(out);
    }
  }

  /**
   * Get response from node where running jira river instance was found.
   * 
   * @return response from node with river or null if no river with given name was found in any node.
   */
  public T getSuccessNodeResponse() {
    if (nodes == null || nodes.length == 0)
      return null;

    for (T resp : nodes) {
      if (resp.riverFound) {
        return resp;
      }
    }
    return null;
  }

}
