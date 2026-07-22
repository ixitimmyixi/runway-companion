package com.example.companion.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Looks up an article intro from the official Minecraft Wiki (minecraft.wiki). No API key needed. */
public final class WikiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int MAX_LEN = 1200;

    private WikiClient() {}

    public static String lookup(String topic) {
        try {
            String q = URLEncoder.encode(topic, StandardCharsets.UTF_8);
            String url = "https://minecraft.wiki/api.php?action=query&format=json&redirects=1"
                + "&generator=search&gsrsearch=" + q + "&gsrlimit=1"
                + "&prop=extracts&exintro=1&explaintext=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "RunwayCompanionMod/0.1 (Minecraft mod)")
                .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return "Wiki lookup failed (HTTP " + res.statusCode() + ").";
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!json.has("query")) return "No wiki article found for \"" + topic + "\".";
            JsonObject pages = json.getAsJsonObject("query").getAsJsonObject("pages");
            for (String k : pages.keySet()) {
                JsonObject page = pages.getAsJsonObject(k);
                String title = page.has("title") ? page.get("title").getAsString() : topic;
                String extract = page.has("extract") ? page.get("extract").getAsString() : "";
                if (!extract.isBlank()) {
                    if (extract.length() > MAX_LEN) extract = extract.substring(0, MAX_LEN) + "...";
                    return "Wiki \u2014 " + title + ": " + extract;
                }
            }
            return "No wiki article found for \"" + topic + "\".";
        } catch (Exception e) {
            return "Wiki lookup failed: " + e.getMessage();
        }
    }
}
