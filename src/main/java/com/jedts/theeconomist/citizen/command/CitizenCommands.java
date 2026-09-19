package com.jedts.theeconomist.citizen.command;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

public final class CitizenCommands {
    private CitizenCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("theeconomist")
                .requires(source -> source.permissions() instanceof LevelBasedPermissionSet permissions
                        && permissions.level().isEqualOrHigherThan(PermissionLevel.ADMINS))
                .then(Commands.literal("reload")
                        .executes(context -> CitizenRuntime.reload(context.getSource()))));
    }
}
