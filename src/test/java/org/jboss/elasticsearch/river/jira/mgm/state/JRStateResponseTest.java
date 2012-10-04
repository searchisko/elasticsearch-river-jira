/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Test;

/**
 * Unit test for {@link JRStateResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateResponseTest {

  @Test
  public void constructor_filling() {
    ClusterName cn = new ClusterName("mycluster");

    NodeJRStateResponse[] nodes = new NodeJRStateResponse[0];
    JRStateResponse tested = new JRStateResponse(cn, nodes);

    Assert.assertEquals(cn, tested.clusterName());
    Assert.assertEquals(nodes, tested.getNodes());
    Assert.assertEquals(nodes, tested.nodes());

  }

  @Test
  public void serialization() throws IOException {
    ClusterName cn = new ClusterName("mycluster");

    DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE);
    DiscoveryNode dn2 = new DiscoveryNode("aa2", DummyTransportAddress.INSTANCE);
    DiscoveryNode dn3 = new DiscoveryNode("aa3", DummyTransportAddress.INSTANCE);

    {
      NodeJRStateResponse[] nodes = new NodeJRStateResponse[] {};
      JRStateResponse testedSrc = new JRStateResponse(cn, nodes);
      performSerializationAndBasicAsserts(testedSrc);

    }

    {
      NodeJRStateResponse[] nodes = new NodeJRStateResponse[] { new NodeJRStateResponse(dn, false, null),
          new NodeJRStateResponse(dn2, false, null), new NodeJRStateResponse(dn3, true, "responseText") };
      JRStateResponse testedSrc = new JRStateResponse(cn, nodes);
      JRStateResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);

      Assert.assertEquals(testedSrc.nodes()[0].node().getId(), testedTarget.nodes()[0].node().getId());
      Assert.assertEquals(testedSrc.nodes()[1].node().getId(), testedTarget.nodes()[1].node().getId());
      Assert.assertEquals(testedSrc.nodes()[2].node().getId(), testedTarget.nodes()[2].node().getId());
    }

  }

  private JRStateResponse performSerializationAndBasicAsserts(JRStateResponse testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    JRStateResponse testedTarget = new JRStateResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));

    Assert.assertEquals(testedSrc.getClusterName(), testedTarget.getClusterName());
    Assert.assertEquals(testedSrc.nodes().length, testedTarget.nodes().length);

    return testedTarget;
  }

}
