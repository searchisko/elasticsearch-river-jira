/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link NodeListRiversResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeListRiversResponseTest {

  DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE);

  @Test
  public void constructor() {
    {
      NodeListRiversResponse tested = new NodeListRiversResponse();
      Assert.assertNull(tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeListRiversResponse tested = new NodeListRiversResponse(dn);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeListRiversResponse tested = new NodeListRiversResponse(dn, null);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertNull(tested.jiraRiverNames);
    }
    {
      NodeListRiversResponse tested = new NodeListRiversResponse(dn, new HashSet<String>());
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertNotNull(tested.jiraRiverNames);
    }
  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      NodeListRiversResponse testedSrc = new NodeListRiversResponse(dn, null);
      NodeListRiversResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      Set<String> rn = new HashSet<String>();
      NodeListRiversResponse testedSrc = new NodeListRiversResponse(dn, rn);
      NodeListRiversResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      Set<String> rn = new HashSet<String>();
      rn.add("muriver");
      NodeListRiversResponse testedSrc = new NodeListRiversResponse(dn, rn);
      NodeListRiversResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      Set<String> rn = new HashSet<String>();
      rn.add("muriver");
      rn.add("muriver2");
      rn.add("muriver3");
      NodeListRiversResponse testedSrc = new NodeListRiversResponse(dn, rn);
      NodeListRiversResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
  }

  private NodeListRiversResponse performSerializationAndBasicAsserts(NodeListRiversResponse testedSrc)
      throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeListRiversResponse testedTarget = new NodeListRiversResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getNode().getId(), testedTarget.getNode().getId());
    Assert.assertEquals(testedSrc.jiraRiverNames, testedTarget.jiraRiverNames);

    return testedTarget;
  }

}
