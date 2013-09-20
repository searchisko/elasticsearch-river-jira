/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.junit.Test;

/**
 * Unit test for {@link NodeJRStateRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeJRStateRequestTest {

  @Test
  public void constructor() {

    // note nodeId can't be asserted because private and no getter for it :-(

    {
      NodeJRStateRequest tested = new NodeJRStateRequest();
      Assert.assertNull(tested.getRequest());
    }

    {
      JRStateRequest request = new JRStateRequest();
      NodeJRStateRequest tested = new NodeJRStateRequest("myNode", request);
      Assert.assertEquals(request, tested.getRequest());
    }

  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      JRStateRequest request = new JRStateRequest("my river");
      NodeJRStateRequest testedSrc = new NodeJRStateRequest("myNode", request);
      NodeJRStateRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }

  }

  private NodeJRStateRequest performSerializationAndBasicAsserts(NodeJRStateRequest testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeJRStateRequest testedTarget = new NodeJRStateRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getRequest().getRiverName(), testedTarget.getRequest().getRiverName());
    return testedTarget;
  }

}
