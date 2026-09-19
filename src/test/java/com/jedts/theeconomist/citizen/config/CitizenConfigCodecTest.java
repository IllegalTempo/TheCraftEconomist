package com.jedts.theeconomist.citizen.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CitizenConfigCodecTest {
    @Test
    void normalizesWhitespaceAndCaseInsensitiveProfileDuplicates() {
        assertEquals(
                new CitizenConfig(
                        List.of("ExamplePlayer", "SecondPlayer"),
                        List.of("Amina", "Diego"),
                        List.of("Patel", "Garcia")
                ),
                CitizenConfigCodec.decode("""
                        {
                          "profileUsernames": [" ExamplePlayer ", "exampleplayer", "SecondPlayer"],
                          "givenNames": [" Amina ", "Amina", "Diego"],
                          "familyNames": ["Patel", " Garcia ", "Patel"]
                        }
                        """)
        );
    }

    @Test
    void allowsEmptyProfilePool() {
        CitizenConfig config = CitizenConfigCodec.decode("""
                {"profileUsernames": [], "givenNames": ["Amina"], "familyNames": ["Patel"]}
                """);

        assertEquals(List.of(), config.profileUsernames());
    }

    @Test
    void rejectsEmptyNameFieldWithFieldName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                CitizenConfigCodec.decode("""
                        {"profileUsernames": [], "givenNames": [], "familyNames": ["Patel"]}
                        """));

        assertEquals("givenNames must contain at least one usable name", error.getMessage());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> CitizenConfigCodec.decode("{"));
    }

    @Test
    void rejectsUnknownFields() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                CitizenConfigCodec.decode("""
                        {"profileUsernames": [], "givenNames": ["Amina"], "familyNames": ["Patel"], "unknown": true}
                        """));

        assertEquals("Unknown Citizen config field: unknown", error.getMessage());
    }
}
