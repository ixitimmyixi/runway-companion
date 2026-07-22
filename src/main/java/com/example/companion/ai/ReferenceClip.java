package com.example.companion.ai;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Turns a voice's (possibly long) preview clip into a short mono WAV data URI
 * usable as a seed_audio reference, which must be at most 30 seconds. We decode
 * the MP3 with JLayer, keep the first {@link #MAX_SECONDS}, downmix to mono and
 * optionally halve the rate to keep the inline payload small.
 */
public final class ReferenceClip {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private static final double MAX_SECONDS = 24.0; // safely under the 30s cap

    private ReferenceClip() {}

    public static String dataUriFromPreview(String previewUrl) throws Exception {
        byte[] mp3 = download(previewUrl);

        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream monoPcm = new ByteArrayOutputStream();

        int sampleRate = 44100, channels = 2;
        boolean gotFmt = false;
        long maxFrames = Long.MAX_VALUE; // mono sample frames to keep
        long written = 0;
        Header h;
        while ((h = bitstream.readFrame()) != null) {
            SampleBuffer buf = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            if (!gotFmt) {
                sampleRate = buf.getSampleFrequency();
                channels = Math.max(1, buf.getChannelCount());
                maxFrames = (long) (sampleRate * MAX_SECONDS);
                gotFmt = true;
            }
            short[] s = buf.getBuffer();
            int n = buf.getBufferLength();
            for (int i = 0; i + channels - 1 < n; i += channels) {
                int sum = 0;
                for (int c = 0; c < channels; c++) sum += s[i + c];
                int m = sum / channels;
                monoPcm.write(m & 0xFF);
                monoPcm.write((m >> 8) & 0xFF);
                if (++written >= maxFrames) break;
            }
            bitstream.closeFrame();
            if (written >= maxFrames) break;
        }
        bitstream.close();

        byte[] pcm = monoPcm.toByteArray();
        if (sampleRate > 24000) { pcm = halve(pcm); sampleRate /= 2; }
        byte[] wav = wrapWav(pcm, sampleRate, 1);
        return "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav);
    }

    private static byte[] download(String url) throws Exception {
        HttpResponse<byte[]> r = HTTP.send(
            HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) throw new RuntimeException("preview download HTTP " + r.statusCode());
        return r.body();
    }

    /** Average adjacent 16-bit LE mono samples, halving the sample rate. */
    private static byte[] halve(byte[] pcm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i + 3 < pcm.length; i += 4) {
            short a = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            short b = (short) ((pcm[i + 2] & 0xFF) | (pcm[i + 3] << 8));
            int m = (a + b) / 2;
            out.write(m & 0xFF);
            out.write((m >> 8) & 0xFF);
        }
        return out.toByteArray();
    }

    private static byte[] wrapWav(byte[] pcm, int sampleRate, int channels) {
        int byteRate = sampleRate * channels * 2;
        int dataLen = pcm.length;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        writeStr(o, "RIFF"); writeInt(o, 36 + dataLen); writeStr(o, "WAVE");
        writeStr(o, "fmt "); writeInt(o, 16); writeShort(o, 1); writeShort(o, channels);
        writeInt(o, sampleRate); writeInt(o, byteRate); writeShort(o, channels * 2); writeShort(o, 16);
        writeStr(o, "data"); writeInt(o, dataLen);
        o.writeBytes(pcm);
        return o.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream o, String s) { for (int i = 0; i < s.length(); i++) o.write(s.charAt(i)); }
    private static void writeInt(ByteArrayOutputStream o, int v) { o.write(v & 0xFF); o.write((v >> 8) & 0xFF); o.write((v >> 16) & 0xFF); o.write((v >> 24) & 0xFF); }
    private static void writeShort(ByteArrayOutputStream o, int v) { o.write(v & 0xFF); o.write((v >> 8) & 0xFF); }
}
