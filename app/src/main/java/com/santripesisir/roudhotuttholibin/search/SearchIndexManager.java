package com.santripesisir.roudhotuttholibin.search;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.santripesisir.roudhotuttholibin.reader.ReaderEngine;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchIndexManager extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mai_reader.db";
    private static final int DATABASE_VERSION = 1;

    public static class SearchResult {
        public String bookId;
        public String bookTitle;
        public int pageId;
        public String chapterTitle;
        public String snippet;
    }

    public SearchIndexManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS books (" +
                "id TEXT PRIMARY KEY," +
                "title TEXT," +
                "author TEXT," +
                "chapter_count INTEGER," +
                "is_indexed INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS pages (" +
                "book_id TEXT," +
                "page_id INTEGER," +
                "chapter_title TEXT," +
                "content TEXT," +
                "content_normalized TEXT," +
                "PRIMARY KEY (book_id, page_id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS books");
        db.execSQL("DROP TABLE IF EXISTS pages");
        onCreate(db);
    }

    public boolean isBookIndexed(String bookId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT is_indexed FROM books WHERE id = ?", new String[]{bookId});
        boolean indexed = false;
        if (cursor.moveToFirst()) {
            indexed = cursor.getInt(0) == 1;
        }
        cursor.close();
        return indexed;
    }

    public void registerBook(ReaderEngine.BookMetadata meta) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", meta.id);
        cv.put("title", meta.title);
        cv.put("author", meta.author);
        cv.put("chapter_count", meta.chapterCount);
        db.insertWithOnConflict("books", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void indexBook(Context context, String fileName, ReaderEngine.BookMetadata meta, Runnable progressCallback) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        
        // Check if already indexed
        if (isBookIndexed(meta.id)) {
            return;
        }

        registerBook(meta);

        ReaderEngine engine = new ReaderEngine(context, fileName);
        engine.initialize();

        db.beginTransaction();
        try {
            int pageCount = engine.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                JSONObject ch = engine.getChapter(i);
                int pageId = ch.optInt("id", i + 1);
                String chapterTitle = ch.optString("title", "");
                
                // Extract text from blocks
                StringBuilder sb = new StringBuilder();
                JSONArray blocks = ch.optJSONArray("blocks");
                if (blocks != null) {
                    for (int j = 0; j < blocks.length(); j++) {
                        JSONObject block = blocks.getJSONObject(j);
                        String type = block.optString("type");
                        if ("paragraph".equals(type) || "heading".equals(type)) {
                            String text = block.optString("text");
                            if (text != null && !text.isEmpty()) {
                                sb.append(text).append("\n");
                            }
                        }
                    }
                }
                String content = sb.toString().trim();
                String contentNormalized = removeHarakat(content);

                ContentValues cv = new ContentValues();
                cv.put("book_id", meta.id);
                cv.put("page_id", pageId);
                cv.put("chapter_title", chapterTitle);
                cv.put("content", content);
                cv.put("content_normalized", contentNormalized);

                db.insertWithOnConflict("pages", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                
                if (i % 100 == 0 && progressCallback != null) {
                    progressCallback.run();
                }
            }

            ContentValues cvMeta = new ContentValues();
            cvMeta.put("is_indexed", 1);
            db.update("books", cvMeta, "id = ?", new String[]{meta.id});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static String removeHarakat(String text) {
        if (text == null) return null;
        // Standard Arabic diacritic character ranges: U+064B to U+065F, and U+0670 (superscript alef)
        return text.replaceAll("[\\u064B-\\u065F\\u0670]", "");
    }

    public List<SearchResult> search(
            String activeBookId,
            List<String> keywords,
            boolean ignoreHarakat,
            boolean caseSensitive,
            boolean sameOrder,
            boolean searchAllBooks
    ) {
        List<SearchResult> results = new ArrayList<>();
        if (keywords == null || keywords.isEmpty()) return results;

        SQLiteDatabase db = getReadableDatabase();
        StringBuilder sql = new StringBuilder();
        List<String> args = new ArrayList<>();

        sql.append("SELECT p.book_id, b.title as book_title, p.page_id, p.chapter_title, p.content ")
           .append("FROM pages p ")
           .append("JOIN books b ON p.book_id = b.id ")
           .append("WHERE 1=1 ");

        if (!searchAllBooks && activeBookId != null) {
            sql.append("AND p.book_id = ? ");
            args.add(activeBookId);
        }

        String searchColumn = ignoreHarakat ? "p.content_normalized" : "p.content";

        if (sameOrder) {
            // Concatenate keywords with wildcard in between
            StringBuilder regexPattern = new StringBuilder("%");
            for (String kw : keywords) {
                String term = ignoreHarakat ? removeHarakat(kw) : kw;
                regexPattern.append(term).append("%");
            }
            sql.append("AND ").append(searchColumn).append(" LIKE ? ");
            args.add(regexPattern.toString());
        } else {
            // Logika AND: each keyword must be present in the content
            for (String kw : keywords) {
                String term = ignoreHarakat ? removeHarakat(kw) : kw;
                sql.append("AND ").append(searchColumn).append(" LIKE ? ");
                args.add("%" + term + "%");
            }
        }

        // Limit search results to avoid freezing the system
        sql.append("LIMIT 300");

        Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                SearchResult res = new SearchResult();
                res.bookId = cursor.getString(0);
                res.bookTitle = cursor.getString(1);
                res.pageId = cursor.getInt(2);
                res.chapterTitle = cursor.getString(3);
                
                String rawContent = cursor.getString(4);
                res.snippet = generateSnippet(rawContent, keywords, ignoreHarakat);
                results.add(res);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return results;
    }

    private String generateSnippet(String content, List<String> keywords, boolean ignoreHarakat) {
        if (content == null || content.isEmpty()) return "";
        // Find index of the first keyword in the content
        String testContent = ignoreHarakat ? removeHarakat(content) : content;
        String firstKw = keywords.get(0);
        String testKw = ignoreHarakat ? removeHarakat(firstKw) : firstKw;

        int index = testContent.toLowerCase().indexOf(testKw.toLowerCase());
        if (index == -1) {
            // fallback
            return content.length() > 150 ? content.substring(0, 150) + "..." : content;
        }

        // Project the index back to the raw content. Because harakat removal alters string lengths,
        // we can do a simple alignment or just slice a region around the index.
        // For simplicity and speed, let's take a substring of rawContent near a estimated ratio or just map it.
        // Let's approximate the offset, or find character index in rawContent by traversing and skipping diacritics.
        int rawIndex = 0;
        int cleanIndex = 0;
        while (rawIndex < content.length() && cleanIndex < index) {
            char c = content.charAt(rawIndex);
            if (c >= 0x064B && c <= 0x065F || c == 0x0670) {
                rawIndex++;
            } else {
                rawIndex++;
                cleanIndex++;
            }
        }

        int start = Math.max(0, rawIndex - 60);
        int end = Math.min(content.length(), rawIndex + 120);

        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";

        return prefix + content.substring(start, end).replace('\n', ' ') + suffix;
    }
}
