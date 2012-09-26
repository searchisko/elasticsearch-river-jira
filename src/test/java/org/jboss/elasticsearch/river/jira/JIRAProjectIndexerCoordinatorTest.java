/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.jboss.elasticsearch.river.jira.testtools.MockThread;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JIRAProjectIndexerCoordinator}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexerCoordinatorTest {

  @Test
  public void projectIndexUpdateNecessary() throws Exception {
    int indexUpdatePeriod = 60 * 1000;

    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null,
        indexUpdatePeriod, 2, -1);

    // case - update necessary- no date of last update stored
    {
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(null);
      Assert.assertTrue(tested.projectIndexUpdateNecessary("ORG"));
    }

    // case - update necessary - date of last update stored and is older than index update period
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexUpdatePeriod - 100));
      Assert.assertTrue(tested.projectIndexUpdateNecessary("ORG"));
    }

    // case - no update necessary - date of last update stored and is newer than index update period
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexUpdatePeriod + 1000));
      Assert.assertFalse(tested.projectIndexUpdateNecessary("ORG"));
    }
  }

  @Test
  public void projectIndexFullUpdateNecessary() throws Exception {
    int indexFullUpdatePeriod = 60 * 1000;

    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 1000, 2, -1);
    tested.setIndexFullUpdatePeriod(0);

    // case - full update disabled, no force
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      Assert.assertFalse(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);
      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    tested.setIndexFullUpdatePeriod(indexFullUpdatePeriod);
    // case - full update necessary - no date of last full update stored, no force
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      Assert.assertTrue(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE);
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);

      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    // case - full update necessary - date of last full update stored and is older than index full update period, no
    // force
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexFullUpdatePeriod - 100));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      Assert.assertTrue(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE);
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);
      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    // case - no full update necessary - date of last full update stored and is newer than index full update period, no
    // force
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexFullUpdatePeriod + 1000));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(null);

      Assert.assertFalse(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE);
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);

      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }
  }

  @Test
  public void projectIndexFullUpdateNecessary_forced() throws Exception {
    int indexFullUpdatePeriod = 60 * 1000;

    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 1000, 2, -1);
    tested.setIndexFullUpdatePeriod(0);

    // case - full update disabled, but forced
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(new Date());
      Assert.assertTrue(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);
      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    tested.setIndexFullUpdatePeriod(indexFullUpdatePeriod);
    // case - full update necessary - no date of last full update stored, but forced
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(new Date());
      Assert.assertTrue(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);

      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    // case - full update necessary - date of last full update stored and is older than index full update period, but
    // forced
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexFullUpdatePeriod - 100));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(new Date());
      Assert.assertTrue(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);
      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    // case - no full update necessary - date of last full update stored and is newer than index full update period, but
    // forced
    {
      reset(esIntegrationMock);
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexFullUpdatePeriod + 1000));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE)).thenReturn(new Date());

      Assert.assertTrue(tested.projectIndexFullUpdateNecessary("ORG"));
      verify(esIntegrationMock).readDatetimeValue("ORG",
          JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE);

      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }
  }

  @Test
  public void fillProjectKeysToIndexQueue() throws Exception {
    int indexUpdatePeriod = 60 * 1000;
    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null,
        indexUpdatePeriod, 2, -1);
    Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());

    // case - no any project available (both null or empty list)
    {
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(null);
      tested.fillProjectKeysToIndexQueue();
      Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());
      verify(esIntegrationMock, times(0)).readDatetimeValue(Mockito.any(String.class), Mockito.anyString());
      reset(esIntegrationMock);
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(new ArrayList<String>());
      tested.fillProjectKeysToIndexQueue();
      Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());
      verify(esIntegrationMock, times(0)).readDatetimeValue(Mockito.any(String.class), Mockito.anyString());
    }

    // case - some projects available
    {
      reset(esIntegrationMock);
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("AAA",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexUpdatePeriod - 100));
      when(
          esIntegrationMock.readDatetimeValue("BBB",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexUpdatePeriod + 1000));
      when(
          esIntegrationMock.readDatetimeValue("CCC",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexUpdatePeriod - 100));
      when(
          esIntegrationMock.readDatetimeValue("DDD",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(
          new Date(System.currentTimeMillis() - indexUpdatePeriod - 100));

      tested.fillProjectKeysToIndexQueue();
      Assert.assertFalse(tested.projectKeysToIndexQueue.isEmpty());
      Assert.assertEquals(4, tested.projectKeysToIndexQueue.size());
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("ORG"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("AAA"));
      Assert.assertFalse(tested.projectKeysToIndexQueue.contains("BBB"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("CCC"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("DDD"));
    }

    // case - some project available for index update, but in processing already, so do not schedule it for processing
    // now
    {
      reset(esIntegrationMock);
      tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, indexUpdatePeriod, 2, -1);
      tested.projectIndexers.put("ORG", new Thread());
      when(
          esIntegrationMock.readDatetimeValue(Mockito.eq(Mockito.anyString()),
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(null);

      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      tested.fillProjectKeysToIndexQueue();
      Assert.assertFalse(tested.projectKeysToIndexQueue.isEmpty());
      Assert.assertEquals(4, tested.projectKeysToIndexQueue.size());
      Assert.assertFalse(tested.projectKeysToIndexQueue.contains("ORG"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("AAA"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("BBB"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("CCC"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("DDD"));
    }

    // case - some project available for index update, but in queue already, so do not schedule it for processing now
    {
      reset(esIntegrationMock);
      tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, indexUpdatePeriod, 2, -1);
      tested.projectKeysToIndexQueue.add("ORG");
      when(
          esIntegrationMock.readDatetimeValue(Mockito.eq(Mockito.anyString()),
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(null);

      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      tested.fillProjectKeysToIndexQueue();
      Assert.assertFalse(tested.projectKeysToIndexQueue.isEmpty());
      Assert.assertEquals(5, tested.projectKeysToIndexQueue.size());
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("ORG"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("AAA"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("BBB"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("CCC"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("DDD"));
    }

    // case - exception when interrupted from ES server
    {
      reset(esIntegrationMock);
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      when(esIntegrationMock.isClosed()).thenReturn(true);
      try {
        tested.fillProjectKeysToIndexQueue();
        Assert.fail("No InterruptedException thrown");
      } catch (InterruptedException e) {
        // OK
      }
    }
  }

  @Test
  public void startIndexers() throws Exception {

    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 100000, 2,
        -1);
    Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());

    // case - nothing to start
    {
      tested.startIndexers();
      Assert.assertTrue(tested.projectIndexers.isEmpty());
      verify(esIntegrationMock, times(0)).acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class));
    }

    // case - all indexer slots full, do not start new ones
    {
      reset(esIntegrationMock);
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      tested.projectIndexers.put("JJ", new Thread());
      tested.projectIndexers.put("II", new Thread());
      tested.startIndexers();
      Assert.assertEquals(2, tested.projectIndexers.size());
      Assert.assertEquals(5, tested.projectKeysToIndexQueue.size());
      verify(esIntegrationMock, times(0)).acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class));
      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }

    // case - one indexer slot empty, start new one
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectIndexers.put("II", new Thread());
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());
      tested.startIndexers();
      Assert.assertEquals(2, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("ORG"));
      Assert.assertTrue(((MockThread) tested.projectIndexers.get("ORG")).wasStarted);
      Assert.assertEquals(4, tested.projectKeysToIndexQueue.size());
      Assert.assertFalse(tested.projectKeysToIndexQueue.contains("ORG"));
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).storeDatetimeValue(Mockito.eq("ORG"),
          Mockito.eq(JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE),
          Mockito.any(Date.class), Mockito.eq((BulkRequestBuilder) null));
    }

    // case - two slots empty and more project available, start two indexers
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG,AAA,BBB,CCC,DDD"));
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_AAA"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());
      tested.startIndexers();
      Assert.assertEquals(2, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("ORG"));
      Assert.assertTrue(tested.projectIndexers.containsKey("AAA"));
      Assert.assertTrue(((MockThread) tested.projectIndexers.get("ORG")).wasStarted);
      Assert.assertTrue(((MockThread) tested.projectIndexers.get("AAA")).wasStarted);
      Assert.assertEquals(3, tested.projectKeysToIndexQueue.size());
      Assert.assertFalse(tested.projectKeysToIndexQueue.contains("ORG"));
      Assert.assertFalse(tested.projectKeysToIndexQueue.contains("AAA"));
      verify(esIntegrationMock, times(2)).acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_AAA"),
          Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).storeDatetimeValue(Mockito.eq("ORG"),
          Mockito.eq(JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE),
          Mockito.any(Date.class), Mockito.eq((BulkRequestBuilder) null));
      verify(esIntegrationMock, times(1)).storeDatetimeValue(Mockito.eq("AAA"),
          Mockito.eq(JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE),
          Mockito.any(Date.class), Mockito.eq((BulkRequestBuilder) null));
    }

    // case - two slots empty but only one project available, start it
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG"));
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());
      tested.startIndexers();
      Assert.assertEquals(1, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("ORG"));
      Assert.assertTrue(((MockThread) tested.projectIndexers.get("ORG")).wasStarted);
      Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).storeDatetimeValue(Mockito.eq("ORG"),
          Mockito.eq(JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE),
          Mockito.any(Date.class), Mockito.eq((BulkRequestBuilder) null));
    }

    // case - exception when interrupted from ES server
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG"));
      when(esIntegrationMock.isClosed()).thenReturn(true);
      try {
        tested.startIndexers();
        Assert.fail("No InterruptedException thrown");
      } catch (InterruptedException e) {
        // OK
      }
    }
  }

  @Test
  public void startIndexers_reserveIndexingThreadSlotForIncremental() throws Exception {

    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 100000, 2,
        -1);
    Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());

    // case - only one thread configured, so use it for full reindex too!!
    {
      reset(esIntegrationMock);
      tested.indexFullUpdatePeriod = 1000;
      tested.maxIndexingThreads = 1;
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG,AAA"));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("AAA",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);

      when(esIntegrationMock.acquireIndexingThread(Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(
          new MockThread());

      tested.startIndexers();
      Assert.assertEquals(1, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("ORG"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("AAA"));
      Assert.assertEquals(1, tested.projectKeysToIndexQueue.size());
    }

    // case - one slot empty from two, but only full reindex requested, so let slot empty
    {
      reset(esIntegrationMock);
      tested.indexFullUpdatePeriod = 1000;
      tested.maxIndexingThreads = 2;
      tested.projectIndexers.clear();
      tested.projectIndexers.put("BBB", new Thread());

      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG"));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(esIntegrationMock.acquireIndexingThread(Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(
          new MockThread());

      tested.startIndexers();
      Assert.assertEquals(1, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("BBB"));
      Assert.assertFalse(tested.projectIndexers.containsKey("ORG"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("ORG"));
      Assert.assertEquals(1, tested.projectKeysToIndexQueue.size());
    }

    // case - one slot empty from two, so use it for incremental update for second project in queue
    {
      reset(esIntegrationMock);
      tested.indexFullUpdatePeriod = 1000;
      tested.maxIndexingThreads = 2;
      tested.projectIndexers.clear();
      tested.projectIndexers.put("BBB", new Thread());

      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG,AAA"));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("AAA",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(new Date());
      when(esIntegrationMock.acquireIndexingThread(Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(
          new MockThread());

      tested.startIndexers();
      Assert.assertEquals(2, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("AAA"));
      Assert.assertTrue(tested.projectIndexers.containsKey("BBB"));
      Assert.assertFalse(tested.projectIndexers.containsKey("ORG"));
      // check first project stayed in queue!
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("ORG"));
      Assert.assertEquals(1, tested.projectKeysToIndexQueue.size());
    }

    // case - two slots empty from three, so use first for full update but second for incremental update which is third
    // in queue
    {
      reset(esIntegrationMock);
      tested.indexFullUpdatePeriod = 1000;
      tested.maxIndexingThreads = 3;
      tested.projectIndexers.clear();
      tested.projectIndexers.put("BBB", new Thread());

      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.addAll(Utils.parseCsvString("ORG,ORG2,AAA,ORG3"));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("ORG2",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("ORG3",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(null);
      when(
          esIntegrationMock.readDatetimeValue("AAA",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE)).thenReturn(new Date());
      when(esIntegrationMock.acquireIndexingThread(Mockito.anyString(), Mockito.any(Runnable.class))).thenReturn(
          new MockThread());

      tested.startIndexers();
      Assert.assertEquals(3, tested.projectIndexers.size());
      Assert.assertTrue(tested.projectIndexers.containsKey("BBB"));
      Assert.assertTrue(tested.projectIndexers.containsKey("ORG"));
      Assert.assertTrue(tested.projectIndexers.containsKey("AAA"));
      Assert.assertFalse(tested.projectIndexers.containsKey("ORG2"));
      Assert.assertFalse(tested.projectIndexers.containsKey("ORG3"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("ORG2"));
      Assert.assertTrue(tested.projectKeysToIndexQueue.contains("ORG3"));
      Assert.assertEquals(2, tested.projectKeysToIndexQueue.size());
    }

  }

  @Test
  public void run() throws Exception {
    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 100000, 2,
        -1);
    when(esIntegrationMock.acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class))).thenReturn(
        new MockThread());

    // case - close flag is set, so interrupt all indexers and free them
    {
      MockThread mt1 = new MockThread();
      MockThread mt2 = new MockThread();
      tested.projectIndexers.put("ORG", mt1);
      tested.projectIndexers.put("AAA", mt2);
      when(esIntegrationMock.isClosed()).thenReturn(true);

      tested.run();
      Assert.assertTrue(tested.projectIndexers.isEmpty());
      Assert.assertTrue(mt1.interruptWasCalled);
      Assert.assertTrue(mt2.interruptWasCalled);
    }

    // case - InterruptedException is thrown, so interrupt all indexers
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      MockThread mt1 = new MockThread();
      MockThread mt2 = new MockThread();
      tested.projectIndexers.put("ORG", mt1);
      tested.projectIndexers.put("AAA", mt2);
      when(esIntegrationMock.isClosed()).thenReturn(false);
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenThrow(new InterruptedException());

      tested.run();
      Assert.assertTrue(tested.projectIndexers.isEmpty());
      Assert.assertTrue(mt1.interruptWasCalled);
      Assert.assertTrue(mt2.interruptWasCalled);
    }

    // case - closed, so try to interrupt all indexers but not exception if empty
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      when(esIntegrationMock.isClosed()).thenReturn(true);

      tested.run();
      Assert.assertTrue(tested.projectIndexers.isEmpty());
    }
  }

  @Test
  public void processLoopTask() throws Exception {
    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 100000, 2,
        -1);
    when(esIntegrationMock.acquireIndexingThread(Mockito.any(String.class), Mockito.any(Runnable.class))).thenReturn(
        new MockThread());

    // case - projectKeysToIndexQueue is empty so call fillProjectKeysToIndexQueue() and then call startIndexers()
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG"));
      when(
          esIntegrationMock.readDatetimeValue("ORG",
              JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE)).thenReturn(null);
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());

      tested.processLoopTask();
      Assert.assertEquals(1, tested.projectIndexers.size());
      verify(esIntegrationMock, times(1)).getAllIndexedProjectsKeys();
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      Assert.assertEquals(JIRAProjectIndexerCoordinator.COORDINATOR_THREAD_WAITS_QUICK, tested.coordinatorThreadWaits);
    }

    // case - projectKeysToIndexQueue is not empty, no fillProjectKeysToIndexQueue() is called because called in near
    // history, but startIndexers is called
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.add("ORG");
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG"));
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());

      tested.processLoopTask();
      Assert.assertEquals(1, tested.projectIndexers.size());
      verify(esIntegrationMock, times(0)).getAllIndexedProjectsKeys();
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      Assert.assertEquals(JIRAProjectIndexerCoordinator.COORDINATOR_THREAD_WAITS_QUICK, tested.coordinatorThreadWaits);
    }

    // case - projectKeysToIndexQueue is not empty, but fillProjectKeysToIndexQueue() is called because called long ago,
    // then startIndexers is called
    {
      reset(esIntegrationMock);
      tested.lastQueueFillTime = System.currentTimeMillis()
          - JIRAProjectIndexerCoordinator.COORDINATOR_THREAD_WAITS_SLOW - 1;
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      tested.projectKeysToIndexQueue.add("ORG");
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(Utils.parseCsvString("ORG,AAA"));
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());
      when(esIntegrationMock.acquireIndexingThread(Mockito.eq("jira_river_indexer_AAA"), Mockito.any(Runnable.class)))
          .thenReturn(new MockThread());

      tested.processLoopTask();
      Assert.assertEquals(2, tested.projectIndexers.size());
      verify(esIntegrationMock, times(1)).getAllIndexedProjectsKeys();
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      verify(esIntegrationMock, times(1)).acquireIndexingThread(Mockito.eq("jira_river_indexer_AAA"),
          Mockito.any(Runnable.class));
      Assert.assertEquals(JIRAProjectIndexerCoordinator.COORDINATOR_THREAD_WAITS_QUICK, tested.coordinatorThreadWaits);
    }

    // case - projectKeysToIndexQueue is empty so call fillProjectKeysToIndexQueue() but still empty so slow down and
    // dont call startIndexers()
    {
      reset(esIntegrationMock);
      tested.projectIndexers.clear();
      tested.projectKeysToIndexQueue.clear();
      when(esIntegrationMock.getAllIndexedProjectsKeys()).thenReturn(null);

      tested.processLoopTask();
      verify(esIntegrationMock, times(1)).getAllIndexedProjectsKeys();
      Assert.assertTrue(tested.projectKeysToIndexQueue.isEmpty());
      verify(esIntegrationMock, times(0)).acquireIndexingThread(Mockito.eq("jira_river_indexer_ORG"),
          Mockito.any(Runnable.class));
      Assert.assertEquals(JIRAProjectIndexerCoordinator.COORDINATOR_THREAD_WAITS_SLOW, tested.coordinatorThreadWaits);
    }
  }

  @Test
  public void reportIndexingFinished() throws Exception {
    IESIntegration esIntegrationMock = mock(IESIntegration.class);
    JIRAProjectIndexerCoordinator tested = new JIRAProjectIndexerCoordinator(null, esIntegrationMock, null, 10, 2, -1);
    tested.projectIndexers.put("ORG", new Thread());
    tested.projectIndexers.put("AAA", new Thread());

    // case - incremental indexing with success
    {
      tested.reportIndexingFinished("ORG", true, false);
      Assert.assertEquals(1, tested.projectIndexers.size());
      Assert.assertFalse(tested.projectIndexers.containsKey("ORG"));
      // no full reindex date stored
      Mockito.verifyZeroInteractions(esIntegrationMock);
    }

    // case - full indexing without success
    {
      tested.reportIndexingFinished("AAA", false, true);
      Assert.assertEquals(0, tested.projectIndexers.size());
      // no full reindex date stored
      Mockito.verifyZeroInteractions(esIntegrationMock);
    }

    // case - full indexing with success
    {
      tested.projectIndexers.put("AAA", new Thread());
      tested.reportIndexingFinished("AAA", true, true);
      Assert.assertEquals(0, tested.projectIndexers.size());
      verify(esIntegrationMock).storeDatetimeValue(Mockito.eq("AAA"),
          Mockito.eq(JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE),
          (Date) Mockito.any(), (BulkRequestBuilder) Mockito.isNull());
      verify(esIntegrationMock).deleteDatetimeValue(Mockito.eq("AAA"),
          Mockito.eq(JIRAProjectIndexerCoordinator.STORE_PROPERTYNAME_FORCE_INDEX_FULL_UPDATE_DATE));
      Mockito.verifyNoMoreInteractions(esIntegrationMock);
    }
  }

}
