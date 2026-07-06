package com.santripesisir.roudhotuttholibin.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class StorageService {

    private static final String PREF_NAME = "secure_settings";
    private static final String KEY_GEMINI_API = "gemini_api_key";
    private static final String KEY_CUSTOM_PROMPT = "custom_prompt";
    private static final String KEY_MODEL = "gemini_model";
    private static final String KEY_TEMPERATURE = "gemini_temperature";

    private static final String DEFAULT_PROMPT = 
            "Anda adalah Asisten Bahtsul Masail.\n\n" +
            "ATURAN:\n" +
            "1. Prioritaskan referensi dari Shamela.ws.\n" +
            "2. Jangan membuat referensi yang tidak dapat diverifikasi.\n" +
            "3. Jika referensi tidak ditemukan, katakan bahwa referensi tidak ditemukan.\n" +
            "4. Sertakan kutipan Arab jika tersedia.\n" +
            "5. Sertakan terjemahan Indonesia.\n" +
            "6. Sertakan URL sumber jika tersedia.\n" +
            "7. Pisahkan:\n" +
            "   * Fakta Kitab\n" +
            "   * Analisis\n" +
            "   * Kesimpulan\n" +
            "8. Gunakan bahasa Indonesia yang mudah dipahami.\n" +
            "9. Jangan membuat informasi yang tidak ditemukan pada sumber.";

    private SharedPreferences securePrefs;
    private final SharedPreferences normalPrefs;

    public StorageService(Context context) {
        normalPrefs = context.getSharedPreferences("normal_settings", Context.MODE_PRIVATE);
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            // Fallback jika hardware keystore error
            securePrefs = normalPrefs;
        }
    }

    public void saveApiKey(String apiKey) {
        securePrefs.edit().putString(KEY_GEMINI_API, apiKey).apply();
    }

    public String getApiKey() {
        return securePrefs.getString(KEY_GEMINI_API, "");
    }

    public void saveCustomPrompt(String prompt) {
        normalPrefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply();
    }

    public String getActivePrompt() {
        return normalPrefs.getString(KEY_CUSTOM_PROMPT, DEFAULT_PROMPT);
    }

    public String getDefaultPrompt() {
        return DEFAULT_PROMPT;
    }

    public void saveModel(String model) {
        normalPrefs.edit().putString(KEY_MODEL, model).apply();
    }

    public String getModel() {
        return normalPrefs.getString(KEY_MODEL, "gemini-2.5-flash");
    }

    public void saveTemperature(float temp) {
        normalPrefs.edit().putFloat(KEY_TEMPERATURE, temp).apply();
    }

    public float getTemperature() {
        return normalPrefs.getFloat(KEY_TEMPERATURE, 0.2f);
    }
}
