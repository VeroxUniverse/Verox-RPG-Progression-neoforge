package net.veroxuniverse.verox_rpg_prog.handler;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;
import net.veroxuniverse.verox_rpg_prog.stage.StageManager;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public class MobEquipmentHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType());

        StageManager.getCurrentEquipmentOverride(entityId).ifPresent(override -> {
            for (StageDefinition.EquipmentEntry entry : override.equipment()) {
                applyEquipment(monster, serverLevel, entry);
            }
        });
    }

    public static void applyEquipment(Mob entity, ServerLevel serverLevel, StageDefinition.EquipmentEntry entry) {
        EquipmentSlot slot = resolveSlot(entry.slot());
        if (slot == null) {
            RPGProgression.LOGGER.warn("MobEquipmentHandler: unknown slot '{}' for {}", entry.slot(), entry.item());
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(entry.item());
        if (item == null) {
            RPGProgression.LOGGER.warn("MobEquipmentHandler: unknown item {}", entry.item());
            return;
        }

        ItemStack stack = new ItemStack(item);

        for (StageDefinition.EnchantmentEntry enchantmentEntry : entry.enchantments()) {
            ResourceKey<Enchantment> enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, enchantmentEntry.enchantmentId());

            Holder<Enchantment> enchantment = serverLevel.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(enchantmentKey)
                    .map(reference -> (Holder<Enchantment>) reference)
                    .orElse(null);

            if (enchantment == null) {
                RPGProgression.LOGGER.warn("MobEquipmentHandler: unknown enchantment {}", enchantmentEntry.enchantmentId());
                continue;
            }

            stack.enchant(enchantment, enchantmentEntry.level());
        }

        entity.setItemSlot(slot, stack);
        entity.setDropChance(slot, entry.dropChance());
    }

    private static EquipmentSlot resolveSlot(String slotName) {
        return switch (slotName.toLowerCase()) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}