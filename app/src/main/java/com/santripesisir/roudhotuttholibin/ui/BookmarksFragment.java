package com.santripesisir.roudhotuttholibin.ui;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.data.BookmarkManager;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BookmarksFragment extends Fragment {

    private RecyclerView rvBookmarks;
    private TextView tvEmpty;
    private BookmarksAdapter adapter;
    private BookmarkManager bookmarkManager;
    private SearchIndexManager searchDb;

    private List<BookmarkItem> bookmarkItems = new ArrayList<>();

    private static class BookmarkItem {
        String bookId;
        String bookTitle;
        int pageIndex; // 0-indexed
        String chapterTitle;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bookmarks, container, false);

        rvBookmarks = view.findViewById(R.id.rv_bookmarks);
        tvEmpty = view.findViewById(R.id.tv_empty);

        bookmarkManager = new BookmarkManager(requireContext());
        searchDb = new SearchIndexManager(requireContext());

        rvBookmarks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookmarksAdapter();
        rvBookmarks.setAdapter(adapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBookmarks();
    }

    private void loadBookmarks() {
        bookmarkItems.clear();
        SQLiteDatabase db = searchDb.getReadableDatabase();

        // 1. Get all books registered
        Cursor bookCursor = db.rawQuery("SELECT id, title FROM books", null);
        if (bookCursor.moveToFirst()) {
            do {
                String bookId = bookCursor.getString(0);
                String bookTitle = bookCursor.getString(1);

                // 2. Get bookmarks for this book
                Set<String> bookmarkedPages = bookmarkManager.getBookmarks(bookId);
                for (String pageStr : bookmarkedPages) {
                    try {
                        int pageIndex = Integer.parseInt(pageStr);
                        int pageId = pageIndex + 1; // database is 1-indexed

                        // Get chapter title from DB index
                        String chapterTitle = "Halaman " + pageId;
                        Cursor pageCursor = db.rawQuery("SELECT chapter_title FROM pages WHERE book_id = ? AND page_id = ?",
                                new String[]{bookId, String.valueOf(pageId)});
                        if (pageCursor.moveToFirst()) {
                            String dbTitle = pageCursor.getString(0);
                            if (dbTitle != null && !dbTitle.isEmpty()) {
                                chapterTitle = dbTitle;
                            }
                        }
                        pageCursor.close();

                        BookmarkItem item = new BookmarkItem();
                        item.bookId = bookId;
                        item.bookTitle = bookTitle;
                        item.pageIndex = pageIndex;
                        item.chapterTitle = chapterTitle;
                        bookmarkItems.add(item);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } while (bookCursor.moveToNext());
        }
        bookCursor.close();

        if (bookmarkItems.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvBookmarks.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvBookmarks.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BookmarkItem item = bookmarkItems.get(position);
            holder.tvTitle.setText(item.bookTitle);
            holder.tvChapter.setText(item.chapterTitle + " - (Halaman " + (item.pageIndex + 1) + ")");

            holder.btnDelete.setOnClickListener(v -> {
                bookmarkManager.removeBookmark(item.bookId, item.pageIndex);
                Toast.makeText(requireContext(), "Bookmark dihapus", Toast.LENGTH_SHORT).show();
                loadBookmarks();
            });

            holder.itemView.setOnClickListener(v -> {
                // Find fileName by matching files in assets
                try {
                    String[] assetsFiles = requireContext().getAssets().list("books");
                    if (assetsFiles != null) {
                        for (String file : assetsFiles) {
                            if (file.replace(".mai", "").equals(item.bookId)) {
                                Intent intent = new Intent(requireContext(), ReaderActivity.class);
                                intent.putExtra("FILE_NAME", file);
                                intent.putExtra("BOOK_ID", item.bookId);
                                intent.putExtra("BOOK_TITLE", item.bookTitle);
                                intent.putExtra("TARGET_PAGE", item.pageIndex);
                                startActivity(intent);
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public int getItemCount() {
            return bookmarkItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvChapter;
            ImageButton btnDelete;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_bookmark_title);
                tvChapter = v.findViewById(R.id.tv_bookmark_chapter);
                btnDelete = v.findViewById(R.id.btn_delete_bookmark);
            }
        }
    }
}
