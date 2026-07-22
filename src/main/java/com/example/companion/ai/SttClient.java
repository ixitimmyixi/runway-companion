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

/**
 * Sends WAV audio to an OpenAI-compatible transcription endpoint
 * (POST {sttBaseUrl}/audio/transcriptions), e.g. Groq's free Whisper.
 * Returns the transcribed text.
 */
public final class SttClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private SttClient() {}

    /** Use the STT key, or fall back to the LLM key (one Groq key can serve both). */
    private static String key() {
        return CompanionConfig.sttApiKey != null && !CompanionConfig.sttApiKey.isBlank()
            ? CompanionConfig.sttApiKey : CompanionConfig.llmApiKey;
    }

    public static String transcribe(byte[] wav) throws Exception {
        String boundary = "----companion" + System.currentTimeMillis();
        byte[] body = multipart(boundary, wav);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(CompanionConfig.sttBaseUrl + "/audio/transcriptions"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + key())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new RuntimeException("STT HTTP " + res.statusCode() + ": " + res.body());

        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        return json.has("text") ? json.get("text").getAsString().trim() : "";
    }

    private static byte[] multipart(String boundary, byte[] wav) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String dash = "--";
        // model field
        out.write((dash + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write((CompanionConfig.sttModel + "\r\n").getBytes(StandardCharsets.UTF_8));
        // file field
        out.write((dash + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(wav);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        // end
        out.write((dash + boundary + dash + "\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
