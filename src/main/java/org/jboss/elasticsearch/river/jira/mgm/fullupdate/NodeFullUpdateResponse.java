package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseResponse;

/**
 * Full reindex node response.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeFullUpdateResponse extends NodeJRMgmBaseResponse {

  boolean projectFound;

  String reindexedProjectNames;

  protected NodeFullUpdateResponse() {
  }

  public NodeFullUpdateResponse(DiscoveryNode node) {
    super(node);
  }

  /**
   * Create response with values to be send back to requestor.
   * 
   * @param node this response is for.
   * @param riverFound set to true if you found river on this node
   * @param projectFound set to true if project reindex was requested and we found this project in given river
   * @param reindexedProjectNames CSV names of jira projects which was forced for full reindex
   */
  public NodeFullUpdateResponse(DiscoveryNode node, boolean riverFound, boolean projectFound,
      String reindexedProjectNames) {
    super(node, riverFound);
    this.projectFound = projectFound;
    this.reindexedProjectNames = reindexedProjectNames;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    projectFound = in.readBoolean();
    reindexedProjectNames = in.readOptionalString();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeBoolean(projectFound);
    out.writeOptionalString(reindexedProjectNames);
  }

}
