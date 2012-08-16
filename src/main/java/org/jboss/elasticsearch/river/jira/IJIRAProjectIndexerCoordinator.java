package org.jboss.elasticsearch.river.jira;

/**
 * Interface for JIRA project indexer coordinator component.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IJIRAProjectIndexerCoordinator extends Runnable {

  /**
   * Report that indexing of JIRA project was finished. Used to coordinate parallel indexing of all projects.
   * 
   * @param jiraProjectKey JIRA project key for finished indexing
   * @param finishedOK set to <code>true</code> if indexing finished OK, <code>false</code> if finished due error
   */
  public abstract void reportIndexingFinished(String jiraProjectKey, boolean finishedOK);

}