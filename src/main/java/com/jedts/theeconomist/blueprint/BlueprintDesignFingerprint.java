package com.jedts.theeconomist.blueprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
                        addTag(digest, block.blockEntityData());
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

    private static void addTag(MessageDigest digest, Tag tag) {
        if (tag == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update(tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = compound.keySet().stream().sorted().toList();
            addInt(digest, keys.size());
            for (String key : keys) {
                addString(digest, key);
                addTag(digest, compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            addInt(digest, list.size());
            for (Tag element : list) addTag(digest, element);
        } else {
            addString(digest, tag.toString());
        }
    }
}
