/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.internal.InternalClient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JRLifecycleAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleActionTest {

  @Test
  public void constructor() {
    Assert.assertEquals(JRLifecycleAction.NAME, JRLifecycleAction.INSTANCE.name());
  }

  @Test
  public void newRequestBuilder() {
    Client client = Mockito.mock(InternalClient.class);

    JRLifecycleRequestBuilder rb = JRLifecycleAction.INSTANCE.newRequestBuilder(client);
    Assert.assertNotNull(rb);
    Assert.assertEquals(client, rb.getClient());
  }

  @Test
  public void newResponse() {
    JRLifecycleResponse rb = JRLifecycleAction.INSTANCE.newResponse();
    Assert.assertNotNull(rb);
  }
}
