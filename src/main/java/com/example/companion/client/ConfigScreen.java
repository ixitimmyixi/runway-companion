package com.example.companion.client;

import com.example.companion.CompanionConfig;
import com.example.companion.ai.VoicesClient;
import com.example.companion.ai.RunwayTts;
import com.example.companion.audio.MicCapture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigScreen extends Screen {
    private static final int LAST_PAGE = 2;
    private static final String[] PAGE_NAMES = {"General & LLM", "Voice output", "Voice input"};
    private static final String MASK = "Key Hidden";

    private final Screen parent;
    private int page = 0;

    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private ForgeSlider volumeSlider;
    private String micSelected;

    private List<VoicesClient.Voice> fetchedVoices = new ArrayList<>();
    private String status = "";

    private final Set<String> hidden = new HashSet<>();
    private final Map<String, String> maskedValue = new HashMap<>();

    private static final List<String> PRESET_VOICES = List.of(
        "Maya","Arjun","Serene","Bernard","Billy","Mark","Clint","Mabel","Chad","Leslie",
        "Eleanor","Elias","Elliot","Grungle","Brodie","Sandra","Kirk","Kylie","Lara","Lisa",
        "Malachi","Marlene","Martin","Miriam","Monster","Paula","Pip","Rusty","Ragnar","Xylar",
        "Maggie","Jack","Katie","Noah","James","Rina","Ella","Mariah","Frank","Claudia",
        "Niki","Vincent","Kendrick","Myrna","Tom","Wanda","Benjamin","Kiana","Rachel");

    public ConfigScreen(Screen parent) {
        super(Component.literal("Runway Companion"));
        this.parent = parent;
        this.micSelected = CompanionConfig.micDeviceName;
        hidden.add("LLM API key");
        hidden.add("Runway API key");
        hidden.add("STT API key");
    }

    @Override
    protected void init() {
        boxes.clear();
        volumeSlider = null;
        int labelW = 95, boxW = 200;
        int boxX = this.width / 2 - (labelW + boxW) / 2 + labelW;
        int y = 24, step = 18;

        if (page == 0) {
            y = box("Wake word",      CompanionConfig.wakeWord,     boxX, y, boxW) + step;
            y = box("Personality",    CompanionConfig.personality,  boxX, y, boxW) + step;
            y = box("LLM base URL",   CompanionConfig.llmBaseUrl,   boxX, y, boxW) + step;
            y = box("LLM model",      CompanionConfig.llmModel,     boxX, y, boxW) + step;
            y = keyBox("LLM API key", CompanionConfig.llmApiKey,    boxX, y, boxW) + step + 4;
        } else if (page == 1) {
            y = keyBox("Runway API key", CompanionConfig.runwayApiKey, boxX, y, boxW) + step;
            y = box("TTS model",         CompanionConfig.ttsModel,     boxX, y, boxW) + step;
            y = box("TTS voice id",      CompanionConfig.ttsVoice,     boxX, y, boxW) + step;

            volumeSlider = new ForgeSlider(boxX, y, boxW, 18,
                Component.literal("Volume: "), Component.literal("%"),
                0, 100, CompanionConfig.ttsVolume, 1, 0, true);
            addRenderableWidget(volumeSlider);
            y += step + 2;

            List<String> displayVoices = new ArrayList<>(PRESET_VOICES);
            Map<String, String> valueOf = new LinkedHashMap<>();
            for (String p : PRESET_VOICES) valueOf.put(p, p);
            for (VoicesClient.Voice v : fetchedVoices) {
                String nm = (v.name() == null || v.name().isBlank()) ? v.id() : v.name();
                String label = nm + " (custom)";
                if (!valueOf.containsKey(label)) { displayVoices.add(label); valueOf.put(label, v.id()); }
            }
            String curVal = CompanionConfig.ttsVoice;
            String initial = displayVoices.get(0);
            for (Map.Entry<String, String> e : valueOf.entrySet())
                if (e.getValue().equals(curVal)) { initial = e.getKey(); break; }
            CycleButton<String> picker = CycleButton.<String>builder(Component::literal)
                .withValues(displayVoices).withInitialValue(initial)
                .create(boxX, y, boxW, 18, Component.literal("Voice"), (btn, val) -> {
                    EditBox vb = boxes.get("TTS voice id");
                    if (vb != null) vb.setValue(valueOf.getOrDefault(val, val));
                });
            addRenderableWidget(picker);
            y += step + 2;

            addRenderableWidget(Button.builder(Component.literal("Fetch my custom voices"), b -> fetchVoices())
                .bounds(boxX, y, boxW, 18).build());
            y += step + 4;
        } else {
            List<String> devices = new ArrayList<>();
            devices.add("(system default)");
            devices.addAll(MicCapture.listInputDevices());
            String initial = (micSelected == null || micSelected.isBlank()) ? devices.get(0)
                : (devices.contains(micSelected) ? micSelected : devices.get(0));
            CycleButton<String> mic = CycleButton.<String>builder(Component::literal)
                .withValues(devices).withInitialValue(initial)
                .create(boxX, y, boxW, 18, Component.literal("Mic device"),
                    (btn, val) -> micSelected = val.equals("(system default)") ? "" : val);
            addRenderableWidget(mic);
            y += step + 2;
            y = box("STT base URL", CompanionConfig.sttBaseUrl, boxX, y, boxW) + step;
            y = box("STT model",    CompanionConfig.sttModel,   boxX, y, boxW) + step;
            y = keyBox("STT API key", CompanionConfig.sttApiKey, boxX, y, boxW) + step + 4;
        }

        bottomBar(y);
    }

    private void bottomBar(int y) {
        if (page > 0)
            addRenderableWidget(Button.builder(Component.literal("\u25C0 Back"), b -> switchPage(page - 1))
                .bounds(this.width / 2 - 154, y, 100, 18).build());
        if (page < LAST_PAGE)
            addRenderableWidget(Button.builder(Component.literal("Next \u25B6"), b -> switchPage(page + 1))
                .bounds(this.width / 2 + 54, y, 100, 18).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
            .bounds(this.width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(this.width / 2 + 4, y, 150, 20).build());
    }

    private int box(String label, String value, int boxX, int y, int boxW) {
        EditBox b = new EditBox(this.font, boxX, y, boxW, 16, Component.literal(label));
        b.setMaxLength(1024);
        b.setValue(value == null ? "" : value);
        addRenderableWidget(b);
        boxes.put(label, b);
        return y;
    }

    private int keyBox(String label, String value, int boxX, int y, int boxW) {
        String real = value == null ? "" : value;
        boolean isHidden = hidden.contains(label);
        int fieldW = boxW - 54;

        EditBox b = new EditBox(this.font, boxX, y, fieldW, 16, Component.literal(label));
        b.setMaxLength(1024);
        if (isHidden) {
            maskedValue.put(label, real);
            b.setValue(MASK);
            b.setEditable(false);
        } else {
            b.setValue(real);
            b.setEditable(true);
        }
        addRenderableWidget(b);
        boxes.put(label, b);

        addRenderableWidget(Button.builder(Component.literal(isHidden ? "Show" : "Hide"), btn -> {
            captureInto();
            if (hidden.contains(label)) hidden.remove(label); else hidden.add(label);
            rebuildWidgets();
        }).bounds(boxX + fieldW + 4, y, 50, 16).build());

        return y;
    }

    private void captureInto() {
        apply("Wake word",      v -> CompanionConfig.wakeWord = v);
        apply("Personality",    v -> CompanionConfig.personality = v);
        apply("LLM base URL",   v -> CompanionConfig.llmBaseUrl = v);
        apply("LLM model",      v -> CompanionConfig.llmModel = v);
        applyKey("LLM API key", v -> CompanionConfig.llmApiKey = v);
        applyKey("Runway API key", v -> CompanionConfig.runwayApiKey = v);
        apply("TTS model",      v -> CompanionConfig.ttsModel = v);
        apply("TTS voice id",   v -> CompanionConfig.ttsVoice = v);
        apply("STT base URL",   v -> CompanionConfig.sttBaseUrl = v);
        apply("STT model",      v -> CompanionConfig.sttModel = v);
        applyKey("STT API key", v -> CompanionConfig.sttApiKey = v);
        if (volumeSlider != null) CompanionConfig.ttsVolume = (int) volumeSlider.getValue();
        if (micSelected != null) CompanionConfig.micDeviceName = micSelected;
    }

    private void apply(String key, java.util.function.Consumer<String> setter) {
        EditBox b = boxes.get(key);
        if (b != null) setter.accept(b.getValue());
    }

    private void applyKey(String key, java.util.function.Consumer<String> setter) {
        if (hidden.contains(key)) {
            if (maskedValue.containsKey(key)) setter.accept(maskedValue.get(key));
            return;
        }
        EditBox b = boxes.get(key);
        if (b != null) setter.accept(b.getValue());
    }

    private void switchPage(int p) { captureInto(); page = p; status = ""; rebuildWidgets(); }

    private void save() { captureInto(); CompanionConfig.save(); RunwayTts.prewarm(); onClose(); }

    private void fetchVoices() {
        captureInto();
        status = "Fetching voices...";
        new Thread(() -> {
            try {
                List<VoicesClient.Voice> v = VoicesClient.list();
                Minecraft.getInstance().execute(() -> {
                    fetchedVoices = v;
                    status = v.isEmpty() ? "No custom voices found" : (v.size() + " custom voice(s) added");
                    rebuildWidgets();
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status = "Error: " + e.getMessage());
            }
        }, "companion-voices").start();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font,
            "Runway Companion \u2014 " + PAGE_NAMES[page] + "  (" + (page + 1) + "/3)",
            this.width / 2, 8, 0xFFFFFF);
        int labelX = this.width / 2 - (95 + 200) / 2;
        for (Map.Entry<String, EditBox> e : boxes.entrySet())
            g.drawString(this.font, e.getKey(), labelX, e.getValue().getY() + 4, 0xC0C0C0);
        super.render(g, mouseX, mouseY, partialTick);
        if (page == 1 && !status.isEmpty())
            g.drawCenteredString(this.font, Component.literal(status), this.width / 2, this.height - 8, 0xA0A0A0);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }
}
