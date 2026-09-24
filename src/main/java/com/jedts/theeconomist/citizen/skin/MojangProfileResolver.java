package com.jedts.theeconomist.citizen.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Resolves public Java Edition profile skins through Mojang's profile services. */
public final class MojangProfileResolver implements ProfileResolver {
    private static final String PROFILE_LOOKUP = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public CompletionStage<Optional<ResolvedProfile>> resolve(String username) {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return getJson(URI.create(PROFILE_LOOKUP + encodedName)).thenCompose(profile -> {
            if (profile.isEmpty()) return CompletableFuture.completedFuture(Optional.<ResolvedProfile>empty());
            try {
                String id = profile.get().get("id").getAsString();
                UUID profileId = UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                return getJson(URI.create(SESSION_PROFILE + id + "?unsigned=false"))
                        .thenApply(session -> session.flatMap(value -> parseProfile(profileId, value)));
            } catch (RuntimeException exception) {
                return CompletableFuture.completedFuture(Optional.<ResolvedProfile>empty());
            }
        }).exceptionally(error -> Optional.<ResolvedProfile>empty());
    }

    private CompletableFuture<Optional<JsonObject>> getJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("User-Agent", "TheEconomist-Minecraft-Mod")
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200) return Optional.empty();
                    try {
                        JsonElement json = JsonParser.parseString(response.body());
                        return json.isJsonObject() ? Optional.of(json.getAsJsonObject()) : Optional.empty();
                    } catch (RuntimeException exception) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<ResolvedProfile> parseProfile(UUID profileId, JsonObject profile) {
        if (!profile.has("properties") || !profile.get("properties").isJsonArray()) return Optional.empty();
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            if (!element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !property.get("name").getAsString().equals("textures")
                    || !property.has("value") || !property.has("signature")) continue;
            String value = property.get("value").getAsString();
            String signature = property.get("signature").getAsString();
            CitizenModelType model = modelType(value);
            return Optional.of(new ResolvedProfile(profileId, value, signature, model));
        }
        return Optional.empty();
    }

    private CitizenModelType modelType(String textureValue) {
        try {
            JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(textureValue),
                    StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures.getAsJsonObject("SKIN");
            String model = skin != null && skin.has("metadata")
                    ? skin.getAsJsonObject("metadata").get("model").getAsString() : "";
            return model.equalsIgnoreCase("slim") ? CitizenModelType.SLIM : CitizenModelType.WIDE;
        } catch (RuntimeException exception) {
            return CitizenModelType.WIDE;
        }
    }
}
