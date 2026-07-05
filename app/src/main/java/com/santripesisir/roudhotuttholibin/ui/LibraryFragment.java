package com.santripesisir.roudhotuttholibin.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.reader.ReaderEngine;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryFragment extends Fragment {

    private RecyclerView rvBooks;
    private BooksAdapter adapter;
    private TextInputEditText etSearch;
    private LinearLayout indexingProgressLayout;
    private TextView tvIndexingStatus;

    private SearchIndexManager searchIndexManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<BookItem> bookItems = new ArrayList<>();
    private List<BookItem> filteredItems = new ArrayList<>();

    private static class BookItem {
        String fileName;
        ReaderEngine.BookMetadata metadata;
        boolean isIndexed;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        
        rvBooks = view.findViewById(R.id.rv_books);
        etSearch = view.findViewById(R.id.et_search);
        indexingProgressLayout = view.findViewById(R.id.indexing_progress_layout);
        tvIndexingStatus = view.findViewById(R.id.tv_indexing_status);

        searchIndexManager = new SearchIndexManager(requireContext());

        rvBooks.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2));
        adapter = new BooksAdapter();
        rvBooks.setAdapter(adapter);

        // Setup search filter listener
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBooks(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        loadBooks();

        return view;
    }

    private void loadBooks() {
        executorService.execute(() -> {
            List<BookItem> items = new ArrayList<>();
            try {
                String[] assetsFiles = requireContext().getAssets().list("books");
                if (assetsFiles != null) {
                    for (String file : assetsFiles) {
                        if (file.endsWith(".mai")) {
                            BookItem item = new BookItem();
                            item.fileName = file;
                            try {
                                ReaderEngine engine = new ReaderEngine(requireContext(), file);
                                engine.initialize();
                                item.metadata = engine.getMetadata();
                                item.isIndexed = searchIndexManager.isBookIndexed(item.metadata.id);
                                searchIndexManager.registerBook(item.metadata);
                                items.add(item);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            mainHandler.post(() -> {
                bookItems = items;
                filterBooks(etSearch.getText() != null ? etSearch.getText().toString() : "");
            });
        });
    }

    private void filterBooks(String query) {
        filteredItems.clear();
        for (BookItem item : bookItems) {
            if (query.isEmpty() || item.metadata.title.toLowerCase().contains(query.toLowerCase())
                    || item.metadata.author.toLowerCase().contains(query.toLowerCase())) {
                filteredItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void startIndexing(BookItem item) {
        indexingProgressLayout.setVisibility(View.VISIBLE);
        tvIndexingStatus.setText("Mengindeks " + item.metadata.title + "...");

        executorService.execute(() -> {
            try {
                searchIndexManager.indexBook(requireContext(), item.fileName, item.metadata, () -> {
                    // Progress callback
                });
                mainHandler.post(() -> {
                    indexingProgressLayout.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Indeks berhasil dibuat!", Toast.LENGTH_SHORT).show();
                    loadBooks(); // refresh list to update badges
                    openBook(item);
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    indexingProgressLayout.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Gagal membuat indeks: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openBook(BookItem item) {
        Intent intent = new Intent(requireContext(), ReaderActivity.class);
        intent.putExtra("FILE_NAME", item.fileName);
        intent.putExtra("BOOK_ID", item.metadata.id);
        intent.putExtra("BOOK_TITLE", item.metadata.title);
        startActivity(intent);
    }

    // --- Adapter ---
    private class BooksAdapter extends RecyclerView.Adapter<BooksAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BookItem item = filteredItems.get(position);
            holder.tvTitle.setText(item.metadata.title);
            holder.tvAuthor.setText(item.metadata.author);
            holder.tvStats.setText(item.metadata.chapterCount + " Halaman");

            if (item.isIndexed) {
                holder.tvBadge.setText("Indeks Aktif");
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_indexed);
            } else {
                holder.tvBadge.setText("Buat Indeks");
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_unindexed);
            }

            holder.itemView.setOnClickListener(v -> {
                if (item.isIndexed) {
                    openBook(item);
                } else {
                    startIndexing(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvAuthor, tvStats, tvBadge;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_book_title);
                tvAuthor = v.findViewById(R.id.tv_book_author);
                tvStats = v.findViewById(R.id.tv_book_stats);
                tvBadge = v.findViewById(R.id.tv_indexing_badge);
            }
        }
    }
}
