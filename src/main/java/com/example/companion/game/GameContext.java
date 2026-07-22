package com.example.companion.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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

        // Where is Bufo's own body (the companion entity), if it's spawned nearby?
        double best = -1;
        try {
            for (Entity e : lvl.entitiesForRendering()) {
                if ("companion:bufo".equals(EntityType.getKey(e.getType()).toString())) {
                    double d = Math.sqrt(e.distanceToSqr(p));
                    if (best < 0 || d < best) best = d;
                }
            }
        } catch (Exception ignored) {}
        String body = best < 0 ? "your body (Bufo) is not currently spawned"
            : best < 4 ? "your body (Bufo) is right next to the player"
            : ("your body (Bufo) is about " + (int) best + " blocks from the player");

        return "pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
             + "; dim=" + lvl.dimension().location()
             + "; biome=" + biome
             + "; time=" + phase
             + "; hp=" + (int) p.getHealth() + "/" + (int) p.getMaxHealth()
             + "; " + body;
    }
}
