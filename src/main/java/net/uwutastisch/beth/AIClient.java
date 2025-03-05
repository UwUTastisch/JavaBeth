package net.uwutastisch.beth;

import org.json.JSONArray;

public interface AIClient {
    String generateResponse(JSONArray context) throws Exception;

    int getMaxTokens();

    String getModelName();

    default String getProvider() {
        return "Unknown";
    }
}