package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.commons.lang.StringEscapeUtils;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Main {


    private static JDA jda;
    private static Dotenv dotenv;

    public static void main(String[] args) throws LoginException, InterruptedException {
        dotenv = Dotenv.configure().load();
        String discordToken = dotenv.get("DiscordToken");
        JDABuilder builder = JDABuilder.createDefault(discordToken);
        builder.addEventListeners(new ChatAIListener());

        // Disable parts of the cache
        builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        // Enable the bulk delete event
        //builder.setBulkDeleteSplittingEnabled(false);
        // Set activity (like "playing Something")
        builder.setActivity(Activity.watching("TV"));
        jda = builder.build();

        jda.awaitReady(); //TimeUnit.SECONDS.sleep(1);

        System.out.println("Seems " + jda.getStatus() + " UWU");
        CompletableFuture.runAsync(() -> {
            List<TextChannel> textChannels = jda.getTextChannels();
            //System.out.println(textChannels);
            for (TextChannel textChannel : textChannels) {
                //System.out.println(textChannel.canTalk() + " und " + textChannel.getTopic().toLowerCase().startsWith("beth=") );
                if(!ConfigUtil.isLLMChat(textChannel)) continue;
                //textChannel.sendMessage("Hewo i am online Again").queue();
                textChannel.sendMessage(ConfigUtil.startMessage).queue();
            }
            System.out.println("Greetings done!");
        });
    }

    public static String chatGPT(String context) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = dotenv.get("OpenAIToken");
        String model = "gpt-3.5-turbo";

        try {
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");

            // The request body
            String body = "{\"model\": \"" + model + "\", " + context + " }";
            System.out.println(body);
            connection.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(body);
            writer.flush();
            writer.close();
            //System.out.println("qwq");
            //System.out.println("Respond " + connection.getResponseCode());
            int responseCode = connection.getResponseCode();
            if(responseCode != 200) {
                System.out.println("Oh no UwU: "+ responseCode);
                throw new RuntimeException("Error Respond: " + responseCode);
            }
            // Response from ChatGPT
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            //System.out.println("QwQ idkkkkk");
            String line;
            StringBuffer response = new StringBuffer();

            while ((line = br.readLine()) != null) {
                response.append(line);
                //System.out.println(line);
            }
            br.close();

            // calls the method to extract the message.
            return extractMessageFromJSONResponse(response.toString());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String extractMessageFromJSONResponse(String response) {
        response = StringEscapeUtils.unescapeJava(response);
        int start = response.indexOf("content")+ 11;

        int end = response.indexOf("\"", start);

        return response.substring(start, end);

    }
}