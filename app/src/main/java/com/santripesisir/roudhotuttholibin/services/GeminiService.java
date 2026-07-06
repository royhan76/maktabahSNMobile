package com.santripesisir.roudhotuttholibin.services;

import com.santripesisir.roudhotuttholibin.search.SearchIndexManager.ChatMessage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GeminiService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;

    public interface GeminiCallback {
        void onSuccess(String responseText);
        void onFailure(Exception e);
    }

    public GeminiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void testApiKey(
            String apiKey,
            String model,
            GeminiCallback callback
    ) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onFailure(new Exception("API Key Gemini kosong."));
            return;
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            // Buat request body JSON minimal
            JSONObject root = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObj = new JSONObject();
            contentObj.put("role", "user");
            JSONArray partsArray = new JSONArray();
            JSONObject partText = new JSONObject();
            partText.put("text", "ping");
            partsArray.put(partText);
            contentObj.put("parts", partsArray);
            contentsArray.put(contentObj);
            root.put("contents", contentsArray);

            String jsonPayload = root.toString();
            RequestBody body = RequestBody.create(jsonPayload, JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    try (Response resp = response) {
                        if (!resp.isSuccessful()) {
                            String errBody = resp.body() != null ? resp.body().string() : "No error body";
                            callback.onFailure(new IOException("Koneksi gagal (" + resp.code() + "): " + errBody));
                            return;
                        }
                        callback.onSuccess("Koneksi berhasil!");
                    } catch (Exception e) {
                        callback.onFailure(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    public void extractArabicKeywords(
            String apiKey,
            String query,
            GeminiCallback callback
    ) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onFailure(new Exception("API Key Gemini kosong."));
            return;
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

            JSONObject root = new JSONObject();
            
            // System instruction untuk mengekstrak keyword arab
            JSONObject sysInstObj = new JSONObject();
            JSONArray sysPartsArray = new JSONArray();
            JSONObject sysPartText = new JSONObject();
            sysPartText.put("text", "You are an expert Arabic search term extractor. Extract 2-4 key Arabic terms/words from the user query to search in an Arabic Islamic book database. Output ONLY the raw Arabic keywords separated by single spaces. Do NOT write any translation, markdown, punctuation, or explanation. Example query: 'hukum wudhu', output: 'الوضوء حكم'.");
            sysPartsArray.put(sysPartText);
            sysInstObj.put("parts", sysPartsArray);
            root.put("systemInstruction", sysInstObj);

            // Contents
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObj = new JSONObject();
            contentObj.put("role", "user");
            JSONArray partsArray = new JSONArray();
            JSONObject partText = new JSONObject();
            partText.put("text", query);
            partsArray.put(partText);
            contentObj.put("parts", partsArray);
            contentsArray.put(contentObj);
            root.put("contents", contentsArray);

            String jsonPayload = root.toString();
            RequestBody body = RequestBody.create(jsonPayload, JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    try (Response resp = response) {
                        if (!resp.isSuccessful()) {
                            callback.onFailure(new IOException("Gagal mengekstrak kata kunci arab (" + resp.code() + ")"));
                            return;
                        }
                        if (resp.body() == null) {
                            callback.onFailure(new IOException("Response kosong"));
                            return;
                        }
                        String responseBodyStr = resp.body().string();
                        JSONObject responseJson = new JSONObject(responseBodyStr);
                        JSONArray candidates = responseJson.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject candidate = candidates.getJSONObject(0);
                            JSONObject content = candidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String text = parts.getJSONObject(0).optString("text", "").trim();
                                    callback.onSuccess(text);
                                    return;
                                }
                            }
                        }
                        callback.onFailure(new Exception("Format response tidak sesuai"));
                    } catch (Exception e) {
                        callback.onFailure(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    public void callGemini(
            String apiKey,
            String model,
            float temperature,
            String systemInstruction,
            List<ChatMessage> history,
            GeminiCallback callback
    ) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onFailure(new Exception("API Key Gemini belum diset. Silakan atur di menu Pengaturan."));
            return;
        }

        try {
            // URL endpoint resmi Gemini API
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            // Buat request body JSON
            JSONObject root = new JSONObject();

            // 1. System Instruction
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                JSONObject sysInstObj = new JSONObject();
                JSONArray partsArray = new JSONArray();
                JSONObject partText = new JSONObject();
                partText.put("text", systemInstruction);
                partsArray.put(partText);
                sysInstObj.put("parts", partsArray);
                root.put("systemInstruction", sysInstObj);
            }

            // 2. Generation Config
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", temperature);
            root.put("generationConfig", genConfig);

            // 3. Contents (History Chat + New Message)
            JSONArray contentsArray = new JSONArray();
            for (ChatMessage msg : history) {
                // Abaikan pesan yang merupakan indikator error local agar tidak mengacaukan context Gemini
                if (msg.content != null && msg.content.startsWith("⚠️")) {
                    continue;
                }

                JSONObject contentObj = new JSONObject();
                // Gemini API menggunakan role "user" dan "model"
                String apiRole = "user".equalsIgnoreCase(msg.role) ? "user" : "model";
                contentObj.put("role", apiRole);

                JSONArray partsArray = new JSONArray();
                JSONObject partText = new JSONObject();
                partText.put("text", msg.content);
                partsArray.put(partText);

                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
            }
            root.put("contents", contentsArray);

            String jsonPayload = root.toString();
            RequestBody body = RequestBody.create(jsonPayload, JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    try (Response resp = response) {
                        if (!resp.isSuccessful()) {
                            String errBody = resp.body() != null ? resp.body().string() : "No error body";
                            callback.onFailure(new IOException("Gagal memanggil API (" + resp.code() + "): " + errBody));
                            return;
                        }

                        if (resp.body() == null) {
                            callback.onFailure(new IOException("Response body kosong"));
                            return;
                        }

                        String responseBodyStr = resp.body().string();
                        JSONObject responseJson = new JSONObject(responseBodyStr);
                        JSONArray candidates = responseJson.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject candidate = candidates.getJSONObject(0);
                            JSONObject content = candidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String text = parts.getJSONObject(0).optString("text", "");
                                    callback.onSuccess(text);
                                    return;
                                }
                            }
                        }
                        callback.onFailure(new Exception("Format response Gemini API tidak sesuai ekspektasi."));
                    } catch (Exception e) {
                        callback.onFailure(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onFailure(e);
        }
    }
}
