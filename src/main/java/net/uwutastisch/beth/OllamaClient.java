package net.uwutastisch.beth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class OllamaClient implements AIClient {
    private final String model;
    private final String baseUrl;

    public OllamaClient(String model) {
        this.model = model;
        this.baseUrl = BethMain.getDotenv().get("OllamaHost", "http://localhost:11434");
        System.out.println("Using Ollama endpoint: " + baseUrl); // Debug logging
    }

    @Override
    public String generateResponse(JSONArray context) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("prompt", buildOllamaPrompt(context));
        payload.put("stream", true); // Enable streaming
        payload.put("options", new JSONObject().put("temperature", 0.7));

        HttpURLConnection con = createConnection("/api/generate");
        sendRequest(con, payload);

        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Ollama API Error: " + readErrorResponse(con));
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                JSONObject jsonResponse = new JSONObject(line);
                response.append(jsonResponse.getString("response"));
            }
        }
        return response.toString();
    }

    private String readErrorResponse(HttpURLConnection con) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    public static List<String> getAvailableModels() throws IOException {
        OllamaClient client = new OllamaClient("");
        HttpURLConnection con = client.createConnection("/api/tags");
        con.setRequestMethod("GET"); // Explicit set GET method

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String response = readAllLines(br);
            if (response.isEmpty()) {
                throw new IOException("Empty response from Ollama");
            }
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray modelArray = jsonResponse.getJSONArray("models");
            if (modelArray.isEmpty()) {
                throw new IOException("No models found on Ollama server");
            }
            List<String> models = new ArrayList<>();
            for (int i = 0; i < modelArray.length(); i++) {
                models.add(modelArray.getJSONObject(i).getString("name"));
            }
            return models;
        }
    }

    private HttpURLConnection createConnection(String endpoint) throws IOException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET".equals(endpoint) ? "GET" : "POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        return con;
    }

    private void sendRequest(HttpURLConnection con, JSONObject payload) throws IOException {
        try (OutputStream os = con.getOutputStream()) {
            os.write(payload.toString().getBytes());
        }
    }

    private String buildOllamaPrompt(JSONArray context) {
        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < context.length(); i++) {
            JSONObject msg = context.getJSONObject(i);
            String role = msg.getString("role");
            String content = msg.getString("content");
            prompt.append(role.equals("assistant") ? "Assistant: " : "User: ")
                    .append(content)
                    .append("\n\n");
        }
        prompt.append("Assistant: ");
        return prompt.toString();
    }

    @Override
    public int getMaxTokens() {
        return 4096;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProvider() {
        return "Ollama";
    }

    private static String readAllLines(BufferedReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        return content.toString();
    }
}