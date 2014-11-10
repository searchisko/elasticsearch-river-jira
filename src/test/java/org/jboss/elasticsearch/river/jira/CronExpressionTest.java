/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit test for {@link CronExpression}. Taken from
 * http://svn.terracotta.org/svn/quartz/trunk/quartz-core/src/test/java/org/quartz/CronExpressionTest.java and adapted
 * to jUnit 4.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class CronExpressionTest {

	private static final TimeZone EST_TIME_ZONE = TimeZone.getTimeZone("US/Eastern");

	/*
	 * Test method for 'org.quartz.CronExpression.isSatisfiedBy(Date)'.
	 */
	@Test
	public void testIsSatisfiedBy() throws Exception {
		CronExpression cronExpression = new CronExpression("0 15 10 * * ? 2005");

		Calendar cal = Calendar.getInstance();

		cal.set(2005, Calendar.JUNE, 1, 10, 15, 0);
		assertTrue(cronExpression.isSatisfiedBy(cal.getTime()));

		cal.set(Calendar.YEAR, 2006);
		assertFalse(cronExpression.isSatisfiedBy(cal.getTime()));

		cal = Calendar.getInstance();
		cal.set(2005, Calendar.JUNE, 1, 10, 16, 0);
		assertFalse(cronExpression.isSatisfiedBy(cal.getTime()));

		cal = Calendar.getInstance();
		cal.set(2005, Calendar.JUNE, 1, 10, 14, 0);
		assertFalse(cronExpression.isSatisfiedBy(cal.getTime()));
	}

	@Test
	public void testLastDayOffset() throws Exception {
		CronExpression cronExpression = new CronExpression("0 15 10 L-2 * ? 2010");

		Calendar cal = Calendar.getInstance();

		cal.set(2010, Calendar.OCTOBER, 29, 10, 15, 0); // last day - 2
		assertTrue(cronExpression.isSatisfiedBy(cal.getTime()));

		cal.set(2010, Calendar.OCTOBER, 28, 10, 15, 0);
		assertFalse(cronExpression.isSatisfiedBy(cal.getTime()));

		cronExpression = new CronExpression("0 15 10 L-5W * ? 2010");

		cal.set(2010, Calendar.OCTOBER, 26, 10, 15, 0); // last day - 5
		assertTrue(cronExpression.isSatisfiedBy(cal.getTime()));

		cronExpression = new CronExpression("0 15 10 L-1 * ? 2010");

		cal.set(2010, Calendar.OCTOBER, 30, 10, 15, 0); // last day - 1
		assertTrue(cronExpression.isSatisfiedBy(cal.getTime()));

		cronExpression = new CronExpression("0 15 10 L-1W * ? 2010");

		cal.set(2010, Calendar.OCTOBER, 29, 10, 15, 0); // nearest weekday to last day - 1 (29th is a friday in 2010)
		assertTrue(cronExpression.isSatisfiedBy(cal.getTime()));

	}

	/*
	 * QUARTZ-571: Showing that expressions with months correctly serialize.
	 */
	@Test
	public void testQuartz571() throws Exception {
		CronExpression cronExpression = new CronExpression("19 15 10 4 Apr ? ");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(cronExpression);
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		ObjectInputStream ois = new ObjectInputStream(bais);
		CronExpression newExpression = (CronExpression) ois.readObject();

		assertEquals(newExpression.getCronExpression(), cronExpression.getCronExpression());

		// if broken, this will throw an exception
		newExpression.getNextValidTimeAfter(new Date());
	}

	/**
	 * QTZ-259 : last day offset causes repeating fire time
	 * 
	 */
	@Test
	public void testQtz259() throws Exception {
		CronExpression trigger = new CronExpression("0 0 0 L-2 * ? *");

		int i = 0;
		Date pdate = trigger.getNextValidTimeAfter(new Date());
		while (++i < 26) {
			Date date = trigger.getNextValidTimeAfter(pdate);
			// System.out.println("fireTime: " + date + ", previousFireTime: " + pdate);
			assertFalse("Next fire time is the same as previous fire time!", pdate.equals(date));
			pdate = date;
		}
	}

	/**
	 * QTZ-259 : last day offset causes repeating fire time
	 * 
	 */
	@Test
	public void testQtz259LW() throws Exception {
		CronExpression trigger = new CronExpression("0 0 0 LW * ? *");

		int i = 0;
		Date pdate = trigger.getNextValidTimeAfter(new Date());
		while (++i < 26) {
			Date date = trigger.getNextValidTimeAfter(pdate);
			// System.out.println("fireTime: " + date + ", previousFireTime: " + pdate);
			assertFalse("Next fire time is the same as previous fire time!", pdate.equals(date));
			pdate = date;
		}
	}

	/*
	 * QUARTZ-574: Showing that storeExpressionVals correctly calculates the month number
	 */
	@Test
	public void testQuartz574() {
		try {
			new CronExpression("* * * * Foo ? ");
			fail("Expected ParseException did not fire for non-existent month");
		} catch (ParseException pe) {
			assertTrue("Incorrect ParseException thrown", pe.getMessage().startsWith("Invalid Month value:"));
		}

		try {
			new CronExpression("* * * * Jan-Foo ? ");
			fail("Expected ParseException did not fire for non-existent month");
		} catch (ParseException pe) {
			assertTrue("Incorrect ParseException thrown", pe.getMessage().startsWith("Invalid Month value:"));
		}
	}

	@Test
	public void testQuartz621() {
		try {
			new CronExpression("0 0 * * * *");
			fail("Expected ParseException did not fire for wildcard day-of-month and day-of-week");
		} catch (ParseException pe) {
			assertTrue(
					"Incorrect ParseException thrown",
					pe.getMessage().startsWith(
							"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented."));
		}
		try {
			new CronExpression("0 0 * 4 * *");
			fail("Expected ParseException did not fire for specified day-of-month and wildcard day-of-week");
		} catch (ParseException pe) {
			assertTrue(
					"Incorrect ParseException thrown",
					pe.getMessage().startsWith(
							"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented."));
		}
		try {
			new CronExpression("0 0 * * * 4");
			fail("Expected ParseException did not fire for wildcard day-of-month and specified day-of-week");
		} catch (ParseException pe) {
			assertTrue(
					"Incorrect ParseException thrown",
					pe.getMessage().startsWith(
							"Support for specifying both a day-of-week AND a day-of-month parameter is not implemented."));
		}
	}

	@Test
	public void testQuartz640() throws ParseException {
		try {
			new CronExpression("0 43 9 1,5,29,L * ?");
			fail("Expected ParseException did not fire for L combined with other days of the month");
		} catch (ParseException pe) {
			assertTrue(
					"Incorrect ParseException thrown",
					pe.getMessage().startsWith(
							"Support for specifying 'L' and 'LW' with other days of the month is not implemented"));
		}
		try {
			new CronExpression("0 43 9 ? * SAT,SUN,L");
			fail("Expected ParseException did not fire for L combined with other days of the week");
		} catch (ParseException pe) {
			assertTrue("Incorrect ParseException thrown",
					pe.getMessage().startsWith("Support for specifying 'L' with other days of the week is not implemented"));
		}
		try {
			new CronExpression("0 43 9 ? * 6,7,L");
			fail("Expected ParseException did not fire for L combined with other days of the week");
		} catch (ParseException pe) {
			assertTrue("Incorrect ParseException thrown",
					pe.getMessage().startsWith("Support for specifying 'L' with other days of the week is not implemented"));
		}
		try {
			new CronExpression("0 43 9 ? * 5L");
		} catch (ParseException pe) {
			fail("Unexpected ParseException thrown for supported '5L' expression.");
		}
	}

	@Test
	public void testQtz96() throws ParseException {
		try {
			new CronExpression("0/5 * * 32W 1 ?");
			fail("Expected ParseException did not fire for W with value larger than 31");
		} catch (ParseException pe) {
			assertTrue("Incorrect ParseException thrown",
					pe.getMessage().startsWith("The 'W' option does not make sense with values larger than"));
		}
	}

	@Test
	public void testQtz395_CopyConstructorMustPreserveTimeZone() throws ParseException {
		TimeZone nonDefault = TimeZone.getTimeZone("Europe/Brussels");
		if (nonDefault.equals(TimeZone.getDefault())) {
			nonDefault = EST_TIME_ZONE;
		}
		CronExpression cronExpression = new CronExpression("0 15 10 * * ? 2005");
		cronExpression.setTimeZone(nonDefault);

		CronExpression copyCronExpression = new CronExpression(cronExpression);
		assertEquals(nonDefault, copyCronExpression.getTimeZone());
	}

}
