package com.santripesisir.roudhotuttholibin.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager.Chat;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager.ChatMessage;
import com.santripesisir.roudhotuttholibin.services.GeminiService;
import com.santripesisir.roudhotuttholibin.utils.StorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private ChatAdapter adapter;
    private List<ChatMessage> messagesList = new ArrayList<>();
    private EditText etInput;
    private MaterialButton btnSend;
    private LinearLayout layoutEmpty;
    private LinearProgressIndicator progressIndicator;

    private SearchIndexManager dbManager;
    private StorageService storageService;
    private GeminiService geminiService;

    private String activeChatId;
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        dbManager = new SearchIndexManager(this);
        storageService = new StorageService(this);
        geminiService = new GeminiService();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvMessages = findViewById(R.id.rv_chat_messages);
        etInput = findViewById(R.id.et_chat_input);
        btnSend = findViewById(R.id.btn_send_chat);
        layoutEmpty = findViewById(R.id.layout_empty_state);
        progressIndicator = findViewById(R.id.progress_ai_typing);
        ImageButton btnSettings = findViewById(R.id.btn_chat_settings);

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(this, messagesList);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Mulai sesi chat baru (generate UUID acak)
        activeChatId = UUID.randomUUID().toString();
        Chat chat = new Chat();
        chat.id = activeChatId;
        chat.title = "Tanya Jawab Fiqih";
        chat.createdAt = System.currentTimeMillis();
        chat.updatedAt = System.currentTimeMillis();
        dbManager.insertChat(chat);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Jika ini adalah pesan pertama, gunakan sebagai judul chat session
        if (messagesList.isEmpty()) {
            dbManager.updateChatTitle(activeChatId, text.length() > 30 ? text.substring(0, 30) + "..." : text);
        }

        // Simpan & tampilkan pesan User
        ChatMessage userMsg = new ChatMessage();
        userMsg.id = UUID.randomUUID().toString();
        userMsg.chatId = activeChatId;
        userMsg.role = "user";
        userMsg.content = text;
        userMsg.createdAt = System.currentTimeMillis();

        dbManager.insertMessage(userMsg);
        messagesList.add(userMsg);
        adapter.notifyItemInserted(messagesList.size() - 1);
        rvMessages.scrollToPosition(messagesList.size() - 1);

        etInput.setText("");
        layoutEmpty.setVisibility(View.GONE);
        rvMessages.setVisibility(View.VISIBLE);

        // Aktifkan Loading Indicator
        progressIndicator.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        // Ambil data settings
        String apiKey = storageService.getApiKey();
        String model = storageService.getModel();
        float temp = storageService.getTemperature();
        String sysPrompt = storageService.getActivePrompt();

        // Panggil Gemini untuk ekstraksi kata kunci arab (RAG)
        geminiService.extractArabicKeywords(apiKey, text, new GeminiService.GeminiCallback() {
            @Override
            public void onSuccess(String keywordsStr) {
                executorService.execute(() -> {
                    // Split keywords
                    List<String> keywords = new ArrayList<>();
                    if (keywordsStr != null && !keywordsStr.trim().isEmpty()) {
                        for (String kw : keywordsStr.split("\\s+")) {
                            String trimmed = kw.trim();
                            if (!trimmed.isEmpty()) {
                                keywords.add(trimmed);
                            }
                        }
                    }

                    StringBuilder contextBuilder = new StringBuilder();
                    if (!keywords.isEmpty()) {
                        // Cari di database menggunakan keywords
                        List<SearchIndexManager.SearchResult> results = dbManager.search(null, keywords, true, false, false, true);
                        if (results != null && !results.isEmpty()) {
                            // Bangun query params untuk highlight keywords
                            StringBuilder hlBuilder = new StringBuilder();
                            try {
                                for (int i = 0; i < keywords.size(); i++) {
                                    if (i > 0) hlBuilder.append("+");
                                    hlBuilder.append(java.net.URLEncoder.encode(keywords.get(i), "UTF-8"));
                                }
                            } catch (Exception ignored) {}
                            String hlQuery = hlBuilder.length() > 0 ? "?hl=" + hlBuilder.toString() : "";

                            contextBuilder.append("KONTEKS REFERENSI KITAB LOKAL:\n");
                            // Batasi maksimal 5 referensi teratas
                            int count = 0;
                            for (SearchIndexManager.SearchResult res : results) {
                                if (count >= 5) break;
                                String fullContent = dbManager.getPageContent(res.bookId, res.pageId);
                                if (fullContent != null && !fullContent.trim().isEmpty()) {
                                    contextBuilder.append("\n--- REFERENSI ").append(count + 1).append(" ---\n")
                                            .append("Kitab: ").append(res.bookTitle).append("\n")
                                            .append("Bab/Pasal: ").append(res.chapterTitle).append("\n")
                                            .append("Halaman: ").append(res.pageId).append("\n")
                                            .append("Format Link: [Lihat Referensi: ").append(res.bookTitle).append(" Halaman ").append(res.pageId).append("](book://").append(res.bookId).append("/").append(res.pageId).append(hlQuery).append(")\n")
                                            .append("Teks Kitab:\n").append(fullContent).append("\n");
                                    count++;
                                }
                            }
                        }
                    }

                    // Susun prompt dinamis dengan menyertakan konteks kitab lokal
                    String dynamicPrompt = sysPrompt + "\n\n";
                    if (contextBuilder.length() > 0) {
                        dynamicPrompt += "Gunakan KONTEKS REFERENSI KITAB LOKAL di bawah ini sebagai sumber utama dan mutlak untuk menjawab pertanyaan pengguna. " +
                                "Terjemahkan kutipan teks Arab yang relevan ke bahasa Indonesia secara ringkas, sertakan kutipan aslinya, serta sebutkan nama Kitab, Bab, dan Halaman secara presisi sesuai data konteks.\n\n" +
                                "PENTING: Di akhir kutipan referensi, Anda WAJIB menyertakan link markdown persis seperti yang tertulis pada bagian 'Format Link' dari referensi yang digunakan agar bisa diklik oleh user (misalnya [Lihat Referensi: Al-Majmu' Halaman 15](book://AlmajmuSyarhMuhadzab/15)).\n\n" +
                                "Jika pertanyaan tidak berkaitan dengan konteks, atau jika jawabannya tidak ditemukan di dalam konteks tersebut, katakan secara sopan bahwa jawabannya tidak ditemukan pada kitab-kitab lokal dalam aplikasi.\n\n" +
                                contextBuilder.toString();
                    } else {
                        dynamicPrompt += "PENTING: Tidak ada referensi kitab lokal yang cocok yang ditemukan di database aplikasi untuk pertanyaan ini. " +
                                "Oleh karena itu, katakan secara sopan dan singkat bahwa Anda tidak dapat menemukan referensi yang valid pada kitab-kitab yang terinstal dalam aplikasi untuk menjawab pertanyaan ini, dan jangan memberikan jawaban dari sumber luar.";
                    }

                    // Panggil Gemini dengan prompt dinamis yang berisi konteks RAG
                    executeGeminiCall(apiKey, model, temp, dynamicPrompt);
                });
            }

            @Override
            public void onFailure(Exception e) {
                // Fallback jika ekstraksi kata kunci gagal: Coba lakukan pencarian sederhana dengan memecah kata kunci input asli
                executorService.execute(() -> {
                    List<String> keywords = new ArrayList<>();
                    for (String kw : text.split("\\s+")) {
                        String trimmed = kw.trim();
                        if (trimmed.length() > 2) {
                            keywords.add(trimmed);
                        }
                    }
                    
                    StringBuilder contextBuilder = new StringBuilder();
                    if (!keywords.isEmpty()) {
                        List<SearchIndexManager.SearchResult> results = dbManager.search(null, keywords, true, false, false, true);
                        if (results != null && !results.isEmpty()) {
                            // Bangun query params untuk highlight keywords
                            StringBuilder hlBuilder = new StringBuilder();
                            try {
                                for (int i = 0; i < keywords.size(); i++) {
                                    if (i > 0) hlBuilder.append("+");
                                    hlBuilder.append(java.net.URLEncoder.encode(keywords.get(i), "UTF-8"));
                                }
                            } catch (Exception ignored) {}
                            String hlQuery = hlBuilder.length() > 0 ? "?hl=" + hlBuilder.toString() : "";

                            contextBuilder.append("KONTEKS REFERENSI KITAB LOKAL:\n");
                            int count = 0;
                            for (SearchIndexManager.SearchResult res : results) {
                                if (count >= 5) break;
                                String fullContent = dbManager.getPageContent(res.bookId, res.pageId);
                                if (fullContent != null && !fullContent.trim().isEmpty()) {
                                    contextBuilder.append("\n--- REFERENSI ").append(count + 1).append(" ---\n")
                                            .append("Kitab: ").append(res.bookTitle).append("\n")
                                            .append("Bab/Pasal: ").append(res.chapterTitle).append("\n")
                                            .append("Halaman: ").append(res.pageId).append("\n")
                                            .append("Format Link: [Lihat Referensi: ").append(res.bookTitle).append(" Halaman ").append(res.pageId).append("](book://").append(res.bookId).append("/").append(res.pageId).append(hlQuery).append(")\n")
                                            .append("Teks Kitab:\n").append(fullContent).append("\n");
                                    count++;
                                }
                            }
                        }
                    }

                    String dynamicPrompt = sysPrompt + "\n\n";
                    if (contextBuilder.length() > 0) {
                        dynamicPrompt += "Gunakan KONTEKS REFERENSI KITAB LOKAL di bawah ini sebagai sumber utama dan mutlak untuk menjawab pertanyaan pengguna. " +
                                "Terjemahkan kutipan teks Arab yang relevan ke bahasa Indonesia secara ringkas, sertakan kutipan aslinya, serta sebutkan nama Kitab, Bab, dan Halaman secara presisi.\n\n" +
                                "PENTING: Di akhir kutipan referensi, Anda WAJIB menyertakan link markdown persis seperti yang tertulis pada bagian 'Format Link' dari referensi yang digunakan agar bisa diklik oleh user (misalnya [Lihat Referensi: Al-Majmu' Halaman 15](book://AlmajmuSyarhMuhadzab/15)).\n\n" +
                                "Jika pertanyaan tidak berkaitan dengan konteks, atau jika jawabannya tidak ditemukan di dalam konteks tersebut, katakan secara sopan bahwa jawabannya tidak ditemukan pada kitab-kitab lokal dalam aplikasi.\n\n" +
                                contextBuilder.toString();
                    } else {
                        dynamicPrompt += "PENTING: Tidak ada referensi kitab lokal yang ditemukan di database untuk pertanyaan ini. " +
                                "Katakan secara sopan dan singkat bahwa Anda tidak dapat menemukan referensi yang valid pada kitab-kitab yang terinstal dalam aplikasi.";
                    }

                    executeGeminiCall(apiKey, model, temp, dynamicPrompt);
                });
            }
        });
    }

    private void executeGeminiCall(String apiKey, String model, float temp, String dynamicPrompt) {
        geminiService.callGemini(apiKey, model, temp, dynamicPrompt, messagesList, new GeminiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                runOnUiThread(() -> {
                    progressIndicator.setVisibility(View.GONE);
                    btnSend.setEnabled(true);

                    // Simpan & tampilkan respons AI
                    ChatMessage aiMsg = new ChatMessage();
                    aiMsg.id = UUID.randomUUID().toString();
                    aiMsg.chatId = activeChatId;
                    aiMsg.role = "assistant";
                    aiMsg.content = responseText;
                    aiMsg.createdAt = System.currentTimeMillis();

                    dbManager.insertMessage(aiMsg);
                    messagesList.add(aiMsg);
                    adapter.notifyItemInserted(messagesList.size() - 1);
                    rvMessages.scrollToPosition(messagesList.size() - 1);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    progressIndicator.setVisibility(View.GONE);
                    btnSend.setEnabled(true);

                    // Simpan & tampilkan respons error di log chat
                    ChatMessage errMsg = new ChatMessage();
                    errMsg.id = UUID.randomUUID().toString();
                    errMsg.chatId = activeChatId;
                    errMsg.role = "assistant";
                    errMsg.content = "⚠️ **Gagal memproses tanggapan AI:**\n\n```\n" + e.getMessage() + "\n```\n\n*Silakan periksa koneksi internet Anda atau pastikan API Key di Pengaturan sudah valid.*";
                    errMsg.createdAt = System.currentTimeMillis();

                    dbManager.insertMessage(errMsg);
                    messagesList.add(errMsg);
                    adapter.notifyItemInserted(messagesList.size() - 1);
                    rvMessages.scrollToPosition(messagesList.size() - 1);

                    Toast.makeText(ChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gemini_settings, null);
        builder.setView(dialogView);

        TextInputEditText etApiKey = dialogView.findViewById(R.id.et_api_key);
        AutoCompleteTextView actvModel = dialogView.findViewById(R.id.actv_model);
        Slider sliderTemp = dialogView.findViewById(R.id.slider_temp);
        TextView tvTemp = dialogView.findViewById(R.id.tv_temp_value);
        TextInputEditText etPrompt = dialogView.findViewById(R.id.et_prompt);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save);
        MaterialButton btnCheckApi = dialogView.findViewById(R.id.btn_check_api);

        // Load parameter yang disimpan
        etApiKey.setText(storageService.getApiKey());
        etPrompt.setText(storageService.getActivePrompt());
        sliderTemp.setValue(storageService.getTemperature());
        tvTemp.setText(String.valueOf(storageService.getTemperature()));

        // Setup dropdown model
        String[] models = {"gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash"};
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, models);
        actvModel.setAdapter(arrayAdapter);
        actvModel.setText(storageService.getModel(), false);

        sliderTemp.addOnChangeListener((slider, value, fromUser) -> tvTemp.setText(String.format("%.1f", value)));

        btnCheckApi.setOnClickListener(v -> {
            String apiKey = etApiKey.getText() != null ? etApiKey.getText().toString().trim() : "";
            String selectedModel = actvModel.getText().toString();
            if (apiKey.isEmpty()) {
                Toast.makeText(ChatActivity.this, "Masukkan API Key terlebih dahulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            btnCheckApi.setEnabled(false);
            btnCheckApi.setText("Menghubungkan...");

            geminiService.testApiKey(apiKey, selectedModel, new GeminiService.GeminiCallback() {
                @Override
                public void onSuccess(String responseText) {
                    runOnUiThread(() -> {
                        btnCheckApi.setEnabled(true);
                        btnCheckApi.setText("Cek Koneksi API Key");
                        new AlertDialog.Builder(ChatActivity.this)
                                .setTitle("Koneksi Berhasil")
                                .setMessage("API Key valid dan terkoneksi ke Gemini.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        btnCheckApi.setEnabled(true);
                        btnCheckApi.setText("Cek Koneksi API Key");
                        new AlertDialog.Builder(ChatActivity.this)
                                .setTitle("Koneksi Gagal")
                                .setMessage("Detail Error:\n" + e.getMessage())
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            });
        });

        AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String apiKey = etApiKey.getText() != null ? etApiKey.getText().toString().trim() : "";
            String selectedModel = actvModel.getText().toString();
            float temperature = sliderTemp.getValue();
            String customPrompt = etPrompt.getText() != null ? etPrompt.getText().toString().trim() : "";

            storageService.saveApiKey(apiKey);
            storageService.saveModel(selectedModel);
            storageService.saveTemperature(temperature);
            storageService.saveCustomPrompt(customPrompt.isEmpty() ? storageService.getDefaultPrompt() : customPrompt);

            Toast.makeText(ChatActivity.this, "Pengaturan disimpan!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}
