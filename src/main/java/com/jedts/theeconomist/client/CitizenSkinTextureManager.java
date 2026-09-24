package com.jedts.theeconomist.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Downloads the skin image named by a signed Mojang texture property and registers it on the client. */
final class CitizenSkinTextureManager {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ConcurrentMap<String, Identifier> LOADED = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CitizenSkinTextureManager() { }

    static Identifier get(String textureValue, Identifier fallback) {
        if (textureValue == null || textureValue.isBlank()) return fallback;
        String key = key(textureValue);
        Identifier loaded = LOADED.get(key);
        if (loaded != null) return loaded;
        if (!FAILED.contains(key) && LOADING.add(key)) load(key, textureValue);
        return fallback;
    }

    private static void load(String key, String textureValue) {
        URI skinUri;
        try {
            String json = new String(Base64.getDecoder().decode(textureValue), java.nio.charset.StandardCharsets.UTF_8);
            String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                    .getAsJsonObject("SKIN").get("url").getAsString();
            skinUri = URI.create(url);
            if (!"textures.minecraft.net".equalsIgnoreCase(skinUri.getHost())) throw new IllegalArgumentException("untrusted skin host");
            skinUri = URI.create("https://textures.minecraft.net" + skinUri.getRawPath());
        } catch (RuntimeException exception) {
            LOADING.remove(key);
            FAILED.add(key);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(skinUri).timeout(Duration.ofSeconds(10)).GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            if (error != null || response == null || response.statusCode() != 200) {
                LOADING.remove(key);
                FAILED.add(key);
                return;
            }
            CompletableFuture.runAsync(() -> register(key, response.body()));
        });
    }

    private static void register(String key, byte[] bytes) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            try {
                NativeImage image = NativeImage.read(bytes);
                Identifier id = Identifier.fromNamespaceAndPath("theeconomist", "citizen_skins/" + key);
                minecraft.getTextureManager().register(id,
                        new DynamicTexture(() -> "Citizen skin " + key, image));
                LOADED.put(key, id);
            } catch (Exception exception) {
                FAILED.add(key);
            } finally {
                LOADING.remove(key);
            }
        });
    }

    private static String key(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
