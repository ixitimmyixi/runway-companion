package com.example.companion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Game (Forge) bus event handlers. Grants the player a Bufo spawn egg the first
 * time they join a given world, so a fresh world comes with a companion ready.
 */
@Mod.EventBusSubscriber(modid = CompanionMod.MODID)
public final class CompanionEvents {
    private CompanionEvents() {}

    private static final String GRANTED_FLAG = "companion_bufo_granted";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // Persist a one-time flag on the player (saved with this world's player data).
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GRANTED_FLAG)) return;

        persisted.putBoolean(GRANTED_FLAG, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);

        player.addItem(new ItemStack(ModRegistry.BUFO_SPAWN_EGG.get()));
    }
}
