package net.uwutastisch.beth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import javax.security.auth.login.LoginException;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class BethMain {
    private static Dotenv dotenv;
    private static JDA jda;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        dotenv = Dotenv.configure().load();
        /*
         * OllamaClient client = new OllamaClient("deepseek-r1:14b");
         * JSONArray testContext = new JSONArray().put(new JSONObject().put("role",
         * "user").put("content", "Why is the sky blue?"));
         * System.out.println(client.generateResponse(testContext));
         */
        jda = JDABuilder.createDefault(
                dotenv.get("DiscordToken"),
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT)
                .disableCache(
                        CacheFlag.EMOJI,
                        CacheFlag.STICKER,
                        CacheFlag.SCHEDULED_EVENTS,
                        CacheFlag.MEMBER_OVERRIDES,
                        CacheFlag.VOICE_STATE)
                .setActivity(Activity.listening("Version: multi-LLM"))
                .addEventListeners(new ChatAIListener())
                .build();
        jda.awaitReady();
    }

    public static JSONObject requestOpenAI(JSONArray context, String model) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL("https://api.openai.com/v1/chat/completions")
                .openConnection();

        // Verify environment variables
        String token = dotenv.get("OpenAIToken");
        if (token == null || token.isEmpty()) {
            throw new IOException("OpenAIToken is missing in .env file");
        }

        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Bearer " + token);
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.setDoInput(true);

        // Debug logging
        System.out.println("Sending request to OpenAI with token: " + token.substring(0, 5) + "...");

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("messages", context);

        try (OutputStream os = con.getOutputStream()) {
            os.write(payload.toString().getBytes());
        }

        // Handle response codes
        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("OpenAI API returned " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        return new JSONObject(response.toString());
    }

    public static String getGptMessageContent(JSONObject response) {
        return response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    public static byte[] generateImage(String prompt, String negativePrompt) throws IOException {
        JSONObject payload = new JSONObject()
                .put("prompt", prompt)
                .put("negative_prompt", negativePrompt)
                .put("steps", 30)
                .put("width", 1280)
                .put("height", 720);

        String stableDiffusionURL = dotenv.get("StableDiffusionAPI", "http://localhost:7860");
        HttpURLConnection con = (HttpURLConnection) new URL(stableDiffusionURL + "/sdapi/v1/txt2img").openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(payload.toString().getBytes());
        }

        JSONObject response = new JSONObject(new BufferedReader(
                new InputStreamReader(con.getInputStream())).readLine());
        return Base64.getDecoder().decode(response.getJSONArray("images").getString(0));
    }

    public static Dotenv getDotenv() {
        return dotenv;
    }
}