package net.veroxuniverse.verox_rpg_prog.stage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record StageAddition(
        int order,
        List<StageDefinition.MobEquipmentOverride> mobEquipment,
        List<StageDefinition.OreDisguise> lockedOres,
        List<StageDefinition.BossInfo> optionalBosses,
        List<ResourceLocation> lockedItems,
        List<ResourceLocation> lockedBlocks,
        List<ResourceLocation> lockedDimensions,
        List<StageDefinition.Territory> territories
) {
    public static final Codec<StageAddition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("order").forGetter(StageAddition::order),
            StageDefinition.MobEquipmentOverride.CODEC.listOf().optionalFieldOf("mob_equipment", List.of()).forGetter(StageAddition::mobEquipment),
            StageDefinition.OreDisguise.CODEC.listOf().optionalFieldOf("locked_ores", List.of()).forGetter(StageAddition::lockedOres),
            StageDefinition.BossInfo.CODEC.listOf().optionalFieldOf("optional_bosses", List.of()).forGetter(StageAddition::optionalBosses),
            ResourceLocation.CODEC.listOf().optionalFieldOf("locked_items", List.of()).forGetter(StageAddition::lockedItems),
            ResourceLocation.CODEC.listOf().optionalFieldOf("locked_blocks_to_mine", List.of()).forGetter(StageAddition::lockedBlocks),
            ResourceLocation.CODEC.listOf().optionalFieldOf("locked_dimensions", List.of()).forGetter(StageAddition::lockedDimensions),
            StageDefinition.Territory.CODEC.listOf().optionalFieldOf("territories", List.of()).forGetter(StageAddition::territories)
    ).apply(instance, StageAddition::new));

    public StageDefinition applyTo(StageDefinition stage) {
        return new StageDefinition(
                stage.order(),
                stage.translationKey(),
                stage.mobAttributeScaling(),
                concat(stage.mobEquipment(), this.mobEquipment),
                concat(stage.lockedOres(), this.lockedOres),
                stage.mainBoss(),
                concat(stage.optionalBosses(), this.optionalBosses),
                concat(stage.lockedItems(), this.lockedItems),
                concat(stage.lockedBlocks(), this.lockedBlocks),
                concat(stage.lockedDimensions(), this.lockedDimensions),
                concat(stage.territories(), this.territories),
                stage.raids()
        );
    }

    public static StageDefinition withRaid(StageDefinition stage, StageRaid raid) {
        return new StageDefinition(
                stage.order(),
                stage.translationKey(),
                stage.mobAttributeScaling(),
                stage.mobEquipment(),
                stage.lockedOres(),
                stage.mainBoss(),
                stage.optionalBosses(),
                stage.lockedItems(),
                stage.lockedBlocks(),
                stage.lockedDimensions(),
                stage.territories(),
                concat(stage.raids(), List.of(raid))
        );
    }

    private static <T> List<T> concat(List<T> base, List<T> extra) {
        if (extra.isEmpty()) return base;
        List<T> merged = new ArrayList<>(base);
        merged.addAll(extra);
        return List.copyOf(merged);
    }
}