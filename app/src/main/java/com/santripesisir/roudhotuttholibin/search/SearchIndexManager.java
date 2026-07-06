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
    private static final int DATABASE_VERSION = 3;

    public static class Chat {
        public String id;
        public String title;
        public long createdAt;
        public long updatedAt;
    }

    public static class ChatMessage {
        public String id;
        public String chatId;
        public String role;
        public String content;
        public long createdAt;
    }

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

        db.execSQL("CREATE TABLE IF NOT EXISTS chats (" +
                "id TEXT PRIMARY KEY," +
                "title TEXT," +
                "created_at INTEGER," +
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
                "id TEXT PRIMARY KEY," +
                "chat_id TEXT," +
                "role TEXT," +
                "content TEXT," +
                "created_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS chats (" +
                    "id TEXT PRIMARY KEY," +
                    "title TEXT," +
                    "created_at INTEGER," +
                    "updated_at INTEGER)");

            db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
                    "id TEXT PRIMARY KEY," +
                    "chat_id TEXT," +
                    "role TEXT," +
                    "content TEXT," +
                    "created_at INTEGER)");
        }
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

    public String getPageContent(String bookId, int pageId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT content FROM pages WHERE book_id = ? AND page_id = ?",
                new String[]{bookId, String.valueOf(pageId)});
        String content = "";
        if (cursor.moveToFirst()) {
            content = cursor.getString(0);
        }
        cursor.close();
        return content;
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
                String contentNormalized = normalizeArabic(content);

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

    /**
     * Normalizes Arabic text for fuzzy/tolerant search matching.
     *
     * Steps applied (in order):
     *  1. Remove all harakat / diacritics (U+064B–U+065F, U+0670, U+06D6–U+06DC, U+06DF–U+06E4, U+06E7–U+06E8, U+06EA–U+06ED)
     *  2. Normalize Alef variants → bare Alef ا
     *     (أ U+0623, إ U+0625, آ U+0622, ٱ U+0671 → ا U+0627)
     *  3. Normalize Hamza variants → bare Hamza ء
     *     (ئ U+0626, ؤ U+0624 → ء U+0621)
     *  4. Normalize Ta Marbuta → Ha  (ة U+0629 → ه U+0647)
     *  5. Normalize Alef Maqsura → Ya (ى U+0649 → ي U+064A)
     *  6. Collapse multiple spaces/newlines into single space & trim.
     */
    public static String normalizeArabic(String text) {
        if (text == null) return null;
        // 1. Remove all harakat (diacritics) – standard + extended ranges
        String s = text.replaceAll(
                "[\u064B-\u065F\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]", "");
        // 2. Alef variants → bare Alef
        s = s.replace('\u0623', '\u0627') // أ → ا
             .replace('\u0625', '\u0627') // إ → ا
             .replace('\u0622', '\u0627') // آ → ا
             .replace('\u0671', '\u0627'); // ٱ → ا
        // 3. Hamza-on-chair variants → bare Hamza
        s = s.replace('\u0626', '\u0621') // ئ → ء
             .replace('\u0624', '\u0621'); // ؤ → ء
        // 4. Ta Marbuta → Ha
        s = s.replace('\u0629', '\u0647'); // ة → ه
        // 5. Alef Maqsura → Ya
        s = s.replace('\u0649', '\u064A'); // ى → ي
        // 6. Collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /**
     * @deprecated Use {@link #normalizeArabic(String)} instead which includes
     *             full hamzah, alef, and ta-marbuta normalization.
     */
    @Deprecated
    public static String removeHarakat(String text) {
        return normalizeArabic(text);
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

        // Always search against the normalized column so that hamzah/alef/harakat
        // variants in both the query and the indexed data are handled uniformly.
        // The ignoreHarakat checkbox is kept for UI clarity but normalization is
        // always applied since indexing always writes content_normalized.
        String searchColumn = "p.content_normalized";

        if (sameOrder) {
            // Concatenate keywords with wildcard in between
            StringBuilder regexPattern = new StringBuilder("%");
            for (String kw : keywords) {
                String term = normalizeArabic(kw);
                regexPattern.append(term).append("%");
            }
            sql.append("AND ").append(searchColumn).append(" LIKE ? ");
            args.add(regexPattern.toString());
        } else {
            // Logika AND: each keyword must be present in the content
            for (String kw : keywords) {
                String term = normalizeArabic(kw);
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
        // Always use normalized form for matching position so hamzah/alef variants are found
        String testContent = normalizeArabic(content);
        String firstKw = keywords.get(0);
        String testKw = normalizeArabic(firstKw);

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

    // =========================================================================
    // CHAT & MESSAGES DATABASE METHODS
    // =========================================================================

    public void insertChat(Chat chat) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", chat.id);
        cv.put("title", chat.title);
        cv.put("created_at", chat.createdAt);
        cv.put("updated_at", chat.updatedAt);
        db.insertWithOnConflict("chats", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Chat> getAllChats() {
        List<Chat> chats = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, title, created_at, updated_at FROM chats ORDER BY updated_at DESC", null);
        if (cursor.moveToFirst()) {
            do {
                Chat chat = new Chat();
                chat.id = cursor.getString(0);
                chat.title = cursor.getString(1);
                chat.createdAt = cursor.getLong(2);
                chat.updatedAt = cursor.getLong(3);
                chats.add(chat);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return chats;
    }

    public void deleteChat(String chatId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("chats", "id = ?", new String[]{chatId});
        db.delete("messages", "chat_id = ?", new String[]{chatId});
    }

    public void updateChatTitle(String chatId, String title) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("updated_at", System.currentTimeMillis());
        db.update("chats", cv, "id = ?", new String[]{chatId});
    }

    public void insertMessage(ChatMessage msg) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", msg.id);
        cv.put("chat_id", msg.chatId);
        cv.put("role", msg.role);
        cv.put("content", msg.content);
        cv.put("created_at", msg.createdAt);
        db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE);

        // Update timestamps chat induknya agar terdorong ke atas di daftar riwayat
        ContentValues cvChat = new ContentValues();
        cvChat.put("updated_at", System.currentTimeMillis());
        db.update("chats", cvChat, "id = ?", new String[]{msg.chatId});
    }

    public List<ChatMessage> getMessagesForChat(String chatId) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, chat_id, role, content, created_at FROM messages WHERE chat_id = ? ORDER BY created_at ASC", new String[]{chatId});
        if (cursor.moveToFirst()) {
            do {
                ChatMessage msg = new ChatMessage();
                msg.id = cursor.getString(0);
                msg.chatId = cursor.getString(1);
                msg.role = cursor.getString(2);
                msg.content = cursor.getString(3);
                msg.createdAt = cursor.getLong(4);
                messages.add(msg);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return messages;
    }
}
