package com.jedts.theeconomist.blueprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/** Stable, bounded digest of a design's dimensions, coordinates, IDs, and block-state properties. */
public final class BlueprintDesignFingerprint {
    private BlueprintDesignFingerprint() { }

    public static String of(BlueprintDesign design) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            addInt(digest, design.width());
            addInt(digest, design.height());
            addInt(digest, design.depth());
            addInt(digest, design.blocks().size());
            design.blocks().stream()
                    .sorted(Comparator.comparingInt(BlueprintBlock::x)
                            .thenComparingInt(BlueprintBlock::y)
                            .thenComparingInt(BlueprintBlock::z))
                    .forEach(block -> {
                        addInt(digest, block.x());
                        addInt(digest, block.y());
                        addInt(digest, block.z());
                        addString(digest, block.blockId());
                        addString(digest, block.stateProperties());
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void addInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void addString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        addInt(digest, bytes.length);
        digest.update(bytes);
    }
}
