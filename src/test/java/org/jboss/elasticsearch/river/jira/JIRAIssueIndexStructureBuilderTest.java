/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import junit.framework.Assert;

import org.junit.Test;

/**
 * Unit test for {@link JIRA5RestIssueIndexStructureBuilder}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAIssueIndexStructureBuilderTest {

  @Test
  public void getRequiredJIRAIssueFields() {
    JIRA5RestIssueIndexStructureBuilder tested = new JIRA5RestIssueIndexStructureBuilder(null, null, null);
    Assert.assertEquals("key,status,issuetype,created,updated,reporter,assignee,summary,description",
        tested.getRequiredJIRAIssueFields());
  }

}
