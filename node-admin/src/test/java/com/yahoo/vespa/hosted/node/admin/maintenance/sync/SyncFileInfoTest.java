// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression.NONE;
import static com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo.Compression.ZSTD;
import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class SyncFileInfoTest {

    private static final FileSystem fileSystem = TestFileSystem.create();

    private static final URI nodeArchiveUri = URI.create("s3://vespa-data-bucket/vespa/music/main/h432a/");
    private static final Path accessLogPath1 = fileSystem.getPath("/opt/vespa/logs/qrs/access.log.20210211");
    private static final Path accessLogPath2 = fileSystem.getPath("/opt/vespa/logs/qrs/access.log.20210212.zst");
    private static final Path accessLogPath3 = fileSystem.getPath("/opt/vespa/logs/qrs/access-json.log.20210213.zst");
    private static final Path accessLogPath4 = fileSystem.getPath("/opt/vespa/logs/qrs/JsonAccessLog.default.20210214.zst");
    private static final Path connectionLogPath1 = fileSystem.getPath("/opt/vespa/logs/qrs/ConnectionLog.default.20210210");
    private static final Path connectionLogPath2 = fileSystem.getPath("/opt/vespa/logs/qrs/ConnectionLog.default.20210212.zst");
    private static final Path vespaLogPath1 = fileSystem.getPath("/opt/vespa/logs/vespa.log");
    private static final Path vespaLogPath2 = fileSystem.getPath("/opt/vespa/logs/vespa.log-2021-02-12");

    @Test
    public void log_files_test() {
        assertForLogFile(accessLogPath1, null, null);
        assertForLogFile(accessLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access.log.20210212.zst", NONE);
        assertForLogFile(accessLogPath3, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/access-json.log.20210213.zst", NONE);
        assertForLogFile(accessLogPath4, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/access/JsonAccessLog.default.20210214.zst", NONE);

        assertForLogFile(connectionLogPath1, null, null);
        assertForLogFile(connectionLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/connection/ConnectionLog.default.20210212.zst", NONE);

        assertForLogFile(vespaLogPath1, null, null);
        assertForLogFile(vespaLogPath2, "s3://vespa-data-bucket/vespa/music/main/h432a/logs/vespa/vespa.log-2021-02-12.zst", ZSTD);
    }

    private static void assertForLogFile(Path srcPath, String destination, SyncFileInfo.Compression compression) {
        Optional<SyncFileInfo> sfi = SyncFileInfo.forLogFile(nodeArchiveUri, srcPath);
        assertEquals(destination, sfi.map(SyncFileInfo::destination).map(URI::toString).orElse(null));
        assertEquals(compression, sfi.map(SyncFileInfo::uploadCompression).orElse(null));
    }
}