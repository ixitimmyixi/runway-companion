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
 * voice is spoken via seed_audio, cloning that voice's own preview clip.
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

    // Custom-voice reference clips are expensive to build (download + decode + base64),
    // and identical for a given voice, so we build once and reuse.
    private static volatile String cachedVoice;
    private static volatile String cachedRefUri;

    private RunwayTts() {}

    public static byte[] synthesize(String text) throws Exception {
        String v = CompanionConfig.ttsVoice == null ? "" : CompanionConfig.ttsVoice.trim();

        JsonObject body = new JsonObject();
        body.addProperty("promptText", text);

        if (PRESETS.contains(v)) {
            // Preset path.
            body.addProperty("model", "eleven_multilingual_v2");
            JsonObject voice = new JsonObject();
            voice.addProperty("type", "runway-preset");
            voice.addProperty("presetId", v);
            body.add("voice", voice);
        } else {
            // Custom voice path: clone the voice's preview clip via seed_audio.
            // seed_audio requires a reference clip of AT MOST 30 seconds. If the user hosts
            // their own short clip we use it directly; otherwise we download the voice's
            // preview and trim it to <30s inline (as a data: URI) so nothing needs hosting.
            String audioUri = referenceFor(v);
            body.addProperty("model", "seed_audio");
            JsonObject voice = new JsonObject();
            voice.addProperty("type", "reference-audio");
            voice.addProperty("audioUri", audioUri);
            body.add("voice", voice);
            body.addProperty("outputFormat", "mp3");
        }

        HttpRequest create = HttpRequest.newBuilder()
            .uri(URI.create(BASE + "/v1/text_to_speech"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + CompanionConfig.runwayApiKey)
            .header("X-Runway-Version", VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> res = HTTP.send(create, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2)
            throw new RuntimeException("TTS create HTTP " + res.statusCode() + ": " + res.body());

        JsonObject created = JsonParser.parseString(res.body()).getAsJsonObject();
        String taskId = created.get("id").getAsString();

        String outputUrl = null;
        long deadline = System.currentTimeMillis() + 60_000;
        boolean firstPoll = true;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(firstPoll ? 300 : 500);
            firstPoll = false;
            HttpRequest poll = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/v1/tasks/" + taskId))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + CompanionConfig.runwayApiKey)
                .header("X-Runway-Version", VERSION)
                .GET().build();
            HttpResponse<String> pr = HTTP.send(poll, HttpResponse.BodyHandlers.ofString());
            if (pr.statusCode() / 100 != 2)
                throw new RuntimeException("TTS poll HTTP " + pr.statusCode() + ": " + pr.body());

            JsonObject task = JsonParser.parseString(pr.body()).getAsJsonObject();
            String status = task.get("status").getAsString();
            if ("SUCCEEDED".equals(status)) {
                JsonArray output = task.getAsJsonArray("output");
                if (output != null && output.size() > 0) outputUrl = output.get(0).getAsString();
                break;
            } else if ("FAILED".equals(status)) {
                throw new RuntimeException("TTS task failed: " + pr.body());
            }
        }
        if (outputUrl == null) throw new RuntimeException("TTS timed out or returned no output");

        HttpRequest dl = HttpRequest.newBuilder()
            .uri(URI.create(outputUrl)).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<InputStream> dlr = HTTP.send(dl, HttpResponse.BodyHandlers.ofInputStream());
        if (dlr.statusCode() / 100 != 2)
            throw new RuntimeException("TTS download HTTP " + dlr.statusCode());
        try (InputStream in = dlr.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    /** Resolve the seed_audio reference clip for a custom voice, building+caching it once. */
    private static String referenceFor(String v) throws Exception {
        if (CompanionConfig.ttsReferenceUrl != null && !CompanionConfig.ttsReferenceUrl.isBlank())
            return CompanionConfig.ttsReferenceUrl.trim();

        if (v.equals(cachedVoice) && cachedRefUri != null) return cachedRefUri;

        String previewUrl = VoicesClient.previewUrlFor(v);
        if (previewUrl == null || previewUrl.isBlank()) {
            throw new RuntimeException("No preview clip found for custom voice \"" + v + "\". Make sure it's "
                + "READY on your Runway account, set ttsReferenceUrl to a <=30s clip, or pick a preset voice.");
        }
        String uri = ReferenceClip.dataUriFromPreview(previewUrl);
        cachedVoice = v;
        cachedRefUri = uri;
        return uri;
    }

    /** Optionally pre-build the reference off the game thread so the first line isn't slow. */
    public static void prewarm() {
        try {
            String v = CompanionConfig.ttsVoice == null ? "" : CompanionConfig.ttsVoice.trim();
            if (!PRESETS.contains(v)) referenceFor(v);
        } catch (Exception ignored) {}
    }
}
