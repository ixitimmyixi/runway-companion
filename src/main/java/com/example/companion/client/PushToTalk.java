package com.example.companion.client;

import com.example.companion.CompanionConfig;
import com.example.companion.CompanionMod;
import com.example.companion.Pipeline;
import com.example.companion.ai.SttClient;
import com.example.companion.audio.AudioPlayer;
import com.example.companion.audio.MicCapture;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Hold V (rebindable in Options > Controls) to record; release to transcribe
 * via STT and route the text through the companion pipeline.
 */
@Mod.EventBusSubscriber(modid = CompanionMod.MODID, value = Dist.CLIENT)
public final class PushToTalk {
    public static final KeyMapping TALK = new KeyMapping(
        "key.companion.talk", KeyConflictContext.IN_GAME,
        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V, "key.categories.companion");

    private static final MicCapture MIC = new MicCapture();
    private static boolean wasDown = false;

    @Mod.EventBusSubscriber(modid = CompanionMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent e) { e.register(TALK); }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        boolean down = TALK.isDown();
        if (down && !wasDown) startRecording();
        if (!down && wasDown) stopAndSend();
        wasDown = down;
    }

    private static void startRecording() {
        try {
            AudioPlayer.stop(); // cut off Bufo's current speech the moment the player talks
            MIC.start(CompanionConfig.micDeviceName);
        } catch (Exception ex) {
            Pipeline.echo("(mic error: " + ex.getMessage() + ")");
        }
    }

    private static void stopAndSend() {
        new Thread(() -> {
            try {
                byte[] wav = MIC.stop();
                if (wav.length == 0) return;
                String text = SttClient.transcribe(wav);
                if (text.isBlank()) return;
                Pipeline.echo("§7(you said: " + text + ")");
                Pipeline.handle(text);
            } catch (Exception ex) {
                Pipeline.echo("(voice error: " + ex.getMessage() + ")");
            }
        }, "companion-ptt").start();
    }
}
