// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexer.Cluster;
import ai.vespa.reindexing.ReindexingCurator.ReindexingLockException;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Runs in all cluster controller containers, and progresses reindexngg efforts.
 * Work is only done by one container at a time, by requiring a shared ZooKeeper lock to be held while visiting.
 * Whichever maintainer gets the lock holds it until all reindexing is done, or until shutdown.
 *
 * @author jonmv
 */
public class ReindexingMaintainer extends AbstractComponent {

    private static final Logger log = Logger.getLogger(Reindexing.class.getName());

    private final Reindexer reindexer;
    private final ScheduledExecutorService executor;

    // VespaZooKeeperServer dependency to ensure the ZK cluster is running.
    @Inject
    public ReindexingMaintainer(VespaZooKeeperServer zooKeeperServer, DocumentAccess access, ZookeepersConfig zookeepersConfig,
                                ClusterListConfig clusterListConfig, AllClustersBucketSpacesConfig allClustersBucketSpacesConfig,
                                ReindexingConfig reindexingConfig, DocumentmanagerConfig documentmanagerConfig) {
        this(Clock.systemUTC(), access, zookeepersConfig, clusterListConfig, allClustersBucketSpacesConfig, reindexingConfig, documentmanagerConfig);
    }

    ReindexingMaintainer(Clock clock, DocumentAccess access, ZookeepersConfig zookeepersConfig,
                         ClusterListConfig clusterListConfig, AllClustersBucketSpacesConfig allClustersBucketSpacesConfig,
                         ReindexingConfig reindexingConfig, DocumentmanagerConfig documentmanagerConfig) {
        DocumentTypeManager manager = new DocumentTypeManager(documentmanagerConfig);
        this.reindexer = new Reindexer(parseCluster(reindexingConfig.clusterName(), clusterListConfig, allClustersBucketSpacesConfig, manager),
                                       parseReady(reindexingConfig, manager),
                                       new ReindexingCurator(Curator.create(zookeepersConfig.zookeeperserverlist()), manager),
                                       access,
                                       clock);
        this.executor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("reindexer-"));
        if (reindexingConfig.enabled())
            scheduleStaggered(this::maintain, executor, Duration.ofMinutes(1), clock.instant(),
                              HostName.getLocalhost(), zookeepersConfig.zookeeperserverlist());
    }

    private void maintain() {
        try {
            reindexer.reindex();
        }
        catch (ReindexingLockException e) {
            // Some other container is handling the reindexing at this moment, which is fine.
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Exception when reindexing", e);
        }
    }

    @Override
    public void deconstruct() {
        try {
            executor.shutdownNow();
            if ( ! executor.awaitTermination(20, TimeUnit.SECONDS))
                log.log(Level.SEVERE, "Failed to shut down reindexer within timeout");
        }
        catch (InterruptedException e) {
            log.log(Level.SEVERE, "Interrupted while waiting for reindexer to shut down");
            Thread.currentThread().interrupt();
        }

    }

    static Map<DocumentType, Instant> parseReady(ReindexingConfig config, DocumentTypeManager manager) {
        return config.status().entrySet().stream()
                     .collect(toUnmodifiableMap(typeStatus -> manager.getDocumentType(typeStatus.getKey()),
                                                typeStatus -> Instant.ofEpochMilli(typeStatus.getValue().readyAtMillis())));
    }

    /** Schedules the given task with the given interval (across all containers in this ZK cluster). */
    static void scheduleStaggered(Runnable task, ScheduledExecutorService executor,
                                  Duration interval, Instant now,
                                  String hostname, String clusterHostnames) {
        long delayMillis = 0;
        long intervalMillis = interval.toMillis();
        List<String> hostnames = Stream.of(clusterHostnames.split(","))
                                       .map(hostPort -> hostPort.split(":")[0])
                                       .collect(toList());
        if (hostnames.contains(hostname)) {
            long offset = hostnames.indexOf(hostname) * intervalMillis;
            intervalMillis *= hostnames.size();
            delayMillis = Math.floorMod(offset - now.toEpochMilli(), interval.toMillis());
        }
        executor.scheduleAtFixedRate(task, delayMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private static Cluster parseCluster(String name, ClusterListConfig clusters, AllClustersBucketSpacesConfig buckets,
                                        DocumentTypeManager manager) {
        return clusters.storage().stream()
                       .filter(storage -> storage.name().equals(name))
                       .map(storage -> new Cluster(name,
                                                   storage.configid(),
                                                   buckets.cluster(name)
                                                          .documentType().entrySet().stream()
                                                          .collect(toMap(entry -> manager.getDocumentType(entry.getKey()),
                                                                         entry -> entry.getValue().bucketSpace()))))
                       .findAny()
                       .orElseThrow(() -> new IllegalStateException("This cluster (" + name + ") not among the list of clusters"));
    }

}
