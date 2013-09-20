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
		Assert.assertEquals(IssueCommentIndexingMode.NONE,
				IssueCommentIndexingMode.parseConfiguration("none", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.NONE,
				IssueCommentIndexingMode.parseConfiguration("None", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.CHILD,
				IssueCommentIndexingMode.parseConfiguration("child", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.CHILD,
				IssueCommentIndexingMode.parseConfiguration("Child", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.STANDALONE,
				IssueCommentIndexingMode.parseConfiguration("standalone", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.STANDALONE,
				IssueCommentIndexingMode.parseConfiguration("Standalone", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED,
				IssueCommentIndexingMode.parseConfiguration("embedded", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED,
				IssueCommentIndexingMode.parseConfiguration("Embedded", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED,
				IssueCommentIndexingMode.parseConfiguration(null, IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.EMBEDDED,
				IssueCommentIndexingMode.parseConfiguration("  ", IssueCommentIndexingMode.EMBEDDED));
		Assert.assertEquals(IssueCommentIndexingMode.NONE,
				IssueCommentIndexingMode.parseConfiguration(null, IssueCommentIndexingMode.NONE));
		Assert.assertEquals(IssueCommentIndexingMode.NONE,
				IssueCommentIndexingMode.parseConfiguration("  ", IssueCommentIndexingMode.NONE));

		try {
			IssueCommentIndexingMode.parseConfiguration("nonsense", IssueCommentIndexingMode.NONE);
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
