/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import java.io.IOException;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseResponse;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link NodeJRLifecycleResponse} and {@link NodeJRMgmBaseResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeJRLifecycleResponseTest {

  DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE);

  @Test
  public void constructor() {
    {
      NodeJRLifecycleResponse tested = new NodeJRLifecycleResponse();
      Assert.assertNull(tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeJRLifecycleResponse tested = new NodeJRLifecycleResponse(dn);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeJRLifecycleResponse tested = new NodeJRLifecycleResponse(dn, false);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }
    {
      NodeJRLifecycleResponse tested = new NodeJRLifecycleResponse(dn, true);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertTrue(tested.isRiverFound());
    }
  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      NodeJRLifecycleResponse testedSrc = new NodeJRLifecycleResponse(dn, false);
      NodeJRLifecycleResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      NodeJRLifecycleResponse testedSrc = new NodeJRLifecycleResponse(dn, true);
      NodeJRLifecycleResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }

  }

  private NodeJRLifecycleResponse performSerializationAndBasicAsserts(NodeJRLifecycleResponse testedSrc)
      throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeJRLifecycleResponse testedTarget = new NodeJRLifecycleResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getNode().getId(), testedTarget.getNode().getId());
    Assert.assertEquals(testedSrc.isRiverFound(), testedTarget.isRiverFound());

    return testedTarget;
  }

}
