package net.veroxuniverse.verox_rpg_prog.stage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Optional;

public record StageRaid(
        String name,
        Optional<ResourceLocation> displayItem,
        Trigger trigger,
        List<Wave> waves,
        Optional<RaidMob> leader,
        StageDefinition.MobAttributeScaling mobScaling,
        Reward reward
) {
    public static final String DEFAULT_NAME = "event.verox_rpg_prog.raid";

    public record Trigger(String time, float chance, int checkInterval, int cooldown) {
        public static final Trigger DEFAULT = new Trigger("night", 0.05f, 1200, 72000);

        public static final Codec<Trigger> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("time", "night").forGetter(Trigger::time),
                Codec.FLOAT.optionalFieldOf("chance", 0.05f).forGetter(Trigger::chance),
                Codec.INT.optionalFieldOf("check_interval", 1200).forGetter(Trigger::checkInterval),
                Codec.INT.optionalFieldOf("cooldown", 72000).forGetter(Trigger::cooldown)
        ).apply(i, Trigger::new));
    }

    public record WaveMob(ResourceLocation entity, Optional<ResourceLocation> displayItem, int count, List<StageDefinition.EquipmentEntry> equipment, List<String> requiredMods) {
        public static final Codec<WaveMob> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(WaveMob::entity),
                ResourceLocation.CODEC.optionalFieldOf("display_item").forGetter(WaveMob::displayItem),
                Codec.INT.optionalFieldOf("count", 1).forGetter(WaveMob::count),
                StageDefinition.EquipmentEntry.CODEC.listOf().optionalFieldOf("equipment", List.of()).forGetter(WaveMob::equipment),
                Codec.STRING.listOf().optionalFieldOf("required_mods", List.of()).forGetter(WaveMob::requiredMods)
        ).apply(i, WaveMob::new));

        public boolean isAvailable() {
            return StageRaid.isAvailable(this.entity, this.requiredMods);
        }
    }

    public record Wave(List<WaveMob> mobs) {
        public static final Codec<Wave> CODEC = RecordCodecBuilder.create(i -> i.group(
                WaveMob.CODEC.listOf().fieldOf("mobs").forGetter(Wave::mobs)
        ).apply(i, Wave::new));
    }

    public record RaidMob(ResourceLocation entity, Optional<ResourceLocation> displayItem, List<StageDefinition.EquipmentEntry> equipment, StageDefinition.MobAttributeScaling scaling, List<String> requiredMods) {
        public static final Codec<RaidMob> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(RaidMob::entity),
                ResourceLocation.CODEC.optionalFieldOf("display_item").forGetter(RaidMob::displayItem),
                StageDefinition.EquipmentEntry.CODEC.listOf().optionalFieldOf("equipment", List.of()).forGetter(RaidMob::equipment),
                StageDefinition.MobAttributeScaling.CODEC.optionalFieldOf("scaling", StageDefinition.MobAttributeScaling.NONE).forGetter(RaidMob::scaling),
                Codec.STRING.listOf().optionalFieldOf("required_mods", List.of()).forGetter(RaidMob::requiredMods)
        ).apply(i, RaidMob::new));

        public boolean isAvailable() {
            return StageRaid.isAvailable(this.entity, this.requiredMods);
        }
    }

    public record Reward(String type, List<String> structures, List<StageDefinition.DropEntry> items) {
        public static final Reward NONE = new Reward("none", List.of(), List.of());

        public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "map").forGetter(Reward::type),
                Codec.STRING.listOf().optionalFieldOf("structures", List.of()).forGetter(Reward::structures),
                StageDefinition.DropEntry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Reward::items)
        ).apply(i, Reward::new));
    }

    public static boolean isAvailable(ResourceLocation entity, List<String> requiredMods) {
        for (String modId : requiredMods) {
            if (!ModList.get().isLoaded(modId)) return false;
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(entity);
    }

    public static final Codec<StageRaid> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", DEFAULT_NAME).forGetter(StageRaid::name),
            ResourceLocation.CODEC.optionalFieldOf("display_item").forGetter(StageRaid::displayItem),
            Trigger.CODEC.optionalFieldOf("trigger", Trigger.DEFAULT).forGetter(StageRaid::trigger),
            Wave.CODEC.listOf().fieldOf("waves").forGetter(StageRaid::waves),
            RaidMob.CODEC.optionalFieldOf("leader").forGetter(StageRaid::leader),
            StageDefinition.MobAttributeScaling.CODEC.optionalFieldOf("mob_scaling", StageDefinition.MobAttributeScaling.NONE).forGetter(StageRaid::mobScaling),
            Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(StageRaid::reward)
    ).apply(i, StageRaid::new));
}