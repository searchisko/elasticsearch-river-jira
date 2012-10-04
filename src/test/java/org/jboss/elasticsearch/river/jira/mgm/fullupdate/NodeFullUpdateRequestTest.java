/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.junit.Test;

/**
 * Unit test for {@link NodeFullUpdateRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeFullUpdateRequestTest {

  @Test
  public void constructor() {

    // note nodeId cann't be asserted because private and no getter for it :-(

    {
      NodeFullUpdateRequest tested = new NodeFullUpdateRequest();
      Assert.assertNull(tested.getRequest());
    }

    {
      FullUpdateRequest request = new FullUpdateRequest();
      NodeFullUpdateRequest tested = new NodeFullUpdateRequest("myNode", request);
      Assert.assertEquals(request, tested.getRequest());
    }

  }

  @SuppressWarnings("unused")
  @Test
  public void serialization() throws IOException {

    {
      FullUpdateRequest request = new FullUpdateRequest("my river", null);
      NodeFullUpdateRequest testedSrc = new NodeFullUpdateRequest("myNode", request);
      NodeFullUpdateRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }
    {
      FullUpdateRequest request = new FullUpdateRequest("my river 2", "AAA");
      NodeFullUpdateRequest testedSrc = new NodeFullUpdateRequest("myNode2", request);
      NodeFullUpdateRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
    }

  }

  private NodeFullUpdateRequest performSerializationAndBasicAsserts(NodeFullUpdateRequest testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    NodeFullUpdateRequest testedTarget = new NodeFullUpdateRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    Assert.assertEquals(testedSrc.getRequest().getRiverName(), testedTarget.getRequest().getRiverName());
    Assert.assertEquals(testedSrc.getRequest().getProjectKey(), testedTarget.getRequest().getProjectKey());

    return testedTarget;
  }

}
