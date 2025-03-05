package net.uwutastisch.beth;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.*;

public class ChatAIListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ChatAIListener.class);
    private static final Pattern TITLE_PATTERN = Pattern.compile("Image Title:\\n```\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern POSITIVE_PATTERN = Pattern.compile("Positive Prompt:\\n```\\n(.*?)\\n```",
            Pattern.DOTALL);
    private static final Pattern NEGATIVE_PATTERN = Pattern.compile("Negative Prompt:\\n```\\n(.*?)\\n```",
            Pattern.DOTALL);
    private static final String SUFFIX = "> \n";

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();
        if (event.getAuthor().isBot())
            return;
        if (!(event.getChannel() instanceof TextChannel channel))
            return;
        if (!ConfigUtil.isLLMChat(channel))
            return;

        List<Message> messages = channel.getIterableHistory().complete();
        JSONArray context = buildContext(messages, channel);
        sendMessage(channel, context);
    }

    private JSONArray buildContext(List<Message> messages, TextChannel channel) {
        JSONArray context = new JSONArray();
        try {
            context.put(new JSONObject()
                    .put("role", "system")
                    .put("content", ConfigUtil.getSystemPrompt(channel)));
        } catch (Exception e) {
            logger.error("Failed to create system prompt", e);
        }

        for (Message m : messages) {
            if (m.getContentRaw().equals(ConfigUtil.START_MESSAGE))
                break;

            try {
                JSONObject msg = new JSONObject();
                if (m.getAuthor().equals(channel.getJDA().getSelfUser())) {
                    String[] parts = m.getContentRaw().split(SUFFIX, 2);
                    String content = parts.length > 1 ? parts[1] : "";
                    msg.put("role", "assistant")
                            .put("content", content);
                } else {
                    msg.put("role", "user")
                            .put("content", formatUserMessage(m));
                }
                context.put(msg);
            } catch (Exception e) {
                logger.error("Error processing message: " + m.getId(), e);
            }
        }
        return context;
    }

    private String formatUserMessage(Message message) {
        User author = message.getAuthor();
        return String.format("User: %s (ID: %s)\nMessage: %s",
                author.getName(),
                author.getId(),
                message.getContentRaw());
    }

    private void sendMessage(TextChannel channel, JSONArray context) {
        CompletableFuture.runAsync(() -> {
            try {
                AIClient aiClient = ConfigUtil.getAIClient(channel);
                String response = aiClient.generateResponse(context);

                // Create message content with proper formatting
                String formattedResponse = String.format("[%s] %s",
                        aiClient.getModelName(),
                        response.replaceAll("[\\x00-\\x1F]", "") // Remove control characters
                );

                // Split long messages
                if (formattedResponse.length() > 2000) {
                    formattedResponse = formattedResponse.substring(0, 1990) + "\n...";
                }

                channel.sendMessage(formattedResponse)
                        .queue(
                                success -> System.out.println("Message sent: " + success.getId()),
                                error -> System.err.println("Failed to send message: " + error.getMessage()));

            } catch (Exception e) {
                System.err.println("Error processing request: ");
                e.printStackTrace();
                channel.sendMessage("Error: " + e.getMessage())
                        .queue(null, err -> System.err.println("Failed to send error: " + err));
            }
        });
    }

    private String formatResponseHeader(AIClient client) {
        return String.format("**[%s - %s]**\n", client.getProvider(), client.getModelName());
    }

    private void processImageResponse(MessageCreateAction action, String response) {
        Matcher titleMatcher = TITLE_PATTERN.matcher(response);
        Matcher positiveMatcher = POSITIVE_PATTERN.matcher(response);
        Matcher negativeMatcher = NEGATIVE_PATTERN.matcher(response);

        if (titleMatcher.find() && positiveMatcher.find() && negativeMatcher.find()) {
            String title = titleMatcher.group(1);
            String positive = positiveMatcher.group(1);
            String negative = negativeMatcher.group(1);

            try {
                action.addFiles(FileUpload.fromData(BethMain.generateImage(positive, negative), "image.png"))
                        .setContent(title);
            } catch (IOException e) {
                action.setContent("Failed to generate image: " + e.getMessage());
            }
        }
    }
}