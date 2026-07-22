package com.example.companion.ai;

import com.example.companion.CompanionConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists the org's custom voices via GET /v1/voices. Response shape isn't pinned
 * in the public docs, so parsing is defensive: accept a top-level array, or an
 * object wrapping the array under data/results/voices.
 */
public final class VoicesClient {
    public record Voice(String id, String name) {
        public String display() { return (name == null || name.isBlank() ? "(unnamed)" : name) + " — " + id; }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private VoicesClient() {}

    public static List<Voice> list() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(CompanionConfig.runwayBaseUrl + "/v1/voices"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + CompanionConfig.runwayApiKey)
            .header("X-Runway-Version", "2024-11-06")
            .GET().build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new RuntimeException("voices HTTP " + res.statusCode() + ": " + res.body());

        JsonElement root = JsonParser.parseString(res.body());
        JsonArray arr = extractArray(root);
        List<Voice> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String id = firstString(o, "id", "voiceId", "voice_id");
            String name = firstString(o, "name", "displayName");
            if (id != null) out.add(new Voice(id, name));
        }
        return out;
    }

    private static JsonArray extractArray(JsonElement root) {
        if (root.isJsonArray()) return root.getAsJsonArray();
        JsonObject o = root.getAsJsonObject();
        for (String key : new String[]{"data", "results", "voices"}) {
            if (o.has(key) && o.get(key).isJsonArray()) return o.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    private static String firstString(JsonObject o, String... keys) {
        for (String k : keys) if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString();
        return null;
    }
}
