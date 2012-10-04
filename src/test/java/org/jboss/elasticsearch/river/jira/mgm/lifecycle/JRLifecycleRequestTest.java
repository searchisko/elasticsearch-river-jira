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
import org.junit.Test;

/**
 * Unit test for {@link JRLifecycleRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleRequestTest {

  @Test
  public void constructor_empty() {
    {
      JRLifecycleRequest tested = new JRLifecycleRequest();

      tested.setRiverName("myriver");
      tested.setCommand(JRLifecycleCommand.RESTART);
      Assert.assertEquals("myriver", tested.getRiverName());
      Assert.assertEquals(JRLifecycleCommand.RESTART, tested.getCommand());
    }
  }

  @Test
  public void constructor_filling() {

    try {
      new JRLifecycleRequest(null, JRLifecycleCommand.RESTART);
      Assert.fail("IllegalArgumentException must be thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }

    try {
      new JRLifecycleRequest("myriver", null);
      Assert.fail("IllegalArgumentException must be thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }

    {
      JRLifecycleRequest tested = new JRLifecycleRequest("myriver", JRLifecycleCommand.STOP);
      Assert.assertEquals("myriver", tested.getRiverName());
      Assert.assertEquals(JRLifecycleCommand.STOP, tested.getCommand());
    }
  }

  @Test
  public void serialization() throws IOException {

    {
      JRLifecycleRequest testedSrc = new JRLifecycleRequest("myriver", JRLifecycleCommand.RESTART);
      JRLifecycleRequest testedTarget = performserialization(testedSrc);
      Assert.assertEquals("myriver", testedTarget.getRiverName());
      Assert.assertEquals(JRLifecycleCommand.RESTART, testedTarget.getCommand());
    }

    {
      JRLifecycleRequest testedSrc = new JRLifecycleRequest("myriver2", JRLifecycleCommand.STOP);
      JRLifecycleRequest testedTarget = performserialization(testedSrc);
      Assert.assertEquals("myriver2", testedTarget.getRiverName());
      Assert.assertEquals(JRLifecycleCommand.STOP, testedTarget.getCommand());
    }

  }

  /**
   * @param testedSrc
   * @return
   * @throws IOException
   */
  private JRLifecycleRequest performserialization(JRLifecycleRequest testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    JRLifecycleRequest testedTarget = new JRLifecycleRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    return testedTarget;
  }

}
