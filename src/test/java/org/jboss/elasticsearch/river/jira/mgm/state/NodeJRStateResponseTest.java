/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import java.io.IOException;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link NodeJRStateResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeJRStateResponseTest {

  DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE);

  @Test
  public void constructor() {
    {
      NodeJRStateResponse tested = new NodeJRStateResponse();
      Assert.assertNull(tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeJRStateResponse tested = new NodeJRStateResponse(dn);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeJRStateResponse tested = new NodeJRStateResponse(dn, false, null);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
      Assert.assertNull(tested.stateInformation);
    }
    {
      NodeJRStateResponse tested = new NodeJRStateResponse(dn, true, "state info");
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertTrue(tested.isRiverFound());
      Assert.assertEquals("state info", tested.stateInformation);
    }
  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      NodeJRStateResponse testedSrc = new NodeJRStateResponse(dn, false, null);
      NodeJRStateResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      NodeJRStateResponse testedSrc = new NodeJRStateResponse(dn, true, "state information");
      NodeJRStateResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }

  }

  private NodeJRStateResponse performSerializationAndBasicAsserts(NodeJRStateResponse testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeJRStateResponse testedTarget = new NodeJRStateResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getNode().getId(), testedTarget.getNode().getId());
    Assert.assertEquals(testedSrc.isRiverFound(), testedTarget.isRiverFound());
    Assert.assertEquals(testedSrc.stateInformation, testedTarget.stateInformation);
    return testedTarget;
  }

}
