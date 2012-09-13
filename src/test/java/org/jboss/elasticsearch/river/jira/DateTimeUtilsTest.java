/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link DateTimeUtils}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class DateTimeUtilsTest {

  @Test
  public void formatISODateTime() {
    Assert.assertNull(DateTimeUtils.formatISODateTime(null));
    // Assert.assertEquals("2012-08-14T08:00:00.000-0400",
    // Utils.formatISODateTime(Utils.parseISODateTime("2012-08-14T08:00:00.000-0400")));
  }

  @Test
  public void parseISODateTimeWithMinutePrecise() {
    Assert.assertNull(DateTimeUtils.parseISODateTimeWithMinutePrecise(null));
    Assert.assertNull(DateTimeUtils.parseISODateTimeWithMinutePrecise(""));
    Assert.assertNull(DateTimeUtils.parseISODateTimeWithMinutePrecise("   "));
    try {
      Assert.assertNull(DateTimeUtils.parseISODateTimeWithMinutePrecise("nonsense date"));
      Assert.fail("IllegalArgumentException must be thrown");
    } catch (IllegalArgumentException e) {
      // OK
    }

    Date expected = DateTimeUtils.parseISODateTime("2012-08-14T08:00:00.000-0400");

    Assert.assertEquals(expected, DateTimeUtils.parseISODateTimeWithMinutePrecise("2012-08-14T08:00:00.000-0400"));
    Assert.assertEquals(expected, DateTimeUtils.parseISODateTimeWithMinutePrecise("2012-08-14T08:00:00.001-0400"));
    Assert.assertEquals(expected, DateTimeUtils.parseISODateTimeWithMinutePrecise("2012-08-14T08:00:10.000-0400"));
    Assert.assertEquals(expected, DateTimeUtils.parseISODateTimeWithMinutePrecise("2012-08-14T08:00:10.545-0400"));
    Assert.assertEquals(expected, DateTimeUtils.parseISODateTimeWithMinutePrecise("2012-08-14T08:00:59.999-0400"));
  }

  @Test
  public void roundDateTimeToMinutePrecise() {
    Assert.assertNull(DateTimeUtils.roundDateTimeToMinutePrecise(null));

    Date expected = DateTimeUtils.parseISODateTime("2012-08-14T08:00:00.000-0400");

    Assert.assertEquals(expected,
        DateTimeUtils.roundDateTimeToMinutePrecise(DateTimeUtils.parseISODateTime("2012-08-14T08:00:00.000-0400")));
    Assert.assertEquals(expected,
        DateTimeUtils.roundDateTimeToMinutePrecise(DateTimeUtils.parseISODateTime("2012-08-14T08:00:00.001-0400")));
    Assert.assertEquals(expected,
        DateTimeUtils.roundDateTimeToMinutePrecise(DateTimeUtils.parseISODateTime("2012-08-14T08:00:10.000-0400")));
    Assert.assertEquals(expected,
        DateTimeUtils.roundDateTimeToMinutePrecise(DateTimeUtils.parseISODateTime("2012-08-14T08:00:10.545-0400")));
    Assert.assertEquals(expected,
        DateTimeUtils.roundDateTimeToMinutePrecise(DateTimeUtils.parseISODateTime("2012-08-14T08:00:59.999-0400")));

  }

}
