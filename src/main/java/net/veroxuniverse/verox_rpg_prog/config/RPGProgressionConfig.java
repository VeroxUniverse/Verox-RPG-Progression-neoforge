package net.veroxuniverse.verox_rpg_prog.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RPGProgressionConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LOOT_BAGS_ENABLED;
    public static final ModConfigSpec.BooleanValue GIVE_BOOK_ON_FIRST_JOIN;
    public static final ModConfigSpec.BooleanValue RAIDS_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("loot_bags");
        LOOT_BAGS_ENABLED = builder
                .comment("If true, registered bosses drop individually rolled loot bags for every nearby player instead of ground loot.")
                .define("enabled", true);
        builder.pop();

        builder.push("book");
        GIVE_BOOK_ON_FIRST_JOIN = builder
                .comment("If true, every player receives the Progression Journal the first time they join a world.")
                .define("give_on_first_join", true);
        builder.pop();

        builder.push("raids");
        RAIDS_ENABLED = builder
                .comment("If true, stage raids defined in data/<namespace>/stage_raids/ can attack players while their stage is the current goal.")
                .define("enabled", false);
        builder.pop();

        SPEC = builder.build();
    }

    private RPGProgressionConfig() {}
}