package org.jboss.elasticsearch.river.jira;

import java.net.MalformedURLException;
import java.util.Map;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;

/**
 * JIRA River implementation class
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiver extends AbstractRiverComponent implements River {

  private final Client client;

  private final JIRA5RestClient jiraClient;

  private final String indexName;

  private volatile Thread thread;

  private volatile boolean closed = false;

  @SuppressWarnings({ "unchecked" })
  @Inject
  public JiraRiver(RiverName riverName, RiverSettings settings, Client client) throws MalformedURLException {
    super(riverName, settings);
    this.client = client;

    String url = null;

    if (settings.settings().containsKey("jira")) {
      Map<String, Object> jiraSettings = (Map<String, Object>) settings.settings().get("jira");
      url = XContentMapValues.nodeStringValue(jiraSettings.get("urlBase"), null);
      jiraClient = new JIRA5RestClient(url, XContentMapValues.nodeStringValue(jiraSettings.get("username"), null),
          XContentMapValues.nodeStringValue(jiraSettings.get("pwd"), null));
    } else {
      throw new SettingsException("jira configuration structure not found");
    }

    // TODO read JIRA http authentication from River Config

    logger.info("creating JIRA River for JIRA base URL  [{}]", url);

    if (settings.settings().containsKey("index")) {
      Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
      indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
    } else {
      indexName = riverName.name();
    }
  }

  @Override
  public void start() {
    logger.info("starting JIRA River");
    try {
      client.admin().indices().prepareCreate(indexName).execute().actionGet();
    } catch (Exception e) {
      if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
        // that's fine
      } else if (ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException) {
        // ok, not recovered yet..., lets start indexing and hope we recover by the first bulk
        // TODO: a smarter logic can be to register for cluster event listener here, and only start sampling when the
        // block is removed...
      } else {
        logger.warn("failed to create index [{}], disabling river...", e, indexName);
        return;
      }
    }

    thread = EsExecutors.daemonThreadFactory(settings.globalSettings(), "jira_slurper").newThread(
        new JIRAProjectIndexer());
    thread.start();
  }

  @Override
  public void close() {
    logger.info("closing JIRA River");
    closed = true;
    if (thread != null) {
      thread.interrupt();
    }
  }

  private class JIRAProjectIndexer implements Runnable {

    private JIRAProjectIndexer() {
    }

    @Override
    public void run() {
      try {
        // TODO process JIRA updated
        // TODO wait a while if nothing found
      } catch (Exception e) {
        if (closed) {
          return;
        }
        logger.error("failed to parse JIRA update", e);
      }
    }
  }

}
