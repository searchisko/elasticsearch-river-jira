/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.internal.InternalClient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JRStateAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateActionTest {

  @Test
  public void constructor() {
    Assert.assertEquals(JRStateAction.NAME, JRStateAction.INSTANCE.name());
  }

  @Test
  public void newRequestBuilder() {
    Client client = Mockito.mock(InternalClient.class);

    JRStateRequestBuilder rb = JRStateAction.INSTANCE.newRequestBuilder(client);
    Assert.assertNotNull(rb);
  }

  @Test
  public void newResponse() {
    JRStateResponse rb = JRStateAction.INSTANCE.newResponse();
    Assert.assertNotNull(rb);
  }
}
