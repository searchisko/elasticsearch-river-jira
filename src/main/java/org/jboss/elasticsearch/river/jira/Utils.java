/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

/**
 * Utility functions.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class Utils {

  /**
   * Trim String value, return null if empty after trim.
   * 
   * @param src value
   * @return trimmed value or null
   */
  public static String trimToNull(String src) {
    if (src == null || src.length() == 0) {
      return null;
    }
    src = src.trim();
    if (src.length() == 0) {
      return null;
    }
    return src;
  }

  /**
   * Check if String value is null or empty.
   * 
   * @param src value
   * @return <code>true</code> if value is null or empty
   */
  public static boolean isEmpty(String src) {
    return (src == null || src.length() == 0 || src.trim().length() == 0);
  }

  /**
   * Parse comma separated string into list of tokens. Tokens are trimmed, empty tokens are not in result.
   * 
   * @param toParse String to parse
   * @return List of tokens if at least one token exists, null otherwise.
   */
  public static List<String> parseCsvString(String toParse) {
    if (toParse == null || toParse.length() == 0) {
      return null;
    }
    String[] t = toParse.split(",");
    if (t.length == 0) {
      return null;
    }
    List<String> ret = new ArrayList<String>();
    for (String s : t) {
      if (s != null) {
        s = s.trim();
        if (s.length() > 0) {
          ret.add(s);
        }
      }
    }
    if (ret.isEmpty())
      return null;
    else
      return ret;
  }

  /**
   * Create string with comma separated list of values from input collection. Ordering by used Collection implementation
   * iteration order is used.
   * 
   * @param in collection to format
   * @return <code>null</code> if <code>in</code> is <code>null</code>, CSV string in other cases (empty id in is empty)
   */
  public static String createCsvString(Collection<String> in) {
    if (in == null)
      return null;
    if (in.isEmpty()) {
      return "";
    }
    boolean first = true;
    StringBuilder sb = new StringBuilder();
    for (String s : in) {
      if (first)
        first = false;
      else
        sb.append(",");
      sb.append(s);
    }
    return sb.toString();
  }

  /**
   * Get node value as {@link Integer} object instance if possible.
   * 
   * @param node to get value from
   * @return Integer value or null.
   * @throws NumberFormatException if value can't be converted to the int value
   * @see XContentMapValues#nodeIntegerValue(Object, int)
   */
  public static Integer nodeIntegerValue(Object node) throws NumberFormatException {
    if (node == null) {
      return null;
    }
    if (node instanceof Integer) {
      return (Integer) node;
    } else if (node instanceof Number) {
      return new Integer(((Number) node).intValue());
    }

    return Integer.parseInt(node.toString());
  }

  /**
   * Filter data in Map. Leave here only data with keys passed in second parameter.
   * 
   * @param map to filter data inside
   * @param keysToLeave keys leaved in map. If <code>null</code> or empty then no filtering is performed!
   */
  public static <T> void filterDataInMap(Map<T, Object> map, Set<T> keysToLeave) {
    if (map == null || map.isEmpty())
      return;
    if (keysToLeave == null || keysToLeave.isEmpty())
      return;

    Set<T> keysToRemove = new HashSet<T>(map.keySet());
    keysToRemove.removeAll(keysToLeave);
    if (!keysToRemove.isEmpty()) {
      for (T rk : keysToRemove) {
        map.remove(rk);
      }
    }
  }

  /**
   * Remap data in input Map. Leave here only data with defined keys, but change these keys to new ones if necessary.
   * Some new key can be same as some other old key, but if two new keys are same, then only latest value is preserved
   * (given by <code>mapToChange</code> key iteration order).
   * 
   * @param mapToChange Map to remap data inside. Must be mutable!
   * @param remapInstructions instructions how to remap. If <code>null</code> or empty then remap is not performed and
   *          <code>mapToChange</code> is not changed! Key in this Map must be same as key in <code>mapToChange</code>
   *          which may leave there. Value in this map means new key of value in <code>mapToChange</code> after
   *          remapping.
   */
  public static <T> void remapDataInMap(Map<T, Object> mapToChange, Map<T, T> remapInstructions) {
    if (mapToChange == null || mapToChange.isEmpty())
      return;
    if (remapInstructions == null || remapInstructions.isEmpty())
      return;

    Map<T, Object> newMap = new HashMap<T, Object>();
    for (T keyOrig : mapToChange.keySet()) {
      if (remapInstructions.containsKey(keyOrig)) {
        T keyNew = remapInstructions.get(keyOrig);
        newMap.put(keyNew, mapToChange.get(keyOrig));
      }
    }

    mapToChange.clear();
    mapToChange.putAll(newMap);
  }

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

  private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ");

  /**
   * Format Date into ISO 8601 full datetime string.
   * 
   * @param date to format
   * @return formatted string
   */
  public static synchronized final String formatISODateTime(Date date) {
    if (date == null)
      return null;
    return ISO_DATE_FORMAT.format(date);
  }

  /**
   * Parse date string with minute precise - so seconds and milliseconds are set to 0. Used because JQL allows only
   * minute precise queries.
   * 
   * @param dateString to parse
   * @return parsed date rounded to minute precise
   * @throws IllegalArgumentException if date is not parseable
   */
  public static Date parseISODateWithMinutePrecise(String dateString) {
    if (Utils.isEmpty(dateString))
      return null;
    return roundDateToMinutePrecise(ISODateTimeFormat.dateTimeParser().parseDateTime(dateString).toDate());
  }

  /**
   * Change date to minute precise - seconds and milliseconds are set to 0. Used because JQL allows only minute precise
   * queries.
   * 
   * @param date to round
   * @return rounded date
   */
  public static Date roundDateToMinutePrecise(Date date) {
    if (date == null)
      return null;
    Calendar cal = new GregorianCalendar();
    cal.setTime(date);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTime();
  }

  /**
   * Read JSON file from classpath into Map of Map structure.
   * 
   * @param filePath path inside jar/classpath pointing to JSON file to read
   * @return parsed JSON file
   * @throws SettingsException
   */
  public static Map<String, Object> loadJSONFromJarPackagedFile(String filePath) throws SettingsException {
    XContentParser parser = null;
    try {
      parser = XContentFactory.xContent(XContentType.JSON).createParser(Utils.class.getResourceAsStream(filePath));
      return parser.mapAndClose();
    } catch (IOException e) {
      throw new SettingsException(e.getMessage(), e);
    } finally {
      if (parser != null)
        parser.close();
    }
  }

}
