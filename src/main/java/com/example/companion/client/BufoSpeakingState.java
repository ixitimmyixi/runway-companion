package com.example.companion.client;

/**
 * Client-side flag for whether Bufo is currently speaking (audio playing).
 * Set by AudioPlayer, read by the renderer/entity for talk animations.
 * Singleplayer-oriented: one shared flag rather than per-entity network sync.
 */
public final class BufoSpeakingState {
    private static volatile boolean speaking = false;
    private BufoSpeakingState() {}
    public static void start() { speaking = true; }
    public static void stop() { speaking = false; }
    public static boolean isSpeaking() { return speaking; }
}
