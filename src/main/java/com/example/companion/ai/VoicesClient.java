package com.example.companion.ai;

import com.example.companion.CompanionConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class VoicesClient {
    public record Voice(String id, String name, String previewUrl) {
        public String display() { return (name == null || name.isBlank() ? "(unnamed)" : name) + " \u2014 " + id; }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private VoicesClient() {}

    public static List<Voice> list() throws Exception {
        HttpResponse<String> res = get(CompanionConfig.runwayBaseUrl + "/v1/voices");
        if (res.statusCode() / 100 != 2) throw new RuntimeException("voices HTTP " + res.statusCode() + ": " + res.body());

        JsonArray arr = extractArray(JsonParser.parseString(res.body()));
        List<Voice> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String id = firstString(o, "id", "voiceId", "voice_id");
            String name = firstString(o, "name", "displayName");
            String preview = firstString(o, "previewUrl", "preview_url", "previewURL");
            if (id != null) out.add(new Voice(id, name, preview));
        }
        return out;
    }

    public static String previewUrlFor(String idOrName) throws Exception {
        if (idOrName == null || idOrName.isBlank()) return null;
        String needle = idOrName.trim();
        Voice match = null;
        for (Voice v : list()) {
            if (needle.equalsIgnoreCase(v.id())
                    || needle.equalsIgnoreCase(v.name())
                    || needle.equalsIgnoreCase(v.display())) {
                match = v;
                break;
            }
        }
        if (match == null) return null;
        if (match.previewUrl() != null && !match.previewUrl().isBlank()) return match.previewUrl();
        return retrievePreviewUrl(match.id());
    }

    private static String retrievePreviewUrl(String id) throws Exception {
        HttpResponse<String> res = get(CompanionConfig.runwayBaseUrl + "/v1/voices/"
            + URLEncoder.encode(id, StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) return null;
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        return firstString(o, "previewUrl", "preview_url", "previewURL");
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + CompanionConfig.runwayApiKey)
            .header("X-Runway-Version", "2024-11-06")
            .GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
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
