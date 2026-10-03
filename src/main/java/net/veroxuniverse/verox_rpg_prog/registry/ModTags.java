package net.veroxuniverse.verox_rpg_prog.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;

public class ModTags {

    public static final TagKey<EntityType<?>> NO_LOOT_BAG = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(RPGProgression.MOD_ID, "no_loot_bag")
    );
}