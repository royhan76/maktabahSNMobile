package com.santripesisir.roudhotuttholibin.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class BookmarkManager {

    private static final String PREFS_BOOKMARKS = "mai_reader_bookmarks";
    private static final String KEY_HISTORY_PREFIX = "history_";
    private static final String KEY_BOOKMARKS_PREFIX = "bookmarks_";

    private final SharedPreferences prefs;

    public BookmarkManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_BOOKMARKS, Context.MODE_PRIVATE);
    }

    // --- History ---
    public int getLastReadPage(String bookId) {
        return prefs.getInt(KEY_HISTORY_PREFIX + bookId, 0); // Default to first page (index 0)
    }

    public void saveLastReadPage(String bookId, int pageIndex) {
        prefs.edit().putInt(KEY_HISTORY_PREFIX + bookId, pageIndex).apply();
    }

    // --- Bookmarks ---
    public Set<String> getBookmarks(String bookId) {
        return prefs.getStringSet(KEY_BOOKMARKS_PREFIX + bookId, new HashSet<>());
    }

    public void addBookmark(String bookId, int pageIndex) {
        Set<String> bookmarks = new HashSet<>(getBookmarks(bookId));
        bookmarks.add(String.valueOf(pageIndex));
        prefs.edit().putStringSet(KEY_BOOKMARKS_PREFIX + bookId, bookmarks).apply();
    }

    public void removeBookmark(String bookId, int pageIndex) {
        Set<String> bookmarks = new HashSet<>(getBookmarks(bookId));
        bookmarks.remove(String.valueOf(pageIndex));
        prefs.edit().putStringSet(KEY_BOOKMARKS_PREFIX + bookId, bookmarks).apply();
    }

    public boolean isBookmarked(String bookId, int pageIndex) {
        return getBookmarks(bookId).contains(String.valueOf(pageIndex));
    }
}
