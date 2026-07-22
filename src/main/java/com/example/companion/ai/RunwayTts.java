package com.example.companion.ai;

import com.example.companion.CompanionConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Runway text-to-speech. Preset voices use eleven_multilingual_v2; a custom
 * voice is spoken via seed_audio, cloning a <=30s reference clip.
 */
public final class RunwayTts {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String BASE = "https://api.dev.runwayml.com";
    private static final String VERSION = "2024-11-06";

    // Preset voices accepted by eleven_multilingual_v2.
    private static final Set<String> PRESETS = Set.of(
        "Maya","Arjun","Serene","Bernard","Billy","Mark","Clint","Mabel","Chad","Leslie",
        "Eleanor","Elias","Elliot","Grungle","Brodie","Sandra","Kirk","Kylie","Lara","Lisa",
        "Malachi","Marlene","Martin","Miriam","Monster","Paula","Pip","Rusty","Ragnar","Xylar",
        "Maggie","Jack","Katie","Noah","James","Rina","Ella","Mariah","Frank","Claudia",
        "Niki","Vincent","Kendrick","Myrna","Tom","Wanda","Benjamin","Kiana","Rachel");

    private RunwayTts() {}

    public static byte[] synthesize(String text) throws Exception {
        String v = CompanionConfig.ttsVoice == null ? "" : CompanionConfig.ttsVoice.trim();

        JsonObject body = new JsonObject();
        body.addProperty("promptText", text);

        if (PRESETS.contains(v)) {
            body.addProperty("model", "eleven_multilingual_v2");
            JsonObject voice = new JsonObject();
            voice.addProperty("type", "runway-preset");
            voice.addProperty("presetId", v);
            body.add("voice", voice);
        } else {
            // seed_audio requires a reference clip of AT MOST 30 seconds. If the user hosts
            // their own short clip we use it directly; otherwise we download the voice's
            // preview and trim it to <30s inline (as a data: URI) so nothing needs hosting.
            String audioUri;
            if (CompanionConfig.ttsReferenceUrl != null && !CompanionConfig.ttsReferenceUrl.isBlank()) {
                audioUri = CompanionConfig.ttsReferenceUrl.trim();
            } else {
