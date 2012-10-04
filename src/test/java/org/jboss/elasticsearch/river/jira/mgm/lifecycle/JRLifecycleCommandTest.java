/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import junit.framework.Assert;

import org.junit.Test;

/**
 * Unit test for {@link JRLifecycleCommand}
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleCommandTest {

  @Test
  public void detectById() {
    Assert.assertEquals(JRLifecycleCommand.RESTART, JRLifecycleCommand.detectById(JRLifecycleCommand.RESTART.getId()));
    Assert.assertEquals(JRLifecycleCommand.STOP, JRLifecycleCommand.detectById(JRLifecycleCommand.STOP.getId()));
    Assert.assertNull(JRLifecycleCommand.detectById(3));
    Assert.assertNull(JRLifecycleCommand.detectById(4));
    Assert.assertNull(JRLifecycleCommand.detectById(0));
  }

}
