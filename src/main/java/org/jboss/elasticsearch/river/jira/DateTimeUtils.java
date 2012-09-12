/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;

/**
 * Date and Time related utility functions.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class DateTimeUtils {

  /**
   * Parse ISO datetime string.
   * 
   * @param dateString to parse
   * @return parsed date
   * @throws IllegalArgumentException if date is not parseable
   */
  public static final Date parseISODateTime(String dateString) {
    if (Utils.isEmpty(dateString))
      return null;
    return ISODateTimeFormat.dateTimeParser().parseDateTime(dateString).toDate();
  }

  protected static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ");

  /**
   * Format Date into ISO 8601 full datetime string.
   * 
   * @param date to format
   * @return formatted string
   */
  public static final String formatISODateTime(Date date) {
    if (date == null)
      return null;
    synchronized (ISO_DATE_FORMAT) {
      return ISO_DATE_FORMAT.format(date);
    }
  }

  /**
   * Parse date string with minute precise - so seconds and milliseconds are set to 0. Used because JQL allows only
   * minute precise queries.
   * 
   * @param dateString to parse
   * @return parsed date rounded to minute precise
   * @throws IllegalArgumentException if date is not parseable
   */
  public static Date parseISODateTimeWithMinutePrecise(String dateString) {
    if (Utils.isEmpty(dateString))
      return null;
    return DateTimeUtils.roundDateTimeToMinutePrecise(ISODateTimeFormat.dateTimeParser().parseDateTime(dateString)
        .toDate());
  }

  /**
   * Change date to minute precise - seconds and milliseconds are set to 0. Used because JQL allows only minute precise
   * queries.
   * 
   * @param date to round
   * @return rounded date
   */
  public static Date roundDateTimeToMinutePrecise(Date date) {
    if (date == null)
      return null;
    Calendar cal = new GregorianCalendar();
    cal.setTime(date);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTime();
  }

}
