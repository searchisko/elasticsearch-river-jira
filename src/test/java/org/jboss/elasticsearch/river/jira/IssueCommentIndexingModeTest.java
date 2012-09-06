/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.common.settings.SettingsException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link IssueCommentIndexingMode}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IssueCommentIndexingModeTest {

  @Test
  public void parseConfiguration() {
    Assert.assertEquals(IssueCommentIndexingMode.NONE, IssueCommentIndexingMode.parseConfiguration("none"));
    Assert.assertEquals(IssueCommentIndexingMode.NONE, IssueCommentIndexingMode.parseConfiguration("None"));
    Assert.assertEquals(IssueCommentIndexingMode.CHILD, IssueCommentIndexingMode.parseConfiguration("child"));
    Assert.assertEquals(IssueCommentIndexingMode.CHILD, IssueCommentIndexingMode.parseConfiguration("Child"));
    Assert.assertEquals(IssueCommentIndexingMode.STANDALONE, IssueCommentIndexingMode.parseConfiguration("standalone"));
    Assert.assertEquals(IssueCommentIndexingMode.STANDALONE, IssueCommentIndexingMode.parseConfiguration("Standalone"));
    Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED, IssueCommentIndexingMode.parseConfiguration("embedded"));
    Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED, IssueCommentIndexingMode.parseConfiguration("Embedded"));
    Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED, IssueCommentIndexingMode.parseConfiguration(null));
    Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED, IssueCommentIndexingMode.parseConfiguration("  "));

    try {
      IssueCommentIndexingMode.parseConfiguration("nonsense");
      Assert.fail("SettingsException must be thrown");
    } catch (SettingsException e) {
      // OK
    }
  }

  @Test
  public void isExtraDocumentIndexed() {
    Assert.assertFalse(IssueCommentIndexingMode.NONE.isExtraDocumentIndexed());
    Assert.assertFalse(IssueCommentIndexingMode.EMBEDDED.isExtraDocumentIndexed());
    Assert.assertTrue(IssueCommentIndexingMode.STANDALONE.isExtraDocumentIndexed());
    Assert.assertTrue(IssueCommentIndexingMode.CHILD.isExtraDocumentIndexed());
  }

}
