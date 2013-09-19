/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link NodeFullUpdateResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeFullUpdateResponseTest {

  DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE, Version.CURRENT);

  @Test
  public void constructor() {
    {
      NodeFullUpdateResponse tested = new NodeFullUpdateResponse();
      Assert.assertNull(tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
    }

    {
      NodeFullUpdateResponse tested = new NodeFullUpdateResponse(dn);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
      Assert.assertFalse(tested.projectFound);
      Assert.assertNull(tested.reindexedProjectNames);
    }

    {
      NodeFullUpdateResponse tested = new NodeFullUpdateResponse(dn, false, false, null);
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertFalse(tested.isRiverFound());
      Assert.assertFalse(tested.projectFound);
      Assert.assertEquals(null, tested.reindexedProjectNames);

    }
    {
      NodeFullUpdateResponse tested = new NodeFullUpdateResponse(dn, true, true, "AAA");
      Assert.assertEquals(dn, tested.getNode());
      Assert.assertTrue(tested.isRiverFound());
      Assert.assertTrue(tested.projectFound);
      Assert.assertEquals("AAA", tested.reindexedProjectNames);
    }
  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      NodeFullUpdateResponse testedSrc = new NodeFullUpdateResponse(dn, false, false, null);
      NodeFullUpdateResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      NodeFullUpdateResponse testedSrc = new NodeFullUpdateResponse(dn, true, true, "test AAA");
      NodeFullUpdateResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }

  }

  private NodeFullUpdateResponse performSerializationAndBasicAsserts(NodeFullUpdateResponse testedSrc)
      throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeFullUpdateResponse testedTarget = new NodeFullUpdateResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getNode().getId(), testedTarget.getNode().getId());
    Assert.assertEquals(testedSrc.isRiverFound(), testedTarget.isRiverFound());
    Assert.assertEquals(testedSrc.projectFound, testedTarget.projectFound);
    Assert.assertEquals(testedSrc.reindexedProjectNames, testedTarget.reindexedProjectNames);
    return testedTarget;
  }

}
