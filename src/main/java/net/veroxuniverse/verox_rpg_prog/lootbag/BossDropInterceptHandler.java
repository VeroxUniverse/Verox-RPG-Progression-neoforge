package net.veroxuniverse.verox_rpg_prog.lootbag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.api.ProgressionApi;
import net.veroxuniverse.verox_rpg_prog.config.RPGProgressionConfig;
import net.veroxuniverse.verox_rpg_prog.registry.ModItems;
import net.veroxuniverse.verox_rpg_prog.registry.ModTags;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public class BossDropInterceptHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!RPGProgressionConfig.LOOT_BAGS_ENABLED.get()) return;
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;

        LivingEntity boss = event.getEntity();
        if (boss.getType().is(ModTags.NO_LOOT_BAG)) return;

        ResourceLocation bossEntityId = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType());
        StageDefinition.BossInfo bossInfo = ProgressionApi.getBossInfo(bossEntityId).orElse(null);
        if (bossInfo == null) return;

        event.setCanceled(true);

        double radius = bossInfo.playerScaling().radius();
        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                boss.getBoundingBox().inflate(radius)
        );

        for (ServerPlayer player : nearbyPlayers) {
            List<ItemStack> rolledItems = rollLoot(bossInfo, serverLevel, boss, player);
            if (rolledItems.isEmpty()) continue;

            ItemStack bag = new ItemStack(ModItems.LOOT_BAG.get());
            bag.set(ModDataComponents.LOOT_BAG_CONTENTS, new LootBagContents(bossEntityId, rolledItems));

            if (!player.getInventory().add(bag)) {
                player.drop(bag, false);
            }
        }
    }

    private static List<ItemStack> rollLoot(StageDefinition.BossInfo bossInfo, ServerLevel level, LivingEntity boss, ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();

        for (ResourceLocation guaranteedId : bossInfo.guaranteedDrops()) {
            Item item = BuiltInRegistries.ITEM.get(guaranteedId);
            if (item != null) {
                result.add(new ItemStack(item));
            }
        }

        String mode = bossInfo.drops().mode();
        boolean includeVanilla = !"replace".equalsIgnoreCase(mode);
        boolean includeCustom = !"none".equalsIgnoreCase(mode);

        if (includeVanilla) {
            LootParams.Builder vanillaParams = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, boss.position())
                    .withParameter(LootContextParams.THIS_ENTITY, boss)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player));

            List<ItemStack> vanillaDrops = level.getServer().reloadableRegistries()
                    .getLootTable(boss.getLootTable())
                    .getRandomItems(vanillaParams.create(LootContextParamSets.ENTITY));

            result.addAll(vanillaDrops);
        }

        if (includeCustom) {
            for (StageDefinition.DropEntry entry : bossInfo.drops().drops()) {
                if (level.getRandom().nextFloat() > entry.chance()) continue;

                Item item = BuiltInRegistries.ITEM.get(entry.item());
                if (item == null) continue;

                int count = entry.countMin() == entry.countMax()
                        ? entry.countMin()
                        : entry.countMin() + level.getRandom().nextInt(entry.countMax() - entry.countMin() + 1);

                if (count > 0) {
                    result.add(new ItemStack(item, count));
                }
            }
        }

        return result;
    }
}