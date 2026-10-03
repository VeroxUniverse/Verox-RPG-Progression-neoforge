package net.veroxuniverse.verox_rpg_prog.raid;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;
import net.veroxuniverse.verox_rpg_prog.stage.StageRaid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RaidRewards {

    private static final int SEARCH_RADIUS_CHUNKS = 100;

    private RaidRewards() {}

    public static ItemStack createLocator(ServerLevel level, BlockPos origin, StageRaid raid, StageDefinition stage) {
        String type = raid.reward().type();
        if (!"map".equalsIgnoreCase(type) && !"compass".equalsIgnoreCase(type)) return ItemStack.EMPTY;

        List<String> entries = raid.reward().structures();
        if (entries.isEmpty()) {
            entries = new ArrayList<>();
            for (StageDefinition.Territory territory : stage.territories()) {
                entries.addAll(territory.structures());
            }
        }

        List<Holder<Structure>> holders = resolveStructures(level, entries);
        if (holders.isEmpty()) return ItemStack.EMPTY;

        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holders), origin, SEARCH_RADIUS_CHUNKS, false);
        if (found == null) return ItemStack.EMPTY;

        BlockPos target = found.getFirst();
        Component structureName = structureName(found.getSecond());

        if ("compass".equalsIgnoreCase(type)) {
            ItemStack compass = new ItemStack(Items.COMPASS);
            compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), target)), false));
            compass.set(DataComponents.ITEM_NAME, Component.translatable("item.verox_rpg_prog.raid_compass", structureName));
            return compass;
        }

        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.verox_rpg_prog.raid_map", structureName));
        return map;
    }

    public static List<ItemStack> rollItems(StageRaid raid, RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        for (StageDefinition.DropEntry drop : raid.reward().items()) {
            if (random.nextFloat() >= drop.chance()) continue;
            if (!BuiltInRegistries.ITEM.containsKey(drop.item())) continue;

            Item item = BuiltInRegistries.ITEM.get(drop.item());
            int min = Math.max(1, drop.countMin());
            int max = Math.max(min, drop.countMax());
            result.add(new ItemStack(item, min + random.nextInt(max - min + 1)));
        }
        return result;
    }

    private static List<Holder<Structure>> resolveStructures(ServerLevel level, List<String> entries) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();

        for (String entry : entries) {
            if (entry.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
                if (id == null) continue;
                registry.getTag(TagKey.create(Registries.STRUCTURE, id)).ifPresent(set -> set.forEach(holders::add));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id == null) continue;
                registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id)).ifPresent(holders::add);
            }
        }
        return holders;
    }

    private static Component structureName(Holder<Structure> structure) {
        ResourceLocation id = structure.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) return Component.literal("?");

        String path = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replace('_', ' ');
        String fallback = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return Component.translatableWithFallback("structure." + id.getNamespace() + "." + id.getPath(), fallback);
    }
}