package net.veroxuniverse.verox_rpg_prog.book;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.veroxuniverse.verox_rpg_prog.RPGProgression;
import net.veroxuniverse.verox_rpg_prog.config.RPGProgressionConfig;
import net.veroxuniverse.verox_rpg_prog.registry.ModItems;

@EventBusSubscriber(modid = RPGProgression.MOD_ID)
public final class BookGrantHandler {

    private static final String RECEIVED_BOOK_KEY = RPGProgression.MOD_ID + ":received_book";

    private BookGrantHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!RPGProgressionConfig.GIVE_BOOK_ON_FIRST_JOIN.get()) return;

        CompoundTag persistentData = player.getPersistentData();
        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(RECEIVED_BOOK_KEY)) return;

        ItemStack book = new ItemStack(ModItems.STAGE_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }

        persisted.putBoolean(RECEIVED_BOOK_KEY, true);
        persistentData.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}