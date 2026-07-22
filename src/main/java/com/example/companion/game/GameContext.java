package com.example.companion.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/** Compact world snapshot fed to the LLM as context. Client-visible info only. */
public final class GameContext {
    private GameContext() {}

    public static String snapshot() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (p == null || lvl == null) return "Player is not in a world.";

        BlockPos pos = p.blockPosition();
        String biome = lvl.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
        long time = lvl.getDayTime() % 24000L;
        String phase = (time < 12300 || time > 23850) ? "day" : "night";

        return "pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
             + "; dim=" + lvl.dimension().location()
             + "; biome=" + biome
             + "; time=" + phase
             + "; hp=" + (int) p.getHealth() + "/" + (int) p.getMaxHealth();
    }
}
