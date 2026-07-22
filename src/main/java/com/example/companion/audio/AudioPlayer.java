package com.example.companion.audio;

import com.example.companion.CompanionConfig;
import com.example.companion.Pipeline;
import com.example.companion.client.BufoSpeakingState;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Plays TTS audio one clip at a time (queued). Decodes MP3 with JLayer. */
public final class AudioPlayer {
    // Clips are queued and played by a single worker so they never overlap.
    private static final BlockingQueue<byte[]> QUEUE = new LinkedBlockingQueue<>();
    private static volatile SourceDataLine currentLine;
    private static volatile Clip currentClip;
    private static volatile boolean stopFlag;

    static {
        Thread worker = new Thread(AudioPlayer::runLoop, "companion-audio");
        worker.setDaemon(true);
        worker.start();
    }

    private AudioPlayer() {}

    /** Names of output devices that can play back audio (for the config picker). */
    public static List<String> listOutputDevices() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                for (Line.Info li : mixer.getSourceLineInfo()) {
                    if (SourceDataLine.class.isAssignableFrom(li.getLineClass())) { names.add(info.getName()); break; }
                }
            } catch (Exception ignored) {}
        }
        return names;
    }

    /** Queue a clip for playback. */
    public static void play(byte[] audio) {
        if (audio == null || audio.length == 0) { Pipeline.echo("\u00A77(audio: 0 bytes)"); return; }
        QUEUE.offer(audio);
    }

    /** Stop whatever is playing now and drop anything queued (e.g. the player starts talking). */
    public static void stop() {
        QUEUE.clear();
        stopFlag = true;
        SourceDataLine l = currentLine;
        if (l != null) { try { l.stop(); l.flush(); } catch (Exception ignored) {} }
        Clip c = currentClip;
        if (c != null) { try { c.stop(); c.flush(); } catch (Exception ignored) {} }
    }

    private static void runLoop() {
        while (true) {
            byte[] audio;
            try { audio = QUEUE.take(); } catch (InterruptedException e) { continue; }
            stopFlag = false;
            try {
                BufoSpeakingState.start();
                if (isMp3(audio)) playMp3(audio);
                else playOther(audio);
            } catch (Exception e) {
                Pipeline.echo("\u00A77(audio error: " + e.getMessage() + ")");
            } finally {
                BufoSpeakingState.stop();
            }
        }
    }

    private static boolean isMp3(byte[] a) {
        int b0 = a[0] & 0xFF, b1 = a.length > 1 ? a[1] & 0xFF : 0;
        return (a.length >= 3 && a[0] == 'I' && a[1] == 'D' && a[2] == '3') || (b0 == 0xFF && (b1 & 0xE0) == 0xE0);
    }

    private static void playMp3(byte[] mp3) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int sampleRate = 44100, channels = 2;
        boolean gotFmt = false;
        Header h;
        while ((h = bitstream.readFrame()) != null) {
            SampleBuffer buf = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            if (!gotFmt) { sampleRate = buf.getSampleFrequency(); channels = buf.getChannelCount(); gotFmt = true; }
            short[] s = buf.getBuffer();
            int n = buf.getBufferLength();
            for (int i = 0; i < n; i++) { pcm.write(s[i] & 0xFF); pcm.write((s[i] >> 8) & 0xFF); }
            bitstream.closeFrame();
        }
        bitstream.close();

        byte[] data = pcm.toByteArray();
        AudioFormat fmt = new AudioFormat(sampleRate, 16, channels, true, false);
        SourceDataLine line = openSourceLine(fmt);
        currentLine = line;
        try {
            applyVolume(line, CompanionConfig.ttsVolume);
            line.start();
            int off = 0;
            while (off < data.length && !stopFlag) {
                int len = Math.min(8192, data.length - off);
                int wrote = line.write(data, off, len);
                if (wrote <= 0) break;
                off += wrote;
            }
            if (!stopFlag) line.drain();
        } finally {
            try { line.stop(); line.close(); } catch (Exception ignored) {}
            currentLine = null;
        }
    }

    private static void playOther(byte[] audio) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(audio)))) {
            Clip clip = openClip(in.getFormat());
            currentClip = clip;
            try {
                clip.open(in);
                applyVolume(clip, CompanionConfig.ttsVolume);
                clip.start();
                long end = System.currentTimeMillis() + clip.getMicrosecondLength() / 1000 + 200;
                while (System.currentTimeMillis() < end && !stopFlag && clip.isRunning()) Thread.sleep(20);
            } finally {
                try { clip.stop(); clip.close(); } catch (Exception ignored) {}
                currentClip = null;
            }
        }
    }

    /** Open a playback line on the configured output device, falling back to the system default. */
    private static SourceDataLine openSourceLine(AudioFormat fmt) throws Exception {
        String dev = CompanionConfig.audioOutputDeviceName;
        if (dev != null && !dev.isBlank()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(dev)) {
                    try {
                        SourceDataLine l = AudioSystem.getSourceDataLine(fmt, info);
                        l.open(fmt);
                        return l;
                    } catch (Exception ignored) { break; } // fall through to default
                }
            }
        }
        SourceDataLine l = AudioSystem.getSourceDataLine(fmt);
        l.open(fmt);
        return l;
    }

    private static Clip openClip(AudioFormat fmt) throws Exception {
        String dev = CompanionConfig.audioOutputDeviceName;
        if (dev != null && !dev.isBlank()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(dev)) {
                    try {
                        return (Clip) AudioSystem.getMixer(info).getLine(new DataLine.Info(Clip.class, fmt));
                    } catch (Exception ignored) { break; } // fall through to default
                }
            }
        }
        return AudioSystem.getClip();
    }

    private static void applyVolume(javax.sound.sampled.Line line, int vol) {
        try {
            if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl g = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float v = Math.max(0, Math.min(100, vol)) / 100f;
            float dB = (v <= 0f) ? g.getMinimum() : (float) (20.0 * Math.log10(v));
            g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), dB)));
        } catch (Exception ignored) {}
    }
}
