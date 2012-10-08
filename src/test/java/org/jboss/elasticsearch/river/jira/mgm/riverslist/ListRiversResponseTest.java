/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Test;

/**
 * Unit test for {@link ListRiversResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ListRiversResponseTest {

  @Test
  public void constructor_filling() {
    ClusterName cn = new ClusterName("mycluster");

    NodeListRiversResponse[] nodes = new NodeListRiversResponse[0];
    ListRiversResponse tested = new ListRiversResponse(cn, nodes);

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
      NodeListRiversResponse[] nodes = new NodeListRiversResponse[] {};
      ListRiversResponse testedSrc = new ListRiversResponse(cn, nodes);
      performSerializationAndBasicAsserts(testedSrc);

    }

    {
      NodeListRiversResponse[] nodes = new NodeListRiversResponse[] { new NodeListRiversResponse(dn),
          new NodeListRiversResponse(dn2), new NodeListRiversResponse(dn3) };
      ListRiversResponse testedSrc = new ListRiversResponse(cn, nodes);
      ListRiversResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);

      Assert.assertEquals(testedSrc.nodes()[0].node().getId(), testedTarget.nodes()[0].node().getId());
      Assert.assertEquals(testedSrc.nodes()[1].node().getId(), testedTarget.nodes()[1].node().getId());
      Assert.assertEquals(testedSrc.nodes()[2].node().getId(), testedTarget.nodes()[2].node().getId());
    }

  }

  private ListRiversResponse performSerializationAndBasicAsserts(ListRiversResponse testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    ListRiversResponse testedTarget = new ListRiversResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));

    Assert.assertEquals(testedSrc.getClusterName(), testedTarget.getClusterName());
    Assert.assertEquals(testedSrc.nodes().length, testedTarget.nodes().length);

    return testedTarget;
  }

}
