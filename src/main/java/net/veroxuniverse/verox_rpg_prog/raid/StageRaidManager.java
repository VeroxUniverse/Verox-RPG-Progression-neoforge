package net.veroxuniverse.verox_rpg_prog.raid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.config.RPGProgressionConfig;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;
import net.veroxuniverse.verox_rpg_prog.stage.StageManager;
import net.veroxuniverse.verox_rpg_prog.stage.StageRaid;
import net.veroxuniverse.verox_rpg_prog.territory.StageStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public final class StageRaidManager {

    private static final List<StageRaidInstance> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> NEXT_ALLOWED = new HashMap<>();

    private StageRaidManager() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        ACTIVE.forEach(StageRaidInstance::tick);
        ACTIVE.removeIf(StageRaidInstance::isDone);

        if (!RPGProgressionConfig.RAIDS_ENABLED.get()) return;

        int tick = server.getTickCount();
        for (StageDefinition stage : StageManager.getAllStages()) {
            if (stage.raids().isEmpty()) continue;
            if (StageStatus.of(stage) != StageStatus.CURRENT) continue;

            for (StageRaid raid : stage.raids()) {
                if (raid.waves().isEmpty()) continue;
                int interval = Math.max(20, raid.trigger().checkInterval());
                if (tick % interval != 0) continue;

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    tryStart(player, stage, raid);
                }
            }
        }
    }

    private static void tryStart(ServerPlayer player, StageDefinition stage, StageRaid raid) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;
        if (isInRaid(player.getUUID())) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now < NEXT_ALLOWED.getOrDefault(player.getUUID(), 0L)) return;
        if (!matchesTime(level, raid.trigger().time())) return;
        if (player.getRandom().nextFloat() >= raid.trigger().chance()) return;

        NEXT_ALLOWED.put(player.getUUID(), now + raid.trigger().cooldown());
        launch(new StageRaidInstance(raid, stage, level, player.blockPosition(), player.getUUID()));
    }

    public static boolean startRaid(ServerPlayer player, StageDefinition stage, StageRaid raid) {
        if (isInRaid(player.getUUID()) || raid.waves().isEmpty()) return false;
        launch(new StageRaidInstance(raid, stage, player.serverLevel(), player.blockPosition(), player.getUUID()));
        return true;
    }

    private static void launch(StageRaidInstance instance) {
        ACTIVE.add(instance);
        instance.start();
    }

    public static boolean isInRaid(UUID playerId) {
        for (StageRaidInstance instance : ACTIVE) {
            if (instance.getTargetPlayer().equals(playerId)) return true;
        }
        return false;
    }

    private static boolean matchesTime(ServerLevel level, String time) {
        return switch (time.toLowerCase()) {
            case "day" -> level.isDay();
            case "any" -> true;
            default -> !level.isDay();
        };
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.forEach(StageRaidInstance::stop);
        ACTIVE.clear();
        NEXT_ALLOWED.clear();
    }
}