package com.santripesisir.roudhotuttholibin.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.data.BookmarkManager;
import com.santripesisir.roudhotuttholibin.reader.ReaderEngine;
import com.santripesisir.roudhotuttholibin.utils.ReaderPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ReaderActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ViewPager2 viewPager;
    private TextView tvChapterTitle;
    private TextView tvPageNumber;
    private Slider sliderPage;
    private ImageButton btnSearch;
    private ImageButton btnFont;
    private ImageButton btnBookmark;

    private String fileName;
    private String bookId;
    private String bookTitle;
    private int targetPage = -1;

    private ReaderEngine engine;
    private ReaderPreferences preferences;
    private BookmarkManager bookmarkManager;
    private ReaderPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = new ReaderPreferences(this);
        // Apply theme before view hierarchy is loaded
        if (preferences.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            setTheme(com.google.android.material.R.style.Theme_Material3_Dark_NoActionBar);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            setTheme(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        fileName = getIntent().getStringExtra("FILE_NAME");
        bookId = getIntent().getStringExtra("BOOK_ID");
        bookTitle = getIntent().getStringExtra("BOOK_TITLE");
        targetPage = getIntent().getIntExtra("TARGET_PAGE", -1);

        if (fileName == null || bookId == null) {
            Toast.makeText(this, "Kitab tidak valid!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbar);
        viewPager = findViewById(R.id.view_pager);
        tvChapterTitle = findViewById(R.id.tv_page_chapter_title);
        tvPageNumber = findViewById(R.id.tv_page_number);
        sliderPage = findViewById(R.id.slider_page);
        btnSearch = findViewById(R.id.btn_reader_search);
        btnFont = findViewById(R.id.btn_reader_font);
        btnBookmark = findViewById(R.id.btn_reader_bookmark);

        // Setup Toolbar
        toolbar.setTitle(bookTitle != null ? bookTitle : "Membaca");
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bookmarkManager = new BookmarkManager(this);
        engine = new ReaderEngine(this, fileName);

        try {
            engine.initialize();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Gagal memuat kitab: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int totalPages = engine.getPageCount();
        sliderPage.setValueTo(Math.max(1, totalPages));
        sliderPage.setValueFrom(1);

        adapter = new ReaderPagerAdapter();
        viewPager.setAdapter(adapter);

        // Setup listeners
        setupListeners(totalPages);

        // Load correct starting page
        int startPage = 0;
        if (targetPage >= 0 && targetPage < totalPages) {
            startPage = targetPage;
        } else {
            startPage = bookmarkManager.getLastReadPage(bookId);
            if (startPage < 0 || startPage >= totalPages) {
                startPage = 0;
            }
        }
        viewPager.setCurrentItem(startPage, false);
        updatePageIndicator(startPage, totalPages);
    }

    private void setupListeners(int totalPages) {
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                bookmarkManager.saveLastReadPage(bookId, position);
                updatePageIndicator(position, totalPages);
            }
        });

        sliderPage.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int pageIdx = (int) value - 1;
                viewPager.setCurrentItem(pageIdx, false);
            }
        });

        btnSearch.setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra("BOOK_ID", bookId);
            intent.putExtra("BOOK_TITLE", bookTitle);
            intent.putExtra("FILE_NAME", fileName);
            startActivity(intent);
        });

        btnFont.setOnClickListener(v -> showFontSettingsDialog());

        btnBookmark.setOnClickListener(v -> {
            int currentPage = viewPager.getCurrentItem();
            if (bookmarkManager.isBookmarked(bookId, currentPage)) {
                bookmarkManager.removeBookmark(bookId, currentPage);
                btnBookmark.clearColorFilter();
                Toast.makeText(this, "Bookmark dihapus", Toast.LENGTH_SHORT).show();
            } else {
                bookmarkManager.addBookmark(bookId, currentPage);
                btnBookmark.setColorFilter(getResources().getColor(com.google.android.material.R.color.design_default_color_primary, getTheme()));
                Toast.makeText(this, "Halaman disimpan ke bookmark", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePageIndicator(int position, int totalPages) {
        sliderPage.setValue(position + 1);
        tvPageNumber.setText("Halaman " + (position + 1) + " / " + totalPages);

        if (bookmarkManager.isBookmarked(bookId, position)) {
            btnBookmark.setColorFilter(getResources().getColor(com.google.android.material.R.color.design_default_color_primary, getTheme()));
        } else {
            btnBookmark.clearColorFilter();
        }

        try {
            JSONObject ch = engine.getChapter(position);
            String title = ch.optString("title", "Bab " + (position + 1));
            tvChapterTitle.setText(title);
        } catch (Exception e) {
            tvChapterTitle.setText("Halaman " + (position + 1));
        }
    }

    private void showFontSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_reader_options, null);
        dialog.setContentView(view);

        RadioGroup rgFontType = view.findViewById(R.id.dialog_rg_font_type);
        RadioButton rbAmiri = view.findViewById(R.id.dialog_rb_font_amiri);
        RadioButton rbKfgqpc = view.findViewById(R.id.dialog_rb_font_kfgqpc);
        TextView tvSizeLabel = view.findViewById(R.id.dialog_tv_font_size_label);
        Slider sliderFontSize = view.findViewById(R.id.dialog_slider_font_size);
        TextView tvSpacingLabel = view.findViewById(R.id.dialog_tv_line_spacing_label);
        Slider sliderLineSpacing = view.findViewById(R.id.dialog_slider_line_spacing);
        TextView tvMarginLabel = view.findViewById(R.id.dialog_tv_margin_label);
        Slider sliderMargin = view.findViewById(R.id.dialog_slider_margin);
        MaterialSwitch switchDark = view.findViewById(R.id.dialog_switch_dark_mode);

        // Load values
        if (ReaderPreferences.FONT_KFGQPC.equals(preferences.getFontType())) {
            rbKfgqpc.setChecked(true);
        } else {
            rbAmiri.setChecked(true);
        }

        int currentSize = preferences.getFontSize();
        tvSizeLabel.setText("Ukuran Teks: " + currentSize + "sp");
        sliderFontSize.setValue(currentSize);

        float currentSpacing = preferences.getLineSpacing();
        tvSpacingLabel.setText(String.format("Jarak Baris: %.1f", currentSpacing));
        sliderLineSpacing.setValue(currentSpacing);

        int currentMargin = preferences.getMargin();
        tvMarginLabel.setText("Margin Samping: " + currentMargin + "dp");
        sliderMargin.setValue(currentMargin);

        switchDark.setChecked(preferences.isDarkMode());

        // Setup changes
        rgFontType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.dialog_rb_font_kfgqpc) {
                preferences.setFontType(ReaderPreferences.FONT_KFGQPC);
            } else {
                preferences.setFontType(ReaderPreferences.FONT_AMIRI);
            }
            adapter.notifyDataSetChanged();
        });

        sliderFontSize.addOnChangeListener((slider, value, fromUser) -> {
            int val = (int) value;
            preferences.setFontSize(val);
            tvSizeLabel.setText("Ukuran Teks: " + val + "sp");
            adapter.notifyDataSetChanged();
        });

        sliderLineSpacing.addOnChangeListener((slider, value, fromUser) -> {
            preferences.setLineSpacing(value);
            tvSpacingLabel.setText(String.format("Jarak Baris: %.1f", value));
            adapter.notifyDataSetChanged();
        });

        sliderMargin.addOnChangeListener((slider, value, fromUser) -> {
            int val = (int) value;
            preferences.setMargin(val);
            tvMarginLabel.setText("Margin Samping: " + val + "dp");
            adapter.notifyDataSetChanged();
        });

        switchDark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setDarkMode(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            recreate();
        });

        dialog.show();
    }

    // --- Reader ViewPager2 Adapter ---
    private class ReaderPagerAdapter extends RecyclerView.Adapter<ReaderPagerAdapter.PageViewHolder> {

        // Cache for loaded chapter JSONs to avoid repeated disk reads during minor UI updates
        private final Map<Integer, JSONObject> chapterCache = new HashMap<>();

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_page, parent, false);
            return new PageViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return engine.getPageCount();
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            LinearLayout container;

            PageViewHolder(View v) {
                super(v);
                container = v.findViewById(R.id.page_container);
            }

            void bind(int position) {
                container.removeAllViews();

                // Apply margins dynamically
                int marginDp = preferences.getMargin();
                float density = container.getResources().getDisplayMetrics().density;
                int marginPx = (int) (marginDp * density);
                container.setPadding(marginPx, marginPx, marginPx, marginPx);

                // Prepare Typeface
                String fontType = preferences.getFontType();
                Typeface typeface;
                try {
                    if (ReaderPreferences.FONT_KFGQPC.equals(fontType)) {
                        typeface = Typeface.createFromAsset(container.getContext().getAssets(), "fonts/KFGQPC.ttf");
                    } else {
                        typeface = Typeface.createFromAsset(container.getContext().getAssets(), "fonts/Amiri-Regular.ttf");
                    }
                } catch (Exception e) {
                    try {
                        typeface = Typeface.createFromAsset(container.getContext().getAssets(), "fonts/Amiri-Regular.ttf");
                    } catch (Exception ex) {
                        typeface = Typeface.DEFAULT;
                    }
                }

                int fontSizeSp = preferences.getFontSize();
                float lineSpacing = preferences.getLineSpacing();

                try {
                    JSONObject ch = chapterCache.get(position);
                    if (ch == null) {
                        ch = engine.getChapter(position);
                        chapterCache.put(position, ch);
                    }

                    JSONArray blocks = ch.optJSONArray("blocks");
                    if (blocks != null) {
                        for (int i = 0; i < blocks.length(); i++) {
                            JSONObject block = blocks.getJSONObject(i);
                            String type = block.optString("type");
                            String text = block.optString("text");

                            if (text == null || text.trim().isEmpty()) continue;

                            TextView tv = new TextView(container.getContext());
                            tv.setText(text);
                            tv.setTypeface(typeface);
                            tv.setTextSize(fontSizeSp);
                            tv.setLineSpacing(0, lineSpacing);
                            tv.setTextDirection(View.TEXT_DIRECTION_RTL);
                            tv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                            
                            // Enable text selection and custom copy/share callback
                            tv.setTextIsSelectable(true);
                            tv.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                                @Override
                                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                                    return true;
                                }

                                @Override
                                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                                    return false;
                                }

                                @Override
                                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                                    int start = tv.getSelectionStart();
                                    int end = tv.getSelectionEnd();
                                    if (start < 0 || end < 0 || start == end) {
                                        return false;
                                    }
                                    int min = Math.min(start, end);
                                    int max = Math.max(start, end);
                                    CharSequence selectedText = tv.getText().subSequence(min, max);

                                    String finalBookTitle = bookTitle != null ? bookTitle : "";
                                    String physicalInfo = "";
                                    try {
                                        JSONObject pageJson = engine.getChapter(position);
                                        JSONArray jsonBlocks = pageJson.optJSONArray("blocks");
                                        if (jsonBlocks != null) {
                                            for (int b = jsonBlocks.length() - 1; b >= 0; b--) {
                                                JSONObject blk = jsonBlocks.optJSONObject(b);
                                                if (blk != null) {
                                                    String blkText = blk.optString("text", "");
                                                    if (blkText.contains("الجزء") || blkText.contains("الصفحة")) {
                                                        physicalInfo = blkText;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    if (physicalInfo.isEmpty()) {
                                        physicalInfo = "Halaman: " + (position + 1);
                                    }

                                    String formattedText = selectedText.toString() + "\n\n" +
                                            "(" + finalBookTitle + " - " + physicalInfo + ")";

                                    int id = item.getItemId();
                                    if (id == android.R.id.copy) {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                                                tv.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("Kitab Quote", formattedText);
                                        if (clipboard != null) {
                                            clipboard.setPrimaryClip(clip);
                                            Toast.makeText(tv.getContext(), "Kutipan disalin beserta referensi", Toast.LENGTH_SHORT).show();
                                        }
                                        mode.finish();
                                        return true;
                                    } else if (item.getTitle() != null && 
                                               (item.getTitle().toString().toLowerCase().contains("share") || 
                                                item.getTitle().toString().toLowerCase().contains("bagikan"))) {
                                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                        shareIntent.setType("text/plain");
                                        shareIntent.putExtra(Intent.EXTRA_TEXT, formattedText);
                                        tv.getContext().startActivity(Intent.createChooser(shareIntent, "Bagikan kutipan"));
                                        mode.finish();
                                        return true;
                                    }
                                    return false;
                                }

                                @Override
                                public void onDestroyActionMode(android.view.ActionMode mode) {
                                }
                            });

                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                            );
                            params.bottomMargin = (int) (12 * density);

                            if ("heading".equals(type)) {
                                tv.setTextSize(fontSizeSp + 4);
                                tv.setTypeface(typeface, Typeface.BOLD);
                                tv.setGravity(Gravity.CENTER);
                                params.topMargin = (int) (16 * density);
                            } else {
                                tv.setGravity(Gravity.START);
                            }

                            container.addView(tv, params);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    TextView tvError = new TextView(container.getContext());
                    tvError.setText("Gagal memuat konten halaman: " + e.getMessage());
                    container.addView(tvError);
                }
            }
        }
    }
}
