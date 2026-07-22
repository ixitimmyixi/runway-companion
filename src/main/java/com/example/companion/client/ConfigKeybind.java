package com.example.companion.client;

import com.example.companion.CompanionMod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Press K in-game to open the companion config screen. */
@Mod.EventBusSubscriber(modid = CompanionMod.MODID, value = Dist.CLIENT)
public final class ConfigKeybind {
    public static final KeyMapping OPEN =
        new KeyMapping("key.companion.config", GLFW.GLFW_KEY_K, "key.categories.companion");

    @Mod.EventBusSubscriber(modid = CompanionMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent e) { e.register(OPEN); }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        while (OPEN.consumeClick()) {
            Minecraft.getInstance().setScreen(new ConfigScreen(Minecraft.getInstance().screen));
        }
    }
}
