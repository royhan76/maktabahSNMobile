package com.santripesisir.roudhotuttholibin.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ReaderPreferences {

    private static final String PREF_NAME = "mai_reader_prefs";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_LINE_SPACING = "line_spacing";
    private static final String KEY_MARGIN = "margin";
    private static final String KEY_FONT_TYPE = "font_type";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_BRIGHTNESS = "brightness";

    public static final String FONT_AMIRI = "Amiri";
    public static final String FONT_KFGQPC = "KFGQPC";

    private final SharedPreferences prefs;

    public ReaderPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getFontSize() {
        return prefs.getInt(KEY_FONT_SIZE, 22); // Default Arabic font size
    }

    public void setFontSize(int size) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply();
    }

    public float getLineSpacing() {
        return prefs.getFloat(KEY_LINE_SPACING, 1.8f); // Default line spacing for Arabic
    }

    public void setLineSpacing(float spacing) {
        prefs.edit().putFloat(KEY_LINE_SPACING, spacing).apply();
    }

    public int getMargin() {
        return prefs.getInt(KEY_MARGIN, 16); // Default margin in dp
    }

    public void setMargin(int margin) {
        prefs.edit().putInt(KEY_MARGIN, margin).apply();
    }

    public String getFontType() {
        return prefs.getString(KEY_FONT_TYPE, FONT_AMIRI);
    }

    public void setFontType(String type) {
        prefs.edit().putString(KEY_FONT_TYPE, type).apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public void setDarkMode(boolean darkMode) {
        prefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
    }

    public float getBrightness() {
        return prefs.getFloat(KEY_BRIGHTNESS, -1.0f); // -1 means system default
    }

    public void setBrightness(float brightness) {
        prefs.edit().putFloat(KEY_BRIGHTNESS, brightness).apply();
    }

    public void reset() {
        prefs.edit().clear().apply();
    }
}
