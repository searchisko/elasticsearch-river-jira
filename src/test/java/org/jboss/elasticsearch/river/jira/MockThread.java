package org.jboss.elasticsearch.river.jira;

/**
 * Mock thread class used in unit tests to check if some lifecycle methods was called correctly.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class MockThread extends Thread {

  public boolean wasStarted = false;

  public boolean interruptWasCalled = false;

  @Override
  public synchronized void start() {
    wasStarted = true;
  }

  @Override
  public void interrupt() {
    interruptWasCalled = true;
  }

}