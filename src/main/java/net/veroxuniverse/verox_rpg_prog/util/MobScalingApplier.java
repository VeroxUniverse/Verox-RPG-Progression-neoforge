package net.veroxuniverse.verox_rpg_prog.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.stage.StageDefinition;

import java.util.List;
import java.util.Optional;

public final class MobScalingApplier {

    private MobScalingApplier() {}

    public static void apply(LivingEntity living, StageDefinition.MobAttributeScaling scaling, String prefix) {
        if (scaling.isNoOp()) return;

        AttributeScalingUtil.applyFractionModifier(living, Attributes.MAX_HEALTH, RPGProgression.id(prefix + "_health"), scaling.healthMultiplier() - 1.0f);
        AttributeScalingUtil.applyFractionModifier(living, Attributes.ATTACK_DAMAGE, RPGProgression.id(prefix + "_damage"), scaling.damageMultiplier() - 1.0f);
        AttributeScalingUtil.applyFractionModifier(living, Attributes.KNOCKBACK_RESISTANCE, RPGProgression.id(prefix + "_knockback_resistance"), scaling.knockbackResistanceMultiplier() - 1.0f);
        AttributeScalingUtil.applyFractionModifier(living, Attributes.ARMOR, RPGProgression.id(prefix + "_armor"), scaling.armorMultiplier() - 1.0f);

        List<StageDefinition.AttributeEntry> entries = scaling.attributes();
        for (int i = 0; i < entries.size(); i++) {
            StageDefinition.AttributeEntry entry = entries.get(i);
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(entry.attribute());
            if (attribute.isEmpty()) continue;

            AttributeScalingUtil.applyModifier(living, attribute.get(), RPGProgression.id(prefix + "_extra_" + i), entry.amount(), entry.operation());
        }
    }
}