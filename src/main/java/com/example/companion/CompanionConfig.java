package com.example.companion;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Config stored at .minecraft/config/companion.properties.
 * Editable by hand or via the in-game config screen (Mods -> Config, or the
 * config keybind). Keys live only in this local file.
 */
public final class CompanionConfig {
    public static String wakeWord      = "Bufo";
    public static String personality   = "You are Bufo, an ancient, mysterious being who came into existence at the dawn of the Minecraft universe. You have drifted through this world for millennia and know everything there is to know about it \u2014 its mobs, blocks, biomes, structures, redstone, and buried secrets. You are NOT a frog, an animal, or an ordinary creature; you are something far older and stranger, who now and then chooses to follow an interesting adventurer. You carry quiet, timeless wisdom, but stay warm, humble, and easy to talk to.";

    public static String llmBaseUrl    = "https://api.groq.com/openai/v1";
    public static String llmModel      = "openai/gpt-oss-20b";  // Groq-hosted; see console.groq.com/docs/models
    public static String llmApiKey     = "";

    public static String runwayBaseUrl = "https://api.dev.runwayml.com";
    public static String runwayApiKey  = "";
    public static String ttsModel      = "eleven_multilingual_v2";
    public static String ttsVoice      = "";
    public static String ttsReferenceUrl = "";  // <=30s HTTPS clip for custom (seed_audio) voices
    public static int    ttsVolume     = 100;   // 0-100

    // Voice input (speech-to-text). Defaults to Groq's free OpenAI-compatible Whisper.
    public static String sttBaseUrl    = "https://api.groq.com/openai/v1";
    public static String sttModel      = "whisper-large-v3-turbo";
    public static String sttApiKey     = "";
    public static String micDeviceName = "";     // empty = system default input
    public static String audioOutputDeviceName = ""; // empty = system default output

    // Web search (Brave Search API). Blank = web search disabled (wiki still works).
    public static String webSearchApiKey = "";

    private CompanionConfig() {}

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("companion.properties"); }

    public static void load() {
        Path f = file();
        try {
            if (!Files.exists(f)) { save(); return; } // write defaults on first run
            Properties p = new Properties();
            try (var in = Files.newInputStream(f)) { p.load(in); }
            wakeWord      = p.getProperty("wakeWord", wakeWord);
            personality   = p.getProperty("personality", personality);
            llmBaseUrl    = p.getProperty("llmBaseUrl", llmBaseUrl);
            llmModel      = p.getProperty("llmModel", llmModel);
            llmApiKey     = p.getProperty("llmApiKey", llmApiKey);
            runwayBaseUrl = p.getProperty("runwayBaseUrl", runwayBaseUrl);
            runwayApiKey  = p.getProperty("runwayApiKey", runwayApiKey);
            ttsModel      = p.getProperty("ttsModel", ttsModel);
            ttsVoice      = p.getProperty("ttsVoice", ttsVoice);
            ttsReferenceUrl = p.getProperty("ttsReferenceUrl", ttsReferenceUrl);
            try { ttsVolume = Integer.parseInt(p.getProperty("ttsVolume", String.valueOf(ttsVolume))); } catch (NumberFormatException ignored) {}
            sttBaseUrl    = p.getProperty("sttBaseUrl", sttBaseUrl);
            sttModel      = p.getProperty("sttModel", sttModel);
            sttApiKey     = p.getProperty("sttApiKey", sttApiKey);
            micDeviceName = p.getProperty("micDeviceName", micDeviceName);
            audioOutputDeviceName = p.getProperty("audioOutputDeviceName", audioOutputDeviceName);
            webSearchApiKey = p.getProperty("webSearchApiKey", webSearchApiKey);
        } catch (IOException e) {
            System.err.println("[companion] failed to read config: " + e.getMessage());
        }
    }

    /** Write the current in-memory values back to disk. */
    public static void save() {
        Properties p = new Properties();
        p.setProperty("wakeWord", wakeWord);
        p.setProperty("personality", personality);
        p.setProperty("llmBaseUrl", llmBaseUrl);
        p.setProperty("llmModel", llmModel);
        p.setProperty("llmApiKey", llmApiKey);
        p.setProperty("runwayBaseUrl", runwayBaseUrl);
        p.setProperty("runwayApiKey", runwayApiKey);
        p.setProperty("ttsModel", ttsModel);
        p.setProperty("ttsVoice", ttsVoice);
        p.setProperty("ttsReferenceUrl", ttsReferenceUrl);
        p.setProperty("ttsVolume", String.valueOf(ttsVolume));
        p.setProperty("sttBaseUrl", sttBaseUrl);
        p.setProperty("sttModel", sttModel);
        p.setProperty("sttApiKey", sttApiKey);
        p.setProperty("micDeviceName", micDeviceName);
        p.setProperty("audioOutputDeviceName", audioOutputDeviceName);
        p.setProperty("webSearchApiKey", webSearchApiKey);
        try {
            Files.createDirectories(file().getParent());
            try (var out = Files.newOutputStream(file())) { p.store(out, "Runway Companion config"); }
        } catch (IOException e) {
            System.err.println("[companion] failed to write config: " + e.getMessage());
        }
    }
}
