/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.jboss.elasticsearch.river.jira.testtools.TestUtils;
import org.junit.Test;

/**
 * Unit test for {@link ProjectIndexingInfo}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ProjectIndexingInfoTest {

	@Test
	public void buildDocument() throws Exception {

		TestUtils.assertJsonEqual(TestUtils.readStringFromClasspathFile("/asserts/ProjectIndexingInfoTest_1.json"),
				new ProjectIndexingInfo("ORG", true, 10, 1, 1, DateTimeUtils.parseISODateTime("2012-09-10T12:55:58Z"), true,
						1250, null).buildDocument(XContentFactory.jsonBuilder(), true, true).string());

		TestUtils.assertJsonEqual(TestUtils.readStringFromClasspathFile("/asserts/ProjectIndexingInfoTest_2.json"),
				new ProjectIndexingInfo("ORG", true, 10, 1, 1, DateTimeUtils.parseISODateTime("2012-09-10T12:56:50Z"), false,
						125, "Error message").buildDocument(XContentFactory.jsonBuilder(), true, true).string());

		TestUtils.assertJsonEqual(TestUtils.readStringFromClasspathFile("/asserts/ProjectIndexingInfoTest_3.json"),
				new ProjectIndexingInfo("ORG", true, 10, 1, 1, DateTimeUtils.parseISODateTime("2012-09-10T12:55:58Z"), true,
						1250, null).buildDocument(XContentFactory.jsonBuilder(), false, true).string());

		TestUtils.assertJsonEqual(TestUtils.readStringFromClasspathFile("/asserts/ProjectIndexingInfoTest_4.json"),
				new ProjectIndexingInfo("ORG", true, 10, 1, 1, DateTimeUtils.parseISODateTime("2012-09-10T12:55:58Z"), true,
						1250, null).buildDocument(XContentFactory.jsonBuilder(), false, false).string());
	}

	@Test
	public void readFromDocument() throws IOException {
		readFromDocumentInternalTest(new ProjectIndexingInfo("ORG", true, 10, 1, 1,
				DateTimeUtils.parseISODateTime("2012-09-10T12:55:58Z"), true, 1250, null));
		readFromDocumentInternalTest(new ProjectIndexingInfo("ORGA", false, 10, 0, 1,
				DateTimeUtils.parseISODateTime("2012-09-11T02:55:58Z"), false, 125, "Error"));
	}

	private void readFromDocumentInternalTest(ProjectIndexingInfo src) throws IOException {
		ProjectIndexingInfo result = ProjectIndexingInfo.readFromDocument(XContentFactory.xContent(XContentType.JSON)
				.createParser(src.buildDocument(XContentFactory.jsonBuilder(), true, true).string()).mapAndClose());

		Assert.assertEquals(src.projectKey, result.projectKey);
		Assert.assertEquals(src.fullUpdate, result.fullUpdate);
		Assert.assertEquals(src.issuesUpdated, result.issuesUpdated);
		Assert.assertEquals(src.issuesDeleted, result.issuesDeleted);
		// not stored and read for now!
		Assert.assertEquals(0, result.commentsDeleted);
		Assert.assertEquals(src.startDate, result.startDate);
		Assert.assertEquals(src.finishedOK, result.finishedOK);
		Assert.assertEquals(src.timeElapsed, result.timeElapsed);
		Assert.assertEquals(src.errorMessage, result.errorMessage);
	}

}
