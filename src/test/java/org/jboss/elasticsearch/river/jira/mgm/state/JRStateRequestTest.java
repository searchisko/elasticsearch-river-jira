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
 * Unit test for {@link JRStateRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateRequestTest {

  @Test
  public void constructor_empty() {
    {
      JRStateRequest tested = new JRStateRequest();

      tested.setRiverName("myriver");
      Assert.assertEquals("myriver", tested.getRiverName());
    }
  }

  @Test
  public void constructor_filling() {

    try {
      new JRStateRequest(null);
      Assert.fail("IllegalArgumentException must be thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }

    {
      JRStateRequest tested = new JRStateRequest("myriver");
      Assert.assertEquals("myriver", tested.getRiverName());
    }
  }

  @Test
  public void serialization() throws IOException {

    {
      JRStateRequest testedSrc = new JRStateRequest("myriver");
      JRStateRequest testedTarget = performserialization(testedSrc);
      Assert.assertEquals("myriver", testedTarget.getRiverName());
    }

  }

  /**
   * @param testedSrc
   * @return
   * @throws IOException
   */
  private JRStateRequest performserialization(JRStateRequest testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    JRStateRequest testedTarget = new JRStateRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    return testedTarget;
  }

}
