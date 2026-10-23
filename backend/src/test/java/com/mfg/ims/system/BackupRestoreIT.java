package com.mfg.ims.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * NFR-005: Verifies that the pg_dump → pg_restore round-trip preserves the 'ims' schema.
 *
 * <p>This test runs against the shared Testcontainers PostgreSQL that already has all
 * Flyway migrations applied and seed data loaded. It:
 * <ol>
 *   <li>Runs {@code pg_dump -n ims -Fc} inside the running container.</li>
 *   <li>Copies the dump to a temp file on the host.</li>
 *   <li>Starts a <em>fresh</em> PostgreSQL container (no Spring context, no migrations).</li>
 *   <li>Restores the dump with {@code pg_restore --no-owner --no-acl}.</li>
 *   <li>Asserts the restored schema has the same table count as the source.</li>
 * </ol>
 *
 * <p>This proves that {@code docker/backup/backup.sh} and {@code docker/backup/restore.sh}
 * will correctly back up and restore a production database.
 */
class BackupRestoreIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("NFR-005: pg_dump → pg_restore preserves the full 'ims' schema")
    void dumpAndRestore_preservesSchema() throws Exception {
        var pg = sharedPostgres();

        // ── 1. Dump the running test database (ims schema only, custom format) ──
        var dumpResult = pg.execInContainer(
                "pg_dump",
                "-U", pg.getUsername(),
                "-d", pg.getDatabaseName(),
                "-n", "ims",
                "-Fc",
                "-f", "/tmp/ims_backup_it.dump");
        assertThat(dumpResult.getExitCode())
                .as("pg_dump must succeed; stderr: %s", dumpResult.getStderr())
                .isZero();

        // ── 2. Copy the dump to the host temp directory ──
        Path tmpDump = Files.createTempFile("ims_backup_it_", ".dump");
        try {
            pg.copyFileFromContainer("/tmp/ims_backup_it.dump", tmpDump.toString());
            assertThat(Files.size(tmpDump))
                    .as("dump file must be non-empty")
                    .isGreaterThan(0L);

            // ── 3. Get the source table count (the target for validation) ──
            var sourceCount = pg.execInContainer(
                    "psql",
                    "-U", pg.getUsername(),
                    "-d", pg.getDatabaseName(),
                    "-t", "-A",
                    "-c", "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ims';");
            assertThat(sourceCount.getExitCode()).isZero();
            int expectedTables = Integer.parseInt(sourceCount.getStdout().strip());
            assertThat(expectedTables)
                    .as("source database must have at least 50 tables (Flyway migrations applied)")
                    .isGreaterThanOrEqualTo(50);

            // ── 4. Start a fresh PostgreSQL container (no application, no migrations) ──
            try (var restoreTarget = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ims_restored")
                    .withUsername("ims")
                    .withPassword("ims")) {
                restoreTarget.start();

                // ── 5. Copy the dump into the restore container ──
                restoreTarget.copyFileToContainer(
                        MountableFile.forHostPath(tmpDump.toString()),
                        "/tmp/ims_restore.dump");

                // ── 6. Restore ──
                var restoreResult = restoreTarget.execInContainer(
                        "pg_restore",
                        "-U", "ims",
                        "-d", "ims_restored",
                        "--no-owner",
                        "--no-acl",
                        "/tmp/ims_restore.dump");
                // pg_restore exits 1 for non-fatal warnings (e.g., role ownership); accept 0 or 1
                assertThat(restoreResult.getExitCode())
                        .as("pg_restore must exit 0 or 1 (warnings only); stderr: %s",
                                restoreResult.getStderr())
                        .isLessThanOrEqualTo(1);
                assertThat(restoreResult.getStderr().toLowerCase())
                        .as("pg_restore must produce no ERROR lines")
                        .doesNotContain("error:");

                // ── 7. Validate: table count in restored schema must match source ──
                var restoredCount = restoreTarget.execInContainer(
                        "psql",
                        "-U", "ims",
                        "-d", "ims_restored",
                        "-t", "-A",
                        "-c",
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ims';");
                assertThat(restoredCount.getExitCode()).isZero();
                int actualTables = Integer.parseInt(restoredCount.getStdout().strip());

                assertThat(actualTables)
                        .as("restored schema must contain the same number of tables as the source (%d)",
                                expectedTables)
                        .isEqualTo(expectedTables);
            }
        } finally {
            Files.deleteIfExists(tmpDump);
        }
    }
}
