package com.jedts.theeconomist.blueprint;

import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintBlockEntitySerializationTest {
    @Test
    void rejects_codec_error_instead_of_accepting_partial_tag() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BlueprintBlockEntitySerialization.save(RegistryAccess.EMPTY,
                        output -> output.store("invalid", Codec.intRange(0, 1), 2)));
        assertTrue(error.getMessage().contains("serialization"));
    }

    @Test
    void accepts_complete_serialization() {
        var tag = BlueprintBlockEntitySerialization.save(RegistryAccess.EMPTY,
                output -> output.putString("id", "minecraft:chest"));
        assertEquals("minecraft:chest", tag.getStringOr("id", ""));
    }
}
