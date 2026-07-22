package com.example.companion.ai;

import com.example.companion.CompanionConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Ephemeral uploads: POST /v1/uploads to get a presigned target, upload the
 * bytes, and receive a runway:// URI usable anywhere a URL/data URI is accepted.
 * URIs are valid ~24h, so callers should cache and refresh.
 */
public final class RunwayUploads {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private RunwayUploads() {}

    /** Upload bytes and return the runway:// URI. Throws on any failure. */
    public static String uploadEphemeral(byte[] data, String filename) throws Exception {
        // 1) ask Runway for an upload target
        JsonObject reqBody = new JsonObject();
        reqBody.addProperty("filename", filename);
        reqBody.addProperty("type", "ephemeral");

        HttpRequest start = HttpRequest.newBuilder()
            .uri(URI.create(CompanionConfig.runwayBaseUrl + "/v1/uploads"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + CompanionConfig.runwayApiKey)
            .header("X-Runway-Version", "2024-11-06")
            .POST(HttpRequest.BodyPublishers.ofString(reqBody.toString()))
            .build();

        HttpResponse<String> sr = HTTP.send(start, HttpResponse.BodyHandlers.ofString());
        if (sr.statusCode() / 100 != 2) throw new RuntimeException("uploads init HTTP " + sr.statusCode() + ": " + sr.body());

        JsonObject o = JsonParser.parseString(sr.body()).getAsJsonObject();
        String uploadUrl = o.get("uploadUrl").getAsString();
        String runwayUri = o.get("runwayUri").getAsString();
        JsonObject fields = o.has("fields") && o.get("fields").isJsonObject() ? o.getAsJsonObject("fields") : new JsonObject();

        // 2) multipart/form-data POST to the presigned URL: all fields first, file last
        String boundary = "----companion" + System.nanoTime();
        ByteArrayOutputStream mp = new ByteArrayOutputStream();
        for (Map.Entry<String, com.google.gson.JsonElement> e : fields.entrySet()) {
            writePart(mp, boundary, e.getKey(), e.getValue().getAsString());
        }
        writeFilePart(mp, boundary, "file", filename, data);
        mp.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest put = HttpRequest.newBuilder()
            .uri(URI.create(uploadUrl))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(mp.toByteArray()))
            .build();

        HttpResponse<String> ur = HTTP.send(put, HttpResponse.BodyHandlers.ofString());
        if (ur.statusCode() / 100 != 2) throw new RuntimeException("upload PUT HTTP " + ur.statusCode() + ": " + ur.body());

        return runwayUri;
    }

    private static void writePart(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(ByteArrayOutputStream out, String boundary, String name, String filename, byte[] data) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
