/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseRequest;
import org.junit.Test;

/**
 * Unit test for {@link NodeJRLifecycleRequest} and {@link NodeJRMgmBaseRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeJRLifecycleRequestTest {

  @Test
  public void constructor() {

    // note nodeId cann't be asserted because private and no getter for it :-(

    {
      NodeJRLifecycleRequest tested = new NodeJRLifecycleRequest();
      Assert.assertNull(tested.getRequest());
    }

    {
      JRLifecycleRequest request = new JRLifecycleRequest();
      NodeJRLifecycleRequest tested = new NodeJRLifecycleRequest("myNode", request);
      Assert.assertEquals(request, tested.getRequest());
    }

  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      JRLifecycleRequest request = new JRLifecycleRequest("my river", JRLifecycleCommand.STOP);
      NodeJRLifecycleRequest testedSrc = new NodeJRLifecycleRequest("myNode", request);
      NodeJRLifecycleRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      JRLifecycleRequest request = new JRLifecycleRequest("my river 2", JRLifecycleCommand.RESTART);
      NodeJRLifecycleRequest testedSrc = new NodeJRLifecycleRequest("myNode2", request);
      NodeJRLifecycleRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }

  }

  private NodeJRLifecycleRequest performSerializationAndBasicAsserts(NodeJRLifecycleRequest testedSrc)
      throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeJRLifecycleRequest testedTarget = new NodeJRLifecycleRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getRequest().getRiverName(), testedTarget.getRequest().getRiverName());
    Assert.assertEquals(testedSrc.getRequest().getCommand(), testedTarget.getRequest().getCommand());

    return testedTarget;
  }

}
