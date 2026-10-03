package net.veroxuniverse.verox_rpg_prog;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.veroxuniverse.verox_rpg_prog.command.StageCommand;
import net.veroxuniverse.verox_rpg_prog.compat.curios.CuriosRestrictionHandler;
import net.veroxuniverse.verox_rpg_prog.config.RPGProgressionConfig;
import net.veroxuniverse.verox_rpg_prog.handler.BlockRestrictionHandler;
import net.veroxuniverse.verox_rpg_prog.handler.ItemRestrictionHandler;
import net.veroxuniverse.verox_rpg_prog.lootbag.ModDataComponents;
import net.veroxuniverse.verox_rpg_prog.registry.ModItems;
import net.veroxuniverse.verox_rpg_prog.stage.WorldStageSavedData;
import org.slf4j.Logger;

@Mod(RPGProgression.MOD_ID)
public class RPGProgression {
    public static final String MOD_ID = "verox_rpg_prog";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RPGProgression(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, RPGProgressionConfig.SPEC);
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(new BlockRestrictionHandler());
        NeoForge.EVENT_BUS.register(new ItemRestrictionHandler());
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);

        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);

        if (ModList.get().isLoaded("curios")) {
            NeoForge.EVENT_BUS.register(new CuriosRestrictionHandler());
        }

        LOGGER.info("RPG Progression initialized successfully.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }


    private void registerCommands(RegisterCommandsEvent event) {
        StageCommand.register(event.getDispatcher());
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            WorldStageSavedData data = WorldStageSavedData.get(serverLevel);
            LOGGER.info("Loaded RPG Progression Stage Order: {}", data.getUnlockedOrder());
        }
    }
}