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

/** Runway text-to-speech (eleven_multilingual_v2): preset voices only. */
public final class RunwayTts {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String BASE = "https://api.dev.runwayml.com";
    private static final String VERSION = "2024-11-06";

    // The only voices Runway's TTS endpoint accepts for this model.
    private static final Set<String> PRESETS = Set.of(
        "Maya","Arjun","Serene","Bernard","Billy","Mark","Clint","Mabel","Chad","Leslie",
        "Eleanor","Elias","Elliot","Grungle","Brodie","Sandra","Kirk","Kylie","Lara","Lisa",
        "Malachi","Marlene","Martin","Miriam","Monster","Paula","Pip","Rusty","Ragnar","Xylar",
        "Maggie","Jack","Katie","Noah","James","Rina","Ella","Mariah","Frank","Claudia",
        "Niki","Vincent","Kendrick","Myrna","Tom","Wanda","Benjamin","Kiana","Rachel");

    private RunwayTts() {}

    public static byte[] synthesize(String text) throws Exception {
        String v = CompanionConfig.ttsVoice == null ? "" : CompanionConfig.ttsVoice.trim();
        if (!PRESETS.contains(v)) {
            throw new RuntimeException("\"" + v + "\" isn't a Runway preset. Runway's text-to-speech "
                + "only supports preset voices (Maya, Vincent, Rachel, ...); custom/trained voices "
                + "aren't available through this API. Pick a preset in the config.");
        }

        JsonObject voice = new JsonObject();
        voice.addProperty("type", "runway-preset");
        voice.addProperty("presetId", v);

        JsonObject body = new JsonObject();
        body.addProperty("model", CompanionConfig.ttsModel);
        body.addProperty("promptText", text);
        body.add("voice", voice);

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
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1000);
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
}
