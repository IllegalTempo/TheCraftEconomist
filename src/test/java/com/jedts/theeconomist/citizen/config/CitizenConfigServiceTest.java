package com.jedts.theeconomist.citizen.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenConfigServiceTest {
    @Test
    void failedReloadKeepsLastValidConfiguration(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("citizens.json");
        Files.writeString(file, validConfig("Amina", "Patel"));
        CitizenConfigService service = CitizenConfigService.load(file);

        Files.writeString(file, "{\"givenNames\": [], \"familyNames\": [\"Garcia\"], \"profileUsernames\": []}");
        ConfigReloadResult result = service.reload();

        assertFalse(result.success());
        assertTrue(result.message().contains("givenNames"));
        assertEquals(List.of("Amina"), service.current().givenNames());
    }

    @Test
    void missingStartupFileWritesDefaults(@TempDir Path directory) {
        Path file = directory.resolve("config").resolve("theeconomist").resolve("citizens.json");

        CitizenConfigService service = CitizenConfigService.load(file);

        assertTrue(Files.exists(file));
        assertEquals(CitizenConfig.defaults(), service.current());
    }

    @Test
    void malformedStartupUsesDefaultsAndReportsDiagnostic(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("citizens.json");
        Files.writeString(file, "{");

        CitizenConfigService service = CitizenConfigService.load(file);

        assertEquals(CitizenConfig.defaults(), service.current());
        assertTrue(service.startupMessage().contains("Invalid Citizen configuration"));
    }

    @Test
    void successfulReloadReplacesSnapshot(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("citizens.json");
        Files.writeString(file, validConfig("Amina", "Patel"));
        CitizenConfigService service = CitizenConfigService.load(file);
        Files.writeString(file, validConfig("Diego", "Garcia"));

        ConfigReloadResult result = service.reload();

        assertTrue(result.success());
        assertEquals(List.of("Diego"), service.current().givenNames());
    }

    private static String validConfig(String given, String family) {
        return "{\"profileUsernames\": [], \"givenNames\": [\"" + given + "\"], \"familyNames\": [\"" + family + "\"]}";
    }
}
