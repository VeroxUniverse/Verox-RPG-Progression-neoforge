package net.veroxuniverse.verox_rpg_prog.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.raid.StageRaidManager;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;
import net.veroxuniverse.verox_rpg_prog.stage.StageManager;
import net.veroxuniverse.verox_rpg_prog.territory.StageStatus;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public final class RaidCommand {

    private RaidCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rpg_prog")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("raid")
                        .then(Commands.literal("start")
                                .executes(context -> startRaid(context.getSource())))));
    }

    private static int startRaid(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        for (StageDefinition stage : StageManager.getAllStages()) {
            if (StageStatus.of(stage) != StageStatus.CURRENT || stage.raids().isEmpty()) continue;

            if (StageRaidManager.startRaid(player, stage, stage.raids().getFirst())) {
                source.sendSuccess(() -> Component.translatable("command.verox_rpg_prog.raid.started"), true);
                return 1;
            }
            source.sendFailure(Component.translatable("command.verox_rpg_prog.raid.already_running"));
            return 0;
        }

        source.sendFailure(Component.translatable("command.verox_rpg_prog.raid.none"));
        return 0;
    }
}