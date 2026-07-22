package com.example.companion;

import com.example.companion.ai.LlmClient;
import com.example.companion.ai.RunwayTts;
import com.example.companion.ai.WebSearchClient;
import com.example.companion.ai.WikiClient;
import com.example.companion.audio.AudioPlayer;
import com.example.companion.game.GameContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared reply flow for typed and voice input: memory, locate/command actions, and lookups. */
public final class Pipeline {
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "companion-ai"); t.setDaemon(true); return t;
    });

    /** One conversation turn. */
    public record Turn(String role, String content) {}

    private static final int MAX_TURNS = 12;
    private static final Deque<Turn> HISTORY = new ArrayDeque<>();

    private static final Pattern LOCATE_TAG =
        Pattern.compile("<locate>\\s*([a-zA-Z_]+)\\s*</locate>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CMD_TAG =
        Pattern.compile("<cmd>\\s*([^<]+?)\\s*</cmd>", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_TAG =
        Pattern.compile("<wiki>\\s*([^<]+?)\\s*</wiki>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_TAG =
        Pattern.compile("<search>\\s*([^<]+?)\\s*</search>", Pattern.CASE_INSENSITIVE);

    // Command roots that change the world irreversibly -> require confirmation first.
    private static final Set<String> DESTRUCTIVE = Set.of(
        "fill", "clone", "kill", "execute", "place", "forceload", "datapack", "structure", "clear", "setblock");

    // A destructive command waiting on the player's yes/no.
    private static String pendingCommand;
    private static String pendingRequest;

    private Pipeline() {}

    public static void handle(String userText) {
        if (userText == null || userText.isBlank()) return;

        // If a destructive command is awaiting confirmation, treat this message as the answer.
        String pendCmd, pendReq;
        synchronized (HISTORY) { pendCmd = pendingCommand; pendReq = pendingRequest; }
        if (pendCmd != null) {
            synchronized (HISTORY) { pendingCommand = null; pendingRequest = null; }
            if (isAffirmative(userText)) {
                POOL.submit(() -> runAndSpeak(pendReq, pendCmd));
                return;
            }
            echo("\u00A77(cancelled)");
            // not a yes -> fall through and answer the new message normally
        }

        String context = GameContext.snapshot();
        List<Turn> priorTurns;
        synchronized (HISTORY) {
            priorTurns = new ArrayList<>(HISTORY);
            record(new Turn("user", userText));
        }

        POOL.submit(() -> {
            try {
                String reply = LlmClient.reply(userText, context, priorTurns);
                if (reply == null) reply = "";

                String target = parseGroup(LOCATE_TAG, reply);
                if (target != null) { handleLocate(userText, target); return; }

                String cmd = parseGroup(CMD_TAG, reply);
                if (cmd != null) { handleCommand(userText, cmd); return; }

                String wiki = parseGroup(WIKI_TAG, reply);
                if (wiki != null) { handleLookup(userText, wiki, "wiki"); return; }

                String search = parseGroup(SEARCH_TAG, reply);
                if (search != null) { handleLookup(userText, search, "search"); return; }

                if (reply.isBlank()) reply = "...";
                speak(reply);
            } catch (Exception e) {
                echo("(companion error: " + e.getMessage() + ")");
            }
        });
    }

    private static void handleCommand(String userText, String command) {
        try {
            String cmd = command.strip();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (isDestructive(cmd)) {
                synchronized (HISTORY) { pendingCommand = cmd; pendingRequest = userText; }
                speak("That would change your world and can't be undone \u2014 want me to run \u00A7f/"
                    + cmd + "\u00A7r? Say yes to confirm.");
                return;
            }
            runAndSpeak(userText, cmd);
        } catch (Exception e) {
            echo("(companion error: " + e.getMessage() + ")");
        }
    }

    private static void runAndSpeak(String userText, String cmd) {
        try {
            echo("\u00A77(Bufo runs /" + cmd + ")");
            String result;
            try { result = BufoActions.run(cmd); }
            catch (Exception ex) { result = "The command failed: " + ex.getMessage(); }
            String spoken = LlmClient.phraseCommandResult(userText, result);
            if (spoken == null || spoken.isBlank()) spoken = "Done.";
            speak(spoken);
        } catch (Exception e) {
            echo("(companion error: " + e.getMessage() + ")");
        }
    }

    private static void handleLocate(String userText, String target) {
        try {
            String result;
            if (!BufoActions.canLocate(target)) {
                result = "I can't locate \"" + target + "\".";
            } else {
                echo("\u00A77(Bufo looks for the nearest " + target.replace('_', ' ') + "...)");
                try { result = BufoActions.locate(target); }
                catch (Exception ex) { result = "The lookup failed: " + ex.getMessage(); }
            }
            String spoken = LlmClient.phraseCommandResult(userText, result);
            if (spoken == null || spoken.isBlank()) spoken = "Hmm, I couldn't work that out.";
            speak(spoken);
        } catch (Exception e) {
            echo("(companion error: " + e.getMessage() + ")");
        }
    }

    private static void handleLookup(String userText, String query, String kind) {
        try {
            echo("\u00A77(Bufo looks up \"" + query + "\"...)");
            String info = "wiki".equals(kind) ? WikiClient.lookup(query) : WebSearchClient.search(query);
            String spoken = LlmClient.answerWithContext(userText, info);
            if (spoken == null || spoken.isBlank()) spoken = "I couldn't find a clear answer on that.";
            speak(spoken);
        } catch (Exception e) {
            echo("(companion error: " + e.getMessage() + ")");
        }
    }

    private static boolean isDestructive(String cmd) {
        String root = cmd.trim().toLowerCase().split("\\s+")[0];
        return DESTRUCTIVE.contains(root);
    }

    private static boolean isAffirmative(String s) {
        String t = s.trim().toLowerCase();
        return t.equals("yes") || t.equals("y") || t.equals("yeah") || t.equals("yep") || t.equals("yup")
            || t.equals("sure") || t.equals("ok") || t.equals("okay") || t.equals("confirm")
            || t.startsWith("yes") || t.startsWith("do it") || t.startsWith("go ahead")
            || t.startsWith("please do") || t.contains("go for it");
    }

    /** Record, echo, synthesize and play a spoken reply. */
    private static void speak(String text) throws Exception {
        synchronized (HISTORY) { record(new Turn("assistant", text)); }
        echo(text);
        AudioPlayer.play(RunwayTts.synthesize(text));
    }

    private static String parseGroup(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : null;
    }

    private static void record(Turn t) {
        HISTORY.addLast(t);
        while (HISTORY.size() > MAX_TURNS) HISTORY.removeFirst();
    }

    /** Clear conversation memory. */
    public static void clearHistory() {
        synchronized (HISTORY) { HISTORY.clear(); pendingCommand = null; pendingRequest = null; }
    }

    public static void echo(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null)
                mc.player.displayClientMessage(
                    Component.literal("\u00A7b" + CompanionConfig.wakeWord + "\u00A7r: " + text), false);
        });
    }
}
