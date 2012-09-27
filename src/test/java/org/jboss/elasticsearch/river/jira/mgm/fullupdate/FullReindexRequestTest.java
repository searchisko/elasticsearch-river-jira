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
import org.jboss.elasticsearch.river.jira.mgm.fullupdate.FullUpdateRequest;
import org.junit.Test;

/**
 * Unit test for {@link FullUpdateRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullReindexRequestTest {

  @Test
  public void serialization() throws IOException {

    {
      FullUpdateRequest testedSrc = new FullUpdateRequest();
      FullUpdateRequest testedTarget = performser(testedSrc);
      Assert.assertNull(testedTarget.getRiverName());
      Assert.assertNull(testedTarget.getProjectKey());
    }

    {
      FullUpdateRequest testedSrc = new FullUpdateRequest("myriver", null);
      FullUpdateRequest testedTarget = performser(testedSrc);
      Assert.assertEquals("myriver", testedTarget.getRiverName());
      Assert.assertNull(testedTarget.getProjectKey());
    }

    {
      FullUpdateRequest testedSrc = new FullUpdateRequest("myriver", "ORG");
      FullUpdateRequest testedTarget = performser(testedSrc);
      Assert.assertEquals("myriver", testedTarget.getRiverName());
      Assert.assertEquals("ORG", testedTarget.getProjectKey());
    }

  }

  /**
   * @param testedSrc
   * @return
   * @throws IOException
   */
  private FullUpdateRequest performser(FullUpdateRequest testedSrc) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);
    FullUpdateRequest testedTarget = new FullUpdateRequest();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));
    return testedTarget;
  }

}
