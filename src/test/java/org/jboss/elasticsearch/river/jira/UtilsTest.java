/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link Utils}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class UtilsTest {

  @Test
  public void isEmpty() {
    Assert.assertTrue(Utils.isEmpty(null));
    Assert.assertTrue(Utils.isEmpty(""));
    Assert.assertTrue(Utils.isEmpty("     "));
    Assert.assertTrue(Utils.isEmpty(" "));
    Assert.assertFalse(Utils.isEmpty("a"));
    Assert.assertFalse(Utils.isEmpty(" a"));
    Assert.assertFalse(Utils.isEmpty("a "));
    Assert.assertFalse(Utils.isEmpty(" a "));
  }

  @Test
  public void trimToNull() {
    Assert.assertNull(Utils.trimToNull(null));
    Assert.assertNull(Utils.trimToNull(""));
    Assert.assertNull(Utils.trimToNull("     "));
    Assert.assertNull(Utils.trimToNull(" "));
    Assert.assertEquals("a", Utils.trimToNull("a"));
    Assert.assertEquals("a", Utils.trimToNull(" a"));
    Assert.assertEquals("a", Utils.trimToNull("a "));
    Assert.assertEquals("a", Utils.trimToNull(" a "));
  }

  @Test
  public void parseCsvString() {
    Assert.assertNull(Utils.parseCsvString(null));
    Assert.assertNull(Utils.parseCsvString(""));
    Assert.assertNull(Utils.parseCsvString("    "));
    Assert.assertNull(Utils.parseCsvString("  ,, ,   ,   "));
    List<String> r = Utils.parseCsvString(" ORG ,UUUU, , PEM  , ,SU07  ");
    Assert.assertEquals(4, r.size());
    Assert.assertEquals("ORG", r.get(0));
    Assert.assertEquals("UUUU", r.get(1));
    Assert.assertEquals("PEM", r.get(2));
    Assert.assertEquals("SU07", r.get(3));
  }

  @Test
  public void createCsvString() {
    Assert.assertNull(Utils.createCsvString(null));
    List<String> c = new ArrayList<String>();
    Assert.assertEquals("", Utils.createCsvString(c));
    c.add("ahoj");
    Assert.assertEquals("ahoj", Utils.createCsvString(c));
    c.add("b");
    c.add("task");
    Assert.assertEquals("ahoj,b,task", Utils.createCsvString(c));
  }

  @Test
  public void nodeIntegerValue() {
    Assert.assertNull(Utils.nodeIntegerValue(null));
    Assert.assertEquals(new Integer(10), Utils.nodeIntegerValue(new Integer(10)));
    Assert.assertEquals(new Integer(10), Utils.nodeIntegerValue(new Short("10")));
    Assert.assertEquals(new Integer(10), Utils.nodeIntegerValue(new Long("10")));
    Assert.assertEquals(new Integer(10), Utils.nodeIntegerValue("10"));
    try {
      Utils.nodeIntegerValue("ahoj");
      Assert.fail("No NumberFormatException thrown.");
    } catch (NumberFormatException e) {
      // OK
    }
  }

  @Test
  public void filterDataInMap() {
    // case - no exceptions on distinct null and empty inputs
    Utils.filterDataInMap(null, null);
    Set<String> keysToLeave = new HashSet<String>();
    Utils.filterDataInMap(null, keysToLeave);
    Map<String, Object> map = new HashMap<String, Object>();
    Utils.filterDataInMap(map, null);
    Utils.filterDataInMap(map, keysToLeave);
    keysToLeave.add("key1");
    Utils.filterDataInMap(null, keysToLeave);
    Utils.filterDataInMap(map, keysToLeave);

    // case - no filtering on null or empty keysToLeave
    keysToLeave.clear();
    map.clear();
    map.put("key1", "val1");
    map.put("key2", "val2");
    Utils.filterDataInMap(map, null);
    Assert.assertEquals(2, map.size());
    Utils.filterDataInMap(map, keysToLeave);
    Assert.assertEquals(2, map.size());

    // case - filtering works
    map.clear();
    keysToLeave.clear();
    map.put("key2", "val2");
    keysToLeave.add("key2");
    Utils.filterDataInMap(map, keysToLeave);
    Assert.assertEquals(1, map.size());
    Assert.assertTrue(map.containsKey("key2"));

    map.clear();
    keysToLeave.clear();
    map.put("key1", "val1");
    map.put("key2", "val2");
    map.put("key3", "val3");
    map.put("key4", "val4");
    keysToLeave.add("key2");
    keysToLeave.add("key3");
    Utils.filterDataInMap(map, keysToLeave);
    Assert.assertEquals(2, map.size());
    Assert.assertTrue(map.containsKey("key2"));
    Assert.assertTrue(map.containsKey("key3"));
  }
}
