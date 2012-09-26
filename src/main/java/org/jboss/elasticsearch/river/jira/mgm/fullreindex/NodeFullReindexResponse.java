package org.jboss.elasticsearch.river.jira.mgm.fullreindex;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodeOperationResponse;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Full reindex node response.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeFullReindexResponse extends NodeOperationResponse {

  boolean riverFound;

  boolean projectFound;

  String reindexedProjectNames;

  NodeFullReindexResponse() {

  }

  /**
   * Create response with values to be send back to requestor.
   * 
   * @param node this response is for.
   * @param riverFound set to true if you found river on this node
   * @param projectFound set to true if project reindex was requested and we found this project in given river
   * @param reindexedProjectNames CSV names of jira projects which was forced for full reindex
   */
  public NodeFullReindexResponse(DiscoveryNode node, boolean riverFound, boolean projectFound,
      String reindexedProjectNames) {
    super(node);
    this.riverFound = riverFound;
    this.projectFound = projectFound;
    this.reindexedProjectNames = reindexedProjectNames;
  }

  public static NodeFullReindexResponse readNodeInfo(StreamInput in) throws IOException {
    NodeFullReindexResponse nodeInfo = new NodeFullReindexResponse();
    nodeInfo.readFrom(in);
    return nodeInfo;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    riverFound = in.readBoolean();
    projectFound = in.readBoolean();
    reindexedProjectNames = in.readOptionalString();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeBoolean(riverFound);
    out.writeBoolean(projectFound);
    out.writeOptionalString(reindexedProjectNames);
  }

}
