package com.jedts.theeconomist.citizen.command;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.contract.CitizenContract;
import com.jedts.theeconomist.contract.board.ContractBoardService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions() instanceof LevelBasedPermissionSet permissions
                                && permissions.level().isEqualOrHigherThan(PermissionLevel.ADMINS))
                        .executes(context -> CitizenRuntime.reload(context.getSource())))
                .then(Commands.literal("contracts")
                        .requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                        .executes(context -> showContracts((net.minecraft.server.level.ServerPlayer) context.getSource().getEntity())))
                .then(Commands.literal("contract")
                        .requires(source -> source.getEntity() != null)
                        .then(Commands.literal("service")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .then(Commands.argument("bounty", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("deadline", LongArgumentType.longArg(1))
                                                        .then(Commands.argument("skill", IntegerArgumentType.integer(0, 100))
                                                                .executes(context -> createServiceContract(context.getSource(),
                                                                        StringArgumentType.getString(context, "target"),
                                                                        IntegerArgumentType.getInteger(context, "bounty"),
                                                                        LongArgumentType.getLong(context, "deadline"),
                                                                        IntegerArgumentType.getInteger(context, "skill"))))))))));
    }

    private static int showContracts(net.minecraft.server.level.ServerPlayer player) {
        ContractBoardService.send(player, CitizenRuntime.contracts());
        return 1;
    }

    private static int createServiceContract(CommandSourceStack source, String target, int bounty,
                                             long deadlineOffset, int requiredSkill) {
        if (target.isBlank()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Contract target must not be blank"));
            return 0;
        }
        long deadline = source.getLevel().getGameTime() + deadlineOffset;
        CitizenContract contract = CitizenContract.service(java.util.UUID.randomUUID(), source.getEntity().getUUID(),
                target, bounty, deadline, requiredSkill);
        CitizenRuntime.contracts().publish(contract);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Published service contract " + contract.id() + " for " + bounty + " Crowns"), false);
        return 1;
    }
}
