package com.example.companion.client;

import com.example.companion.CompanionConfig;
import com.example.companion.CompanionMod;
import com.example.companion.Pipeline;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Say "<wakeWord> <message>" in chat to talk to the companion (typed input).
 * The message is intercepted and handed to the shared Pipeline.
 */
@Mod.EventBusSubscriber(modid = CompanionMod.MODID, value = Dist.CLIENT)
public final class ChatHandler {
    @SubscribeEvent
    public static void onChat(ClientChatEvent event) {
        String raw = event.getMessage().trim();
        String prefix = CompanionConfig.wakeWord + " ";
        if (!raw.toLowerCase().startsWith(prefix.toLowerCase())) return;

        event.setCanceled(true); // don't broadcast to the server
        Pipeline.handle(raw.substring(prefix.length()).trim());
    }
}
