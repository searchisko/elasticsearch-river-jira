package org.jboss.elasticsearch.river.jira;

/**
 * Interface for JIRA project indexer coordinator component.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IJIRAProjectIndexerCoordinator extends Runnable {

  /**
   * Report that indexing of JIRA project was finished. Used to coordinate parallel indexing of all projects.
   * Implementation of this method must be thread safe!
   * 
   * @param jiraProjectKey JIRA project key for finished indexing
   * @param finishedOK set to <code>true</code> if indexing finished OK, <code>false</code> if finished due error
   * @param fullUpdate set to <code>true</code> if reported indexing was full update, <code>false</code> on incremental
   *          update
   */
  public abstract void reportIndexingFinished(String jiraProjectKey, boolean finishedOK, boolean fullUpdate);

  /**
   * Force full reindex for given jira project.
   * 
   * @param projectKey to force reindex for
   * @throws Exception
   */
  void forceFullReindex(String projectKey) throws Exception;

}