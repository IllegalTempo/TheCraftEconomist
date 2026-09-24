package com.jedts.theeconomist.citizen.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenConfigMigrationTest {
    @Test
    void upgradesExistingConfigWithScoringDefaults() throws IOException {
        Path directory = Files.createTempDirectory("citizen-config-upgrade");
        Path file = directory.resolve("citizens.json");
        try {
            Files.writeString(file, "{\"profileUsernames\":[],\"givenNames\":[\"Amina\"],\"familyNames\":[\"Patel\"]}");
            CitizenConfigService service = CitizenConfigService.load(file);
            assertEquals(20, service.current().decisionScoring().costPenalty());
            assertTrue(Files.readString(file).contains("\"decisionScoring\""));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
