package com.santripesisir.roudhotuttholibin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.reader.ReaderEngine;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager;
import com.santripesisir.roudhotuttholibin.utils.ReaderPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private LinearLayout filtersContainer;
    private MaterialButton btnAddFilter;
    private CheckBox cbIgnoreHarakat;
    private CheckBox cbSameOrder;
    private CheckBox cbSearchAllBooks;
    private MaterialButton btnExecuteSearch;
    private LinearProgressIndicator progressBar;
    private TextView tvResultsCount;
    private RecyclerView rvResults;

    private String activeBookId;
    private String activeBookTitle;
    private String activeFileName;

    private SearchIndexManager searchDb;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ResultsAdapter adapter;
    private List<SearchIndexManager.SearchResult> searchResults = new ArrayList<>();
    private List<String> lastUsedKeywords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ReaderPreferences preferences = new ReaderPreferences(this);
        // Set theme based on dark mode preferences
        if (preferences.isDarkMode()) {
            setTheme(com.google.android.material.R.style.Theme_Material3_Dark_NoActionBar);
        } else {
            setTheme(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        activeBookId = getIntent().getStringExtra("BOOK_ID");
        activeBookTitle = getIntent().getStringExtra("BOOK_TITLE");
        activeFileName = getIntent().getStringExtra("FILE_NAME");

        toolbar = findViewById(R.id.toolbar);
        filtersContainer = findViewById(R.id.filters_container);
        btnAddFilter = findViewById(R.id.btn_add_filter);
        cbIgnoreHarakat = findViewById(R.id.cb_ignore_harakat);
        cbSameOrder = findViewById(R.id.cb_same_order);
        cbSearchAllBooks = findViewById(R.id.cb_search_all_books);
        btnExecuteSearch = findViewById(R.id.btn_execute_search);
        progressBar = findViewById(R.id.search_progress);
        tvResultsCount = findViewById(R.id.tv_results_count);
        rvResults = findViewById(R.id.rv_search_results);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (activeBookTitle != null) {
            toolbar.setTitle("Cari di: " + activeBookTitle);
            cbSearchAllBooks.setChecked(false);
        } else {
            toolbar.setTitle("Pencarian Multi Filter");
            cbSearchAllBooks.setChecked(true);
            cbSearchAllBooks.setEnabled(false); // Can only search all books
        }

        searchDb = new SearchIndexManager(this);

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter();
        rvResults.setAdapter(adapter);

        // Add first filter field automatically
        addFilterField();

        btnAddFilter.setOnClickListener(v -> {
            if (filtersContainer.getChildCount() >= 5) {
                Toast.makeText(this, "Maksimal 5 filter pencarian", Toast.LENGTH_SHORT).show();
                return;
            }
            addFilterField();
        });

        btnExecuteSearch.setOnClickListener(v -> executeSearch());
    }

    private void addFilterField() {
        View filterView = LayoutInflater.from(this).inflate(R.layout.item_search_filter, filtersContainer, false);
        ImageButton btnRemove = filterView.findViewById(R.id.btn_remove_filter);

        btnRemove.setOnClickListener(v -> {
            if (filtersContainer.getChildCount() <= 1) {
                Toast.makeText(this, "Minimal harus ada 1 filter", Toast.LENGTH_SHORT).show();
                return;
            }
            filtersContainer.removeView(filterView);
        });

        filtersContainer.addView(filterView);
    }

    private void executeSearch() {
        List<String> keywords = new ArrayList<>();
        for (int i = 0; i < filtersContainer.getChildCount(); i++) {
            View view = filtersContainer.getChildAt(i);
            TextInputEditText et = view.findViewById(R.id.et_filter);
            if (et != null && et.getText() != null) {
                String val = et.getText().toString().trim();
                if (!val.isEmpty()) {
                    keywords.add(val);
                }
            }
        }

        if (keywords.isEmpty()) {
            Toast.makeText(this, "Masukkan minimal satu kata kunci!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvResultsCount.setVisibility(View.GONE);
        btnExecuteSearch.setEnabled(false);

        boolean ignoreHarakat = cbIgnoreHarakat.isChecked();
        boolean sameOrder = cbSameOrder.isChecked();
        boolean searchAll = cbSearchAllBooks.isChecked();

        lastUsedKeywords = new ArrayList<>(keywords);

        executorService.execute(() -> {
            // Jika mode cari semua kitab, pastikan semua kitab sudah ter-index terlebih dahulu.
            // Kitab yang belum ter-index tidak akan muncul di hasil pencarian.
            if (searchAll) {
                ensureAllBooksIndexed();
            }

            List<SearchIndexManager.SearchResult> results = searchDb.search(
                    activeBookId,
                    keywords,
                    ignoreHarakat,
                    false,
                    sameOrder,
                    searchAll
            );

            mainHandler.post(() -> {
                searchResults = results;
                progressBar.setVisibility(View.GONE);
                btnExecuteSearch.setEnabled(true);
                tvResultsCount.setVisibility(View.VISIBLE);
                tvResultsCount.setText("Hasil Pencarian (" + results.size() + ")");
                adapter.notifyDataSetChanged();

                if (results.isEmpty()) {
                    Toast.makeText(this, "Tidak ada hasil ditemukan", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Memastikan semua kitab di folder assets/books sudah ter-index.
     * Dipanggil dari background thread sebelum pencarian multi-kitab.
     * Kitab yang sudah ter-index dilewati (tidak di-index ulang).
     */
    private void ensureAllBooksIndexed() {
        try {
            String[] assetFiles = getAssets().list("books");
            if (assetFiles == null) return;

            int totalToIndex = 0;
            // Hitung berapa kitab yang belum ter-index
            for (String file : assetFiles) {
                if (!file.endsWith(".mai")) continue;
                String bookId = file.replace(".mai", "");
                if (!searchDb.isBookIndexed(bookId)) totalToIndex++;
            }

            if (totalToIndex == 0) return; // Semua sudah ter-index

            final int[] indexed = {0};
            for (String file : assetFiles) {
                if (!file.endsWith(".mai")) continue;
                String bookId = file.replace(".mai", "");
                if (searchDb.isBookIndexed(bookId)) continue;

                try {
                    ReaderEngine engine = new ReaderEngine(this, file);
                    engine.initialize();
                    ReaderEngine.BookMetadata meta = engine.getMetadata();

                    final int currentNum = ++indexed[0];
                    final int total = totalToIndex;
                    mainHandler.post(() -> {
                        if (!isFinishing()) {
                            tvResultsCount.setVisibility(View.VISIBLE);
                            tvResultsCount.setText("Mengindeks kitab " + currentNum + "/" + total + ": " + meta.title);
                        }
                    });

                    searchDb.indexBook(this, file, meta, null);
                } catch (Exception e) {
                    e.printStackTrace(); // Lewati kitab yang gagal diproses
                }
            }

            mainHandler.post(() -> {
                if (!isFinishing()) {
                    tvResultsCount.setVisibility(View.GONE);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchIndexManager.SearchResult result = searchResults.get(position);
            holder.tvBookTitle.setText(result.bookTitle);
            holder.tvPageNumber.setText("Halaman " + result.pageId);
            holder.tvChapterTitle.setText(result.chapterTitle);

            // Highlight keywords in the snippet
            String snippetText = result.snippet;
            SpannableString spannable = new SpannableString(snippetText);

            boolean ignoreHarakat = cbIgnoreHarakat.isChecked();
            android.util.TypedValue typedValue = new android.util.TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true);
            int colorHighlight = typedValue.data;

            for (String kw : lastUsedKeywords) {
                String cleanSnippet = SearchIndexManager.normalizeArabic(snippetText);
                String cleanKw = SearchIndexManager.normalizeArabic(kw);

                int index = cleanSnippet.toLowerCase().indexOf(cleanKw.toLowerCase());
                while (index != -1) {
                    // Map cleaned string index back to raw string index
                    int start = 0;
                    int cleanCount = 0;
                    while (start < snippetText.length() && cleanCount < index) {
                        char c = snippetText.charAt(start);
                        if (c >= 0x064B && c <= 0x065F || c == 0x0670) {
                            start++;
                        } else {
                            start++;
                            cleanCount++;
                        }
                    }

                    int end = start;
                    int kwCleanCount = 0;
                    while (end < snippetText.length() && kwCleanCount < cleanKw.length()) {
                        char c = snippetText.charAt(end);
                        if (c >= 0x064B && c <= 0x065F || c == 0x0670) {
                            end++;
                        } else {
                            end++;
                            kwCleanCount++;
                        }
                    }

                    if (start < end && end <= snippetText.length()) {
                        spannable.setSpan(
                                new BackgroundColorSpan(colorHighlight),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }

                    index = cleanSnippet.toLowerCase().indexOf(cleanKw.toLowerCase(), index + 1);
                }
            }

            holder.tvSnippet.setText(spannable);

            holder.itemView.setOnClickListener(v -> {
                try {
                    String matchedFile = null;
                    String[] assetsFiles = getAssets().list("books");
                    if (assetsFiles != null) {
                        for (String file : assetsFiles) {
                            if (file.replace(".mai", "").equals(result.bookId)) {
                                matchedFile = file;
                                break;
                            }
                        }
                    }

                    if (matchedFile != null) {
                        Intent intent = new Intent(SearchActivity.this, ReaderActivity.class);
                        intent.putExtra("FILE_NAME", matchedFile);
                        intent.putExtra("BOOK_ID", result.bookId);
                        intent.putExtra("BOOK_TITLE", result.bookTitle);
                        intent.putExtra("TARGET_PAGE", result.pageId - 1); // 0-indexed reader
                        intent.putStringArrayListExtra("HIGHLIGHT_KEYWORDS", new ArrayList<>(lastUsedKeywords));
                        intent.putExtra("IGNORE_HARAKAT", ignoreHarakat);
                        startActivity(intent);
                    } else {
                        Toast.makeText(SearchActivity.this, "File kitab tidak ditemukan!", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public int getItemCount() {
            return searchResults.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBookTitle, tvPageNumber, tvChapterTitle, tvSnippet;

            ViewHolder(View v) {
                super(v);
                tvBookTitle = v.findViewById(R.id.tv_result_book_title);
                tvPageNumber = v.findViewById(R.id.tv_result_page_number);
                tvChapterTitle = v.findViewById(R.id.tv_result_chapter_title);
                tvSnippet = v.findViewById(R.id.tv_result_snippet);
            }
        }
    }
}
