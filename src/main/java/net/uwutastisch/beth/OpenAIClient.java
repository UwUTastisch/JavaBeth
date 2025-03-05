package net.uwutastisch.beth;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;

public class OpenAIClient implements AIClient {
    private final String model;
    private final String apiKey;

    public OpenAIClient(String model) {
        this.model = model;
        this.apiKey = BethMain.getDotenv().get("OpenAIToken");
    }

    @Override
    public String generateResponse(JSONArray context) throws IOException {
        JSONObject response = BethMain.requestOpenAI(context, model);
        return BethMain.getGptMessageContent(response);
    }

    @Override
    public int getMaxTokens() {
        return switch (model) {
            case "gpt-4" -> 8192;
            case "gpt-3.5-turbo-16k" -> 16384;
            case "gpt-3.5-turbo" -> 4096;
            default -> 2048;
        };
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProvider() {
        return "OpenAI";
    }
}