/*
 * Copyright (c) 2015 The original author or authors
 * ---------------------------------
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.shareddata.IgniteClusteredSharedCounterTest;
import org.apache.ignite.Ignite;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.binary.BinaryEnumCache;
import org.apache.ignite.internal.util.GridClassLoaderCache;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.util.typedef.G;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.marshaller.MarshallerExclusions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author Lukas Prettenthaler
 */
public class Lifecycle {
  private static final Logger log = LoggerFactory.getLogger(IgniteClusteredSharedCounterTest.class);

  public static void closeClustered(List<Vertx> clustered) throws Exception {
    CountDownLatch latch = new CountDownLatch(clustered.size());
    for (Vertx clusteredVertx : clustered) {
      clusteredVertx.close(ar -> {
        if (ar.failed()) {
          log.error("Failed to shutdown vert.x", ar.cause());
        }
        latch.countDown();
      });
    }
    assertTrue(latch.await(180, TimeUnit.SECONDS));

    Thread.sleep(200L);

    Collection<Ignite> list = new ArrayList<>(G.allGrids());

    for (Ignite g : list) {
      stopGrid(g.name());
    }

    List<Ignite> nodes = G.allGrids();

    assert nodes.isEmpty() : nodes;

    GridClassLoaderCache.clear();
    U.clearClassCache();
    MarshallerExclusions.clearCache();
    BinaryEnumCache.clear();
  }

  private static void stopGrid(String igniteInstanceName) {
    try {
      IgniteEx ignite = (IgniteEx) G.ignite(igniteInstanceName);

      assert ignite != null : "Ignite returned null grid for name: " + igniteInstanceName;

      UUID id = ignite.context().localNodeId();
      log.info(">>> Stopping grid [name=" + ignite.name() + ", id=" + id + ']');
      IgniteUtils.setCurrentIgniteName(igniteInstanceName);

      try {
        G.stop(igniteInstanceName, true);
      } finally {
        IgniteUtils.setCurrentIgniteName(null);
      }
    } catch (IllegalStateException ignored) {
      // Ignore error if grid already stopped.
    } catch (Throwable e) {
      log.error("Failed to stop grid [igniteInstanceName=" + igniteInstanceName + ']', e);
    }
  }
}
