package org.urbansafe.priority.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 持久层代码生成工具的离线回归测试。 */
class PersistenceCodeGeneratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void tableCountRemainsTheSingleGenerationQuantitySource() throws Exception {
        Method method = PersistenceCodeGenerator.class.getDeclaredMethod("tableCount");
        method.setAccessible(true);

        assertEquals(7, method.invoke(null));

        Path repositoryRoot = Path.of("..", "..", "..").toAbsolutePath().normalize();
        String generateScript = Files.readString(
                repositoryRoot.resolve("scripts/backend/persistence-codegen/generate.sh"));
        String driftScript = Files.readString(
                repositoryRoot.resolve("scripts/backend/persistence-codegen/check-drift.sh"));

        assertTrue(generateScript.contains("table-count"));
        assertTrue(generateScript.contains("server/src/generated/java"));
        assertTrue(driftScript.contains("server/src/generated/java"));
        assertFalse(generateScript.contains("persistence/src/generated/java"));
        assertFalse(driftScript.contains("backend-java/persistence/"));
    }

    @Test
    void migrationDirectoryCanBeProvidedByWrapperScript() throws Exception {
        Method method = PersistenceCodeGenerator.class.getDeclaredMethod("defaultMigrationDirectory");
        method.setAccessible(true);
        String previous = System.getProperty("urban.safe.codegen.migrationDir");
        Path migration = temporaryDirectory.resolve("migration");
        try {
            System.setProperty("urban.safe.codegen.migrationDir", migration.toString());
            assertEquals(migration, method.invoke(null));
        } finally {
            if (previous == null) {
                System.clearProperty("urban.safe.codegen.migrationDir");
            } else {
                System.setProperty("urban.safe.codegen.migrationDir", previous);
            }
        }
    }

    @Test
    void repositoryMigrationVersionUsesNumericOrdering() throws Exception {
        Files.createFile(temporaryDirectory.resolve("V9__baseline.sql"));
        Files.createFile(temporaryDirectory.resolve("V10__latest.sql"));
        Files.createFile(temporaryDirectory.resolve("README.md"));

        Method method = PersistenceCodeGenerator.class.getDeclaredMethod(
                "repositoryLatestMigrationVersion", Path.class);
        method.setAccessible(true);

        assertEquals("10", method.invoke(null, temporaryDirectory));
    }

    @Test
    void unknownPostgresqlTypeIsRejectedWithColumnContext() throws Exception {
        Method method = PersistenceCodeGenerator.class.getDeclaredMethod(
                "column",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(
                        null,
                        "core",
                        "building",
                        "location",
                        "USER-DEFINED",
                        "geometry"));

        String message = exception.getCause().getMessage();
        assertTrue(message.contains("core.building.location"));
        assertTrue(message.contains("geometry"));
    }
}
