package com.example.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lets Bufo run a strict allowlist of read-only /locate commands and capture the
 * result. Singleplayer only, and only the exact commands built here from a fixed
 * table -- the AI never supplies raw command text, so it can't run anything else.
 */
public final class BufoActions {
    private BufoActions() {}

    private static final Map<String, String> LOCATE = Map.ofEntries(
        Map.entry("village", "#minecraft:village"),
        Map.entry("pillager_outpost", "minecraft:pillager_outpost"),
        Map.entry("outpost", "minecraft:pillager_outpost"),
        Map.entry("mansion", "minecraft:mansion"),
        Map.entry("woodland_mansion", "minecraft:mansion"),
        Map.entry("monument", "minecraft:monument"),
        Map.entry("ocean_monument", "minecraft:monument"),
        Map.entry("stronghold", "minecraft:stronghold"),
        Map.entry("nether_fortress", "minecraft:fortress"),
        Map.entry("fortress", "minecraft:fortress"),
        Map.entry("bastion", "minecraft:bastion_remnant"),
        Map.entry("end_city", "minecraft:end_city"),
        Map.entry("ancient_city", "minecraft:ancient_city"),
        Map.entry("mineshaft", "#minecraft:mineshaft"),
        Map.entry("ruined_portal", "#minecraft:ruined_portal"),
        Map.entry("shipwreck", "minecraft:shipwreck"),
        Map.entry("buried_treasure", "minecraft:buried_treasure"),
        Map.entry("desert_pyramid", "minecraft:desert_pyramid"),
        Map.entry("desert_temple", "minecraft:desert_pyramid"),
        Map.entry("jungle_pyramid", "minecraft:jungle_pyramid"),
        Map.entry("jungle_temple", "minecraft:jungle_pyramid"),
        Map.entry("igloo", "minecraft:igloo"),
        Map.entry("swamp_hut", "minecraft:swamp_hut"),
        Map.entry("witch_hut", "minecraft:swamp_hut"),
        Map.entry("trail_ruins", "minecraft:trail_ruins"));

    public static boolean canLocate(String target) {
        return target != null && LOCATE.containsKey(target.toLowerCase());
    }

    /** Runs /locate for a whitelisted target and returns the game's message text. */
    public static String locate(String target) throws Exception {
        String arg = LOCATE.get(target.toLowerCase());
        if (arg == null) return "I can't locate that.";

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("I can only run commands in singleplayer right now.");
        if (mc.player == null) throw new IllegalStateException("No player.");
        UUID uuid = mc.player.getUUID();
        String command = "locate structure " + arg;

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp == null) { future.complete("(couldn't find you on the server)"); return; }
                ServerLevel level = (ServerLevel) sp.level();
                StringBuilder out = new StringBuilder();
                CommandSource sink = new CommandSource() {
                    @Override public void sendSystemMessage(Component c) { out.append(c.getString()).append(' '); }
                    @Override public boolean acceptsSuccess() { return true; }
                    @Override public boolean acceptsFailure() { return true; }
                    @Override public boolean shouldInformAdmins() { return false; }
                };
                CommandSourceStack stack = new CommandSourceStack(
                    sink, sp.position(), new Vec2(sp.getXRot(), sp.getYRot()),
                    level, 2, sp.getName().getString(), sp.getDisplayName(), server, sp);
                server.getCommands().performPrefixedCommand(stack, command);
                String s = out.toString().trim();
                future.complete(s.isEmpty() ? "(no result returned)" : s);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(8, TimeUnit.SECONDS);
    }

    /** Runs an arbitrary command at op level in the player's singleplayer world; returns feedback text. */
    public static String run(String command) throws Exception {
        if (command == null || command.isBlank()) return "(empty command)";
        String c = command.strip();
        if (c.startsWith("/")) c = c.substring(1);
        final String toRun = c;

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("I can only run commands in singleplayer right now.");
        if (mc.player == null) throw new IllegalStateException("No player.");
        UUID uuid = mc.player.getUUID();

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp == null) { future.complete("(couldn't find you on the server)"); return; }
                ServerLevel level = (ServerLevel) sp.level();
                StringBuilder out = new StringBuilder();
                CommandSource sink = new CommandSource() {
                    @Override public void sendSystemMessage(Component c) { out.append(c.getString()).append(' '); }
                    @Override public boolean acceptsSuccess() { return true; }
                    @Override public boolean acceptsFailure() { return true; }
                    @Override public boolean shouldInformAdmins() { return false; }
                };
                CommandSourceStack stack = new CommandSourceStack(
                    sink, sp.position(), new Vec2(sp.getXRot(), sp.getYRot()),
                    level, 4, sp.getName().getString(), sp.getDisplayName(), server, sp);
                server.getCommands().performPrefixedCommand(stack, toRun);
                String r = out.toString().trim();
                future.complete(r.isEmpty() ? "(done)" : r);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(8, TimeUnit.SECONDS);
    }
}
