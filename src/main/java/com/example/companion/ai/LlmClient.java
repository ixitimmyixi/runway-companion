package com.example.companion.ai;

import com.example.companion.CompanionConfig;
import com.example.companion.Pipeline;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** OpenAI-compatible chat client (Groq or Anthropic), with memory, locate + lookup actions. */
public final class LlmClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String MC_VERSION = "1.20.1 (Java Edition)";

    private LlmClient() {}

    private static String key() {
        return CompanionConfig.llmApiKey != null && !CompanionConfig.llmApiKey.isBlank()
            ? CompanionConfig.llmApiKey : CompanionConfig.sttApiKey;
    }

    public static String reply(String userText, String gameContext, List<Pipeline.Turn> history) throws Exception {
        JsonArray messages = new JsonArray();

        String systemPrompt = CompanionConfig.personality
            + "\n\nYou're hanging out in the game with the player like a friend. Be relaxed, warm, and brief."
            + " Match their energy: if they just say \"hello,\" say hi back and maybe ask what's up \u2014"
            + " don't launch into a status report."
            + "\n\nYour body: You are NOT a disembodied voice. You have a real body in the world \u2014 a small floating creature with wings that hovers and follows the player around, and they can see you in-game. Never say you lack a physical form, a sprite, or a body. If the player can't see you, asks where you are, or wants you to come to them, bring your body to them by replying with EXACTLY <cmd>tp @e[type=companion:bufo,limit=1,sort=nearest] @p</cmd> and nothing else."
            + "\n\nStyle:"
            + "\n- Usually 1-2 sentences; never more than 3."
            + "\n- Do NOT narrate the world, time of day, coordinates, health, or biome unless the player asks"
            + " or it's directly relevant to what they just said. Treat that info as background, not something to recite."
            + "\n- Just talk \u2014 no bullet points or lists."
            + "\n\nAccuracy:"
            + "\n- This is Minecraft " + MC_VERSION + ". Only give advice correct for that version."
            + "\n- Only say what you're sure of. You don't know the world seed or where anything is unless told;"
            + " never invent coordinates, structure locations, or crafting recipes. If unsure, say so or suggest"
            + " an in-game way to find out."
            + "\n\nAbility \u2014 locating structures: You really CAN find the nearest structure for the player."
            + " When they ask where the nearest one is, respond with EXACTLY a tag and nothing else, like"
            + " <locate>village</locate>. This is a real command that runs in the game and returns the location"
            + " to you afterward \u2014 it is NOT pretend, so never claim you lack access to commands. Available"
            + " names: village, pillager_outpost, mansion, monument, stronghold, nether_fortress, bastion,"
            + " end_city, ancient_city, mineshaft, ruined_portal, shipwreck, buried_treasure, desert_pyramid,"
            + " jungle_pyramid, igloo, swamp_hut, trail_ruins. If they ask for something not on that list, tell"
            + " them you can only locate those specific structures."
            + "\n\nAbility \u2014 looking things up: You can fetch real, current Minecraft info instead of guessing."
            + " Reply with EXACTLY one tag and nothing else:"
            + "\n- <wiki>TOPIC</wiki> for anything on the Minecraft Wiki: mechanics, blocks, items, mobs,"
            + " redstone components, enchantments, brewing, version changes. Example: <wiki>redstone comparator</wiki>."
            + "\n- <search>QUERY</search> for things NOT on the wiki: content creators, specific mods, community"
            + " builds, recent updates or news. Example: <search>best create mod farms</search>."
            + " After a tag, the real result is sent back to you and you answer from it. Only use a tag when the"
            + " player wants factual or current info you are not sure of \u2014 for casual chat, just reply normally."
            + "\n\nAbility \u2014 running commands: You can run Minecraft commands in the player's"
            + " singleplayer world to help with what they ask \u2014 change time or weather, give items,"
            + " teleport, effects, gamemode, enchant, summon, build, and so on. Reply with EXACTLY the command"
            + " in a tag and nothing else, with NO leading slash, like <cmd>time set day</cmd> or"
            + " <cmd>give @p minecraft:diamond 5</cmd>. Use correct Minecraft " + MC_VERSION + " syntax. The"
            + " command actually runs and its result comes back to you. Prefer <locate> for finding structures."
            + " Only run a command when the player is actually asking you to do or change something."
            + "\n\nBackground world state (consult only if relevant, don't recite): " + gameContext;

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        messages.add(sys);

        // One-shot examples so the model reliably picks the right tag (or none).
        addExample(messages, "where's the nearest village?", "<locate>village</locate>");
        addExample(messages, "make it day", "<cmd>time set day</cmd>");
        addExample(messages, "how does a redstone comparator work?", "<wiki>redstone comparator</wiki>");
        addExample(messages, "know any good redstone youtubers?", "<search>best redstone youtubers</search>");
        addExample(messages, "hey bufo", "Hey! What are we getting into?");
        addExample(messages, "come here", "<cmd>tp @e[type=companion:bufo,limit=1,sort=nearest] @p</cmd>");

        if (history != null) {
            for (Pipeline.Turn t : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", t.role());
                m.addProperty("content", t.content());
                messages.add(m);
            }
        }

        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", userText);
        messages.add(usr);

        return complete(messages);
    }

    private static void addExample(JsonArray messages, String user, String assistant) {
        JsonObject u = new JsonObject();
        u.addProperty("role", "user");
        u.addProperty("content", user);
        messages.add(u);
        JsonObject a = new JsonObject();
        a.addProperty("role", "assistant");
        a.addProperty("content", assistant);
        messages.add(a);
    }

    /** Second pass: answer the player using looked-up reference text (wiki or web). */
    public static String answerWithContext(String userText, String info) throws Exception {
        JsonArray messages = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", CompanionConfig.personality
            + "\n\nYou just looked something up for the player. Using ONLY the reference text below, answer"
            + " their question accurately in your own voice, 1-3 short sentences, friendly and natural. If the"
            + " reference doesn't actually answer it, say you couldn't find a clear answer. Never invent"
            + " specifics. No lists.\n\nReference:\n" + info);
        messages.add(sys);

        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", "The player asked: \"" + userText + "\". Answer using the reference above.");
        messages.add(usr);

        return complete(messages);
    }

    /** Second pass: turn a command's raw result into a natural spoken reply. */
    public static String phraseCommandResult(String userText, String commandResult) throws Exception {
        JsonArray messages = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", CompanionConfig.personality
            + "\n\nYou just looked something up in the game for the player. Tell them the answer in your own"
            + " voice, 1-2 short sentences, friendly and natural. If it found coordinates, state them clearly."
            + " If it found nothing, say so plainly. No lists.");
        messages.add(sys);

        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", "The player asked: \"" + userText + "\". The game's response was: \""
            + commandResult + "\". Answer the player.");
        messages.add(usr);

        return complete(messages);
    }

    private static String complete(JsonArray messages) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", CompanionConfig.llmModel);
        body.add("messages", messages);
        body.addProperty("max_completion_tokens", 512);
        body.addProperty("reasoning_effort", "low");
        body.addProperty("temperature", 0.6);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(CompanionConfig.llmBaseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + key())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new RuntimeException("LLM HTTP " + res.statusCode() + ": " + res.body());

        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                   .getAsJsonObject("message").get("content").getAsString().trim();
        if (content.isEmpty()) content = "Hmm, I blanked for a sec \u2014 say that again?";
        return content;
    }
}
