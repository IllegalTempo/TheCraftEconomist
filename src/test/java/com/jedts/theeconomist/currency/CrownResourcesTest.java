package com.jedts.theeconomist.currency;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class CrownResourcesTest {
    @ParameterizedTest
    @EnumSource(CrownDenomination.class)
    void eachDenominationHasDefinitionModelAndTexture(CrownDenomination denomination) throws IOException {
        String path = denomination.path();

        JsonObject definition = readJson("/assets/theeconomist/items/" + path + ".json");
        assertEquals("theeconomist:item/" + path, definition.getAsJsonObject("model").get("model").getAsString());

        JsonObject model = readJson("/assets/theeconomist/models/item/" + path + ".json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("theeconomist:item/" + path, model.getAsJsonObject("textures").get("layer0").getAsString());

        try (InputStream stream = resource("/assets/theeconomist/textures/item/" + path + ".png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
    }

    @org.junit.jupiter.api.Test
    void currencyTagContainsEachDenominationExactlyOnce() throws IOException {
        JsonObject tag = readJson("/data/theeconomist/tags/item/currency.json");
        List<String> values = tag.getAsJsonArray("values").asList().stream().map(element -> element.getAsString()).toList();
        assertEquals(List.of(
                "theeconomist:copper_crown",
                "theeconomist:silver_crown",
                "theeconomist:gold_crown"
        ), values);
    }

    private static JsonObject readJson(String path) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = CrownResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource " + path);
        return stream;
    }
}
