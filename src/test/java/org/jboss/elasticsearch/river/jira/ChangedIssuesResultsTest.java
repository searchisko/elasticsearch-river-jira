/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link ChangedIssuesResults}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ChangedIssuesResultsTest {

  @Test
  public void constructorAndGetters() {
    try {
      new ChangedIssuesResults(null, null, 1, 2);
      Assert.fail("IllegalArgumentException not thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      new ChangedIssuesResults(null, 1, null, 2);
      Assert.fail("IllegalArgumentException not thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      new ChangedIssuesResults(null, 1, 2, null);
      Assert.fail("IllegalArgumentException not thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }

    ChangedIssuesResults tested = new ChangedIssuesResults(null, 1, 2, 3);
    Assert.assertEquals(1, tested.getStartAt());
    Assert.assertEquals(2, tested.getMaxResults());
    Assert.assertEquals(3, tested.getTotal());
    Assert.assertNull(tested.getIssues());
    Assert.assertEquals(0, tested.getIssuesCount());

    List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();
    tested = new ChangedIssuesResults(issues, 1, 2, 3);
    Assert.assertEquals(1, tested.getStartAt());
    Assert.assertEquals(2, tested.getMaxResults());
    Assert.assertEquals(3, tested.getTotal());
    Assert.assertNotNull(tested.getIssues());
    Assert.assertEquals(0, tested.getIssuesCount());

    issues.add(new HashMap<String, Object>());
    tested = new ChangedIssuesResults(issues, 10, 20, 300);
    Assert.assertEquals(10, tested.getStartAt());
    Assert.assertEquals(20, tested.getMaxResults());
    Assert.assertEquals(300, tested.getTotal());
    Assert.assertNotNull(tested.getIssues());
    Assert.assertEquals(1, tested.getIssuesCount());
  }

  @Test
  public void toStringTest() {
    ChangedIssuesResults tested = new ChangedIssuesResults(null, 1, 2, 3);
    Assert.assertNotNull(tested.toString());
  }
}
