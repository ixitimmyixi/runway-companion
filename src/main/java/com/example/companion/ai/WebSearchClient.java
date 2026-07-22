package com.example.companion.ai;

import com.example.companion.CompanionConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Web search via the Brave Search API. Needs CompanionConfig.webSearchApiKey. */
public final class WebSearchClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int MAX_RESULTS = 4;

    private WebSearchClient() {}

    public static String search(String query) {
        String key = CompanionConfig.webSearchApiKey;
        if (key == null || key.isBlank()) return "Web search isn't set up (no API key configured).";
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.search.brave.com/res/v1/web/search?q=" + q + "&count=" + MAX_RESULTS;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", key)
                .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return "Web search failed (HTTP " + res.statusCode() + ").";
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!json.has("web")) return "No web results for \"" + query + "\".";
            JsonArray results = json.getAsJsonObject("web").getAsJsonArray("results");
            if (results == null || results.isEmpty()) return "No web results for \"" + query + "\".";
            StringBuilder sb = new StringBuilder("Web results for \"" + query + "\":\n");
            int n = Math.min(MAX_RESULTS, results.size());
            for (int i = 0; i < n; i++) {
                JsonObject r = results.get(i).getAsJsonObject();
                String title = r.has("title") ? r.get("title").getAsString() : "";
                String desc = r.has("description") ? r.get("description").getAsString() : "";
                desc = desc.replaceAll("<[^>]+>", ""); // strip Brave's highlight tags
                sb.append("- ").append(title).append(": ").append(desc).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Web search failed: " + e.getMessage();
        }
    }
}
