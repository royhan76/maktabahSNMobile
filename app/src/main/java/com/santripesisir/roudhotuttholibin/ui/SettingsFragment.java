package com.santripesisir.roudhotuttholibin.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.utils.ReaderPreferences;

public class SettingsFragment extends Fragment {

    private TextView tvFontSizeLabel;
    private Slider sliderFontSize;
    private TextView tvLineSpacingLabel;
    private Slider sliderLineSpacing;
    private TextView tvMarginLabel;
    private Slider sliderMargin;
    private RadioGroup rgFontType;
    private RadioButton rbFontAmiri;
    private RadioButton rbFontKfgqpc;
    private MaterialSwitch switchDarkMode;
    private MaterialButton btnResetSettings;

    private ReaderPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        preferences = new ReaderPreferences(requireContext());

        tvFontSizeLabel = view.findViewById(R.id.tv_font_size_label);
        sliderFontSize = view.findViewById(R.id.slider_font_size);
        tvLineSpacingLabel = view.findViewById(R.id.tv_line_spacing_label);
        sliderLineSpacing = view.findViewById(R.id.slider_line_spacing);
        tvMarginLabel = view.findViewById(R.id.tv_margin_label);
        sliderMargin = view.findViewById(R.id.slider_margin);
        rgFontType = view.findViewById(R.id.rg_font_type);
        rbFontAmiri = view.findViewById(R.id.rb_font_amiri);
        rbFontKfgqpc = view.findViewById(R.id.rb_font_kfgqpc);
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        btnResetSettings = view.findViewById(R.id.btn_reset_settings);

        loadPreferences();
        setupListeners();

        return view;
    }

    private void loadPreferences() {
        int fontSize = preferences.getFontSize();
        tvFontSizeLabel.setText("Ukuran Font: " + fontSize + "sp");
        sliderFontSize.setValue(fontSize);

        float lineSpacing = preferences.getLineSpacing();
        tvLineSpacingLabel.setText(String.format("Line Spacing: %.1f", lineSpacing));
        sliderLineSpacing.setValue(lineSpacing);

        int margin = preferences.getMargin();
        tvMarginLabel.setText("Margin Halaman: " + margin + "dp");
        sliderMargin.setValue(margin);

        String fontType = preferences.getFontType();
        if (ReaderPreferences.FONT_KFGQPC.equals(fontType)) {
            rbFontKfgqpc.setChecked(true);
        } else {
            rbFontAmiri.setChecked(true);
        }

        boolean isDark = preferences.isDarkMode();
        switchDarkMode.setChecked(isDark);
    }

    private void setupListeners() {
        sliderFontSize.addOnChangeListener((slider, value, fromUser) -> {
            int val = (int) value;
            preferences.setFontSize(val);
            tvFontSizeLabel.setText("Ukuran Font: " + val + "sp");
        });

        sliderLineSpacing.addOnChangeListener((slider, value, fromUser) -> {
            preferences.setLineSpacing(value);
            tvLineSpacingLabel.setText(String.format("Line Spacing: %.1f", value));
        });

        sliderMargin.addOnChangeListener((slider, value, fromUser) -> {
            int val = (int) value;
            preferences.setMargin(val);
            tvMarginLabel.setText("Margin Halaman: " + val + "dp");
        });

        rgFontType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_font_kfgqpc) {
                preferences.setFontType(ReaderPreferences.FONT_KFGQPC);
            } else {
                preferences.setFontType(ReaderPreferences.FONT_AMIRI);
            }
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setDarkMode(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        btnResetSettings.setOnClickListener(v -> {
            preferences.reset();
            loadPreferences();
            Toast.makeText(requireContext(), "Pengaturan diatur ulang ke default", Toast.LENGTH_SHORT).show();
            // Reapply dark mode if reset changed it
            if (preferences.isDarkMode()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }
}
