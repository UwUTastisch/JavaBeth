package net.uwutastisch.beth;

import java.io.IOException;
import java.util.Objects;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ConfigUtil {
    public static boolean isLLMChat(TextChannel channel) {
        return channel.canTalk() && Objects.requireNonNull(channel.getTopic()).toLowerCase().startsWith("beth=");
    }

    public static AIClient getAIClient(TextChannel channel) {
        String config = channel.getTopic().substring(5).split(" ")[0];
        String[] parts = config.split(":", 3);

        String provider = parts[0].toLowerCase();
        String model = parts.length > 1 ? parts[1] : "";
        String size = parts.length > 2 ? parts[2] : "";

        switch (provider) {
            case "ollama":
                String fullModel = model + (size.isEmpty() ? "" : ":" + size);
                if (!model.isEmpty()) {
                    try {
                        if (OllamaClient.getAvailableModels().contains(fullModel)) {
                            return new OllamaClient(fullModel);
                        }
                    } catch (IOException e) {
                        channel.sendMessage("Ollama connection error: " + e.getMessage()).queue();
                    }
                }
                return getDefaultClient();

            case "openai":
                return new OpenAIClient(!model.isEmpty() ? model : "gpt-3.5-turbo");

            default:
                return getDefaultClient();
        }
    }

    public static AIClient getDefaultClient() {
        String defaultConfig = System.getenv().getOrDefault("DefaultModel", "ollama:deepseek-r1:14b");
        String[] parts = defaultConfig.split(":", 2);
        return parts[0].equalsIgnoreCase("ollama") ? new OllamaClient(parts[1]) : new OpenAIClient(parts[1]);
    }

    public static String getSystemPrompt(TextChannel channel) {
        String s = Objects.requireNonNull(channel.getTopic());
        int i = s.indexOf("\n");
        return i > 0 ? s.substring(i + 1) : "";
    }

    public static final String START_MESSAGE = "Hewwo, du hast mich neugestartet :D";

}