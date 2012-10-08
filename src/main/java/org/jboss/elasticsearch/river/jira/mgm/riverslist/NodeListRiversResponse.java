package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseResponse;

/**
 * node response with list names of all Jira Rivers running in ES cluster.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeListRiversResponse extends NodeJRMgmBaseResponse {

  Set<String> jiraRiverNames;

  protected NodeListRiversResponse() {
  }

  public NodeListRiversResponse(DiscoveryNode node) {
    super(node);
  }

  /**
   * Create response with values to be send back to requestor.
   * 
   * @param node this response is for.
   * @param jiraRiverNames set of jira river names found on this node.
   */
  public NodeListRiversResponse(DiscoveryNode node, Set<String> jiraRiverNames) {
    super(node, jiraRiverNames != null && !jiraRiverNames.isEmpty());
    this.jiraRiverNames = jiraRiverNames;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    int len = in.readInt();
    if (len >= 0) {
      jiraRiverNames = new HashSet<String>();
      for (int i = 0; i < len; i++) {
        jiraRiverNames.add(in.readString());
      }
    }
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    if (jiraRiverNames == null) {
      out.writeInt(-1);
    } else {
      out.writeInt(jiraRiverNames.size());
      if (jiraRiverNames != null) {
        for (String s : jiraRiverNames) {
          out.writeString(s);
        }
      }
    }
  }

}
