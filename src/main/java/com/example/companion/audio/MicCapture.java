package com.example.companion.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures microphone audio into a WAV byte[] (16 kHz mono 16-bit — what Whisper
 * expects). Push-to-talk drives start()/stop().
 */
public final class MicCapture {
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    private TargetDataLine line;
    private Thread thread;
    private volatile boolean recording;
    private ByteArrayOutputStream buffer;

    /** Names of input devices that can record our format. */
    public static List<String> listInputDevices() {
        List<String> names = new ArrayList<>();
        DataLine.Info want = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(info).isLineSupported(want)) names.add(info.getName());
            } catch (Exception ignored) {}
        }
        return names;
    }

    public synchronized void start(String deviceName) throws LineUnavailableException {
        if (recording) return;
        line = openLine(deviceName);
        line.open(FORMAT);
        line.start();
        buffer = new ByteArrayOutputStream();
        recording = true;
        thread = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (recording) {
                int n = line.read(buf, 0, buf.length);
                if (n > 0) synchronized (buffer) { buffer.write(buf, 0, n); }
            }
        }, "companion-mic");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops recording and returns the captured audio as a WAV byte[]. */
    public synchronized byte[] stop() throws Exception {
        if (!recording) return new byte[0];
        recording = false;
        try { thread.join(500); } catch (InterruptedException ignored) {}
        line.stop();
        line.close();

        byte[] pcm;
        synchronized (buffer) { pcm = buffer.toByteArray(); }
        AudioInputStream ais = new AudioInputStream(
            new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    private TargetDataLine openLine(String deviceName) throws LineUnavailableException {
        if (deviceName != null && !deviceName.isBlank()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(deviceName)) {
                    return (TargetDataLine) AudioSystem.getMixer(info)
                        .getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
                }
            }
        }
        return AudioSystem.getTargetDataLine(FORMAT); // system default
    }
}
