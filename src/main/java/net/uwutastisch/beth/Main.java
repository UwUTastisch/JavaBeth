package net.uwutastisch.beth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

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
        builder.setActivity(Activity.of(Activity.ActivityType.LISTENING,"Version: alpha"));
        jda = builder.build();

        jda.awaitReady(); //TimeUnit.SECONDS.sleep(1);

        System.out.println("Seems " + jda.getStatus() + " UWU");

        /*CompletableFuture.runAsync(() -> {
            System.out.println("Hmmm :P");
            List<TextChannel> textChannels = jda.getTextChannels();
            System.out.println(textChannels);
            for (TextChannel textChannel : textChannels) {
                //System.out.println(textChannel.canTalk() + " und " + textChannel.getTopic().toLowerCase().startsWith("beth=") );
                try {


                if(!ConfigUtil.isLLMChat(textChannel)) continue;
                //textChannel.sendMessage("Hewo i am online Again").queue();
                textChannel.sendMessage(ConfigUtil.startMessage).queue();
                } catch (Exception ignored) {}
            }
            System.out.println("Greetings done!");
        });*/


    }

    public static String chatGPT(JSONArray context) {
        String model = "gpt-3.5-turbo";
        try {
            JSONObject jsonResponse = requestOpenAI(context, model);
            //System.out.println(messages);
            //System.out.println(string1);
            return getGptMessageContent(jsonResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public static JSONObject requestOpenAI(JSONArray context, String model) throws IOException {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = dotenv.get("OpenAIToken");

        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");

        // The request body
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("model", model);
        jsonObject.put("messages", context);
        //String body = "{\"model\": \"" + model + "\", " + context + " }";
        String jsonString = jsonObject.toString();
        System.out.println(jsonString);
        connection.setDoOutput(true);
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
        writer.write(jsonString);
        writer.flush();
        writer.close();
        //System.out.println("qwq");
        //System.out.println("Respond " + connection.getResponseCode());
        int responseCode = connection.getResponseCode();
        if(responseCode != 200) {
            System.out.println("Oh no UwU: "+ responseCode);
            throw new RuntimeException();
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
        //return extractMessageFromJSONResponse(response.toString());
        String string = response.toString();
        System.out.println(string);
        JSONObject jsonResponse = new JSONObject(string);
        return jsonResponse;
    }

    public static String getGptMessageContent(JSONObject gptResponse) {
        return gptResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    public static String extractMessageFromJSONResponse(String response) {
        response = StringEscapeUtils.unescapeJava(response);
        int start = response.indexOf("content")+ 11;

        int end = response.indexOf("\"", start);

        return response.substring(start, end);

    }

    public static void addBase64Image(Message message, String prompt, String negativePrompt, String imageTitle) throws IOException {
        // Assuming base64Image is the image string without data mime type prefix
        byte[] imageBytes = Base64.getDecoder().decode(getImageWithPrompt(prompt,negativePrompt));

        boolean folderiscreated = new File("images").mkdirs();
        // Write the image bytes to a file
        File file = new File("images/"+ System.currentTimeMillis() + ".png"); // Ensure to use the correct extension
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(imageBytes);
        } catch (IOException e) {
            e.printStackTrace();
            return; // Handle the exception properly
        }

        MessageAction reply = message.reply(imageTitle);
        // Send the file
        reply.addFile(file, "image.png").queue(
                success -> file.delete(), // Delete the file if the upload is successful
                failure -> file.delete()  // Delete the file if the upload failed
        );
    }

    private static String getImageWithPrompt(String prompt, String negativePrompt) throws IOException {
        // Define the payload
        JSONObject payload = createJsonPayload(prompt,negativePrompt);

        // Send the POST request
        URL url = new URL(dotenv.get("StableDiffusionAPI") + "/sdapi/v1/txt2img");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        System.out.println(payload);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json"); //; utf-8
        con.setRequestProperty("accept", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = payload.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read the response
        StringBuilder response = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(con.getInputStream(), "utf-8"))) {
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        // Parse the response
        JSONObject jsonResponse = new JSONObject(response.toString());
        String base64Image = jsonResponse.getJSONArray("images").getString(0);

        // Close the connection
        con.disconnect();

        return base64Image;
    }


    public static JSONObject createJsonPayload(String prompt, String negativePrompt) {
        return new JSONObject()
                .put("prompt", prompt)
                .put("negative_prompt", negativePrompt)
                .put("styles", new JSONArray())
                .put("seed", -1)
                .put("subseed", -1)
                .put("subseed_strength", 0)
                .put("seed_resize_from_h", -1)
                .put("seed_resize_from_w", -1)
                .put("sampler_name", "Heun")
                .put("batch_size", 1)
                .put("n_iter", 1)
                .put("steps", 30)
                .put("cfg_scale", 7)
                .put("width", 512)
                .put("height", 512)
                .put("restore_faces", false)
                .put("tiling", false)
                .put("do_not_save_samples", false)
                .put("do_not_save_grid", false)
                .put("eta", 0)
                .put("denoising_strength", 0)
                .put("s_min_uncond", 0)
                .put("s_churn", 0)
                .put("s_tmax", 0)
                .put("s_tmin", 0)
                .put("s_noise", 0)
                .put("override_settings", new JSONObject())
                .put("override_settings_restore_afterwards", true)
                .put("refiner_checkpoint", "")
                .put("refiner_switch_at", 0)
                .put("disable_extra_networks", false)
                .put("comments", new JSONObject())
                .put("enable_hr", false)
                .put("firstphase_width", 0)
                .put("firstphase_height", 0)
                .put("hr_scale", 2)
                .put("hr_upscaler", "")
                .put("hr_second_pass_steps", 0)
                .put("hr_resize_x", 0)
                .put("hr_resize_y", 0)
                .put("hr_checkpoint_name", "")
                .put("hr_sampler_name", "")
                .put("hr_prompt", "")
                .put("hr_negative_prompt", "")
                .put("sampler_index", "Euler")
                .put("script_name", "")
                .put("script_args", new JSONArray())
                .put("send_images", true)
                .put("save_images", true)
                .put("alwayson_scripts", new JSONObject());
    }

}