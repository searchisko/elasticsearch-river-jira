/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullreindex;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.junit.Test;

/**
 * Unit test for {@link FullReindexRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullReindexRequestTest {

  @Test
  public void serialization() throws IOException {

    {
      FullReindexRequest testedSrc = new FullReindexRequest();
      FullReindexRequest testedTarget = performser(testedSrc);
      Assert.assertNull(testedTarget.getRiverName());
      Assert.assertNull(testedTarget.getProjectKey());
    }

    {
      FullReindexRequest testedSrc = new FullReindexRequest("myriver", null);
      FullReindexRequest testedTarget = performser(testedSrc);
      Assert.assertEquals("myriver", testedTarget.getRiverName());
      Assert.assertNull(testedTarget.getProjectKey());
    }

    {
      FullReindexRequest testedSrc = new FullReindexRequest("myriver", "ORG");
      FullReindexRequest testedTarget = performser(testedSrc);
      Assert.assertEquals("myriver", testedTarget.getRiverName());
      Assert.assertEquals("ORG", testedTarget.getProjectKey());
    }

  }

  /**
   * @param testedSrc
   * @return
   * @throws IOException
   */
  private FullReindexRequest performser(FullReindexRequest testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    FullReindexRequest testedTarget = new FullReindexRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    return testedTarget;
  }

}
