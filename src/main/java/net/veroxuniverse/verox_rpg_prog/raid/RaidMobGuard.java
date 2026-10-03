package net.veroxuniverse.verox_rpg_prog.raid;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public final class RaidMobGuard {

    public static final String RAID_MOB_TAG = RPGProgression.MOD_ID + ":raid_mob";

    private RaidMobGuard() {}

    public static void mark(Entity entity) {
        entity.getPersistentData().putBoolean(RAID_MOB_TAG, true);
    }

    public static boolean isRaidMob(Entity entity) {
        return entity.getPersistentData().getBoolean(RAID_MOB_TAG);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !isRaidMob(event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide() || !isRaidMob(event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        if (isRaidMob(event.getAnimal())) {
            event.setCanceled(true);
        }
    }
}