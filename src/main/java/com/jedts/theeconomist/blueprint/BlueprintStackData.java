package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public record BlueprintStackData(BlueprintState state, BlueprintDesign design, BlueprintPlacement placement) {
    private static final String ROOT = "TheEconomistBlueprint";

    public static BlueprintStackData read(ItemStack stack) {
        return readTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static BlueprintStackData readTag(CompoundTag tag) {
        CompoundTag root = tag.getCompoundOrEmpty(ROOT);
        BlueprintState state = parseState(root.getStringOr("State", BlueprintState.EMPTY.name()));
        BlueprintDesign design = root.contains("Width") ? readDesign(root) : null;
        BlueprintPlacement placement = root.contains("OriginX") ? new BlueprintPlacement(root.getStringOr("Dimension", "minecraft:overworld"),
                new BlockPos(root.getIntOr("OriginX", 0), root.getIntOr("OriginY", 0), root.getIntOr("OriginZ", 0)),
                root.getIntOr("Rotation", 0), root.getBooleanOr("MirrorX", false), root.getBooleanOr("MirrorZ", false)) : null;
        return new BlueprintStackData(state, design, placement);
    }

    public static void setDesigned(ItemStack stack, BlueprintDesign design) {
        write(stack, new BlueprintStackData(BlueprintState.DESIGNED, design, null));
    }

    public static void setPlanned(ItemStack stack, BlueprintPlacement placement) {
        BlueprintStackData current = read(stack);
        if (current.design == null) throw new IllegalStateException("cannot plan an empty blueprint");
        write(stack, new BlueprintStackData(BlueprintState.PLANNED, current.design, placement));
    }

    public static void write(ItemStack stack, BlueprintStackData data) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(BlueprintDisplayName.forState(data.state())));
        CustomData.set(DataComponents.CUSTOM_DATA, stack, writeTag(data));
    }

    public static CompoundTag writeTag(BlueprintStackData data) {
        CompoundTag root = new CompoundTag();
        root.putString("State", data.state.name());
        if (data.design != null) writeDesign(root, data.design);
        if (data.placement != null) {
            root.putInt("OriginX", data.placement.origin().getX());
            root.putString("Dimension", data.placement.dimension());
            root.putInt("OriginY", data.placement.origin().getY());
            root.putInt("OriginZ", data.placement.origin().getZ());
            root.putInt("Rotation", data.placement.rotation());
            root.putBoolean("MirrorX", data.placement.mirrorX());
            root.putBoolean("MirrorZ", data.placement.mirrorZ());
        }
        CompoundTag all = new CompoundTag();
        all.put(ROOT, root);
        return all;
    }

    private static void writeDesign(CompoundTag root, BlueprintDesign design) {
        root.putInt("Width", design.width()); root.putInt("Height", design.height()); root.putInt("Depth", design.depth());
        ListTag blocks = new ListTag();
        for (BlueprintBlock block : design.blocks()) {
            CompoundTag value = new CompoundTag();
            value.putInt("X", block.x()); value.putInt("Y", block.y()); value.putInt("Z", block.z()); value.putString("Block", block.blockId()); value.putString("Properties", block.stateProperties());
            if (block.blockEntityData() != null) {
                root.putInt("FormatVersion", 2);
                value.put("BlockEntity", block.blockEntityData());
            }
            blocks.add(value);
        }
        root.put("Blocks", blocks);
    }

    private static BlueprintDesign readDesign(CompoundTag root) {
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (CompoundTag value : root.getListOrEmpty("Blocks").compoundStream().toList()) {
            blocks.add(new BlueprintBlock(value.getIntOr("X", 0), value.getIntOr("Y", 0), value.getIntOr("Z", 0),
                    value.getStringOr("Block", ""), value.getStringOr("Properties", ""),
                    value.contains("BlockEntity") ? value.getCompoundOrEmpty("BlockEntity") : null));
        }
        return new BlueprintDesign(root.getIntOr("Width", 1), root.getIntOr("Height", 1), root.getIntOr("Depth", 1), blocks);
    }

    private static BlueprintState parseState(String value) {
        try { return BlueprintState.valueOf(value); } catch (IllegalArgumentException ignored) { return BlueprintState.EMPTY; }
    }
}
