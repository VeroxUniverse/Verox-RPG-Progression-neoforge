package net.veroxuniverse.verox_rpg_prog.book;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.registry.ModItems;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public final class BookCreativeTabHandler {

    private BookCreativeTabHandler() {}

    @SubscribeEvent
    public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.STAGE_BOOK.get());
        }
    }
}