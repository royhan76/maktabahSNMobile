package com.santripesisir.roudhotuttholibin.reader;

import android.content.Context;
import com.santripesisir.roudhotuttholibin.utils.CipherUtils;
import com.santripesisir.roudhotuttholibin.utils.ZlibUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ReaderEngine {

    public static class ChapterIndex {
        public long offset;
        public int compLen;
        public int origLen;
    }

    public static class BookMetadata {
        public String id;
        public String title;
        public String author;
        public String publisher;
        public String isbn;
        public String language;
        public int chapterCount;
        public int wordCount;
        public int paragraphCount;
    }

    /** Represents one entry in the Table of Contents. */
    public static class TocEntry {
        /** 0-based page/chapter index in the reader. */
        public int pageIndex;
        /** Display title from chapter title or first heading. */
        public String title;
        /**
         * Hierarchy level: 1 = Bab (top), 2 = Fashl, 3 = Sub-Fashl, etc.
         * Derived from the first heading block's "level" field, or 1 if none.
         */
        public int level;
        /** Optional physical page info (e.g. "الجزء: 1 - الصفحة: 11"). */
        public String pageInfo;
    }

    private final Context context;
    private final String fileName;
    private final String bookId;

    private BookMetadata metadata;
    private final List<ChapterIndex> chaptersIndices = new ArrayList<>();
    private final File cacheFile;

    public ReaderEngine(Context context, String fileName) {
        this.context = context;
        this.fileName = fileName;
        this.bookId = fileName.replace(".mai", "");
        this.cacheFile = new File(context.getCacheDir(), bookId + ".dec");
    }

    public void initialize() throws Exception {
        InputStream is = context.getAssets().open("books/" + fileName);
        
        byte[] headerBytes = new byte[68];
        int read = is.read(headerBytes);
        if (read < 68) {
            is.close();
            throw new Exception("Invalid MAI file header: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] magic = new byte[4];
        buffer.get(magic);
        if (magic[0] != 'M' || magic[1] != 'A' || magic[2] != 'I' || magic[3] != 1) {
            is.close();
            throw new Exception("Invalid magic bytes in MAI file");
        }

        byte[] payloadHash = new byte[32];
        buffer.get(payloadHash);

        byte[] iv = new byte[16];
        buffer.get(iv);

        int metaOffset = buffer.getInt();
        int metaCompLen = buffer.getInt();
        int metaLen = buffer.getInt();
        int numChapters = buffer.getInt();

        int indexTableSize = numChapters * 12;
        byte[] indexTableBytes = new byte[indexTableSize];
        read = is.read(indexTableBytes);
        if (read < indexTableSize) {
            is.close();
            throw new Exception("Invalid index table size");
        }

        ByteBuffer idxBuffer = ByteBuffer.wrap(indexTableBytes);
        idxBuffer.order(ByteOrder.BIG_ENDIAN);

        chaptersIndices.clear();
        for (int i = 0; i < numChapters; i++) {
            ChapterIndex idx = new ChapterIndex();
            idx.offset = idxBuffer.getInt() & 0xFFFFFFFFL;
            idx.compLen = idxBuffer.getInt();
            idx.origLen = idxBuffer.getInt();
            chaptersIndices.add(idx);
        }

        if (!cacheFile.exists() || cacheFile.length() == 0) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] tempBuf = new byte[4096];
            int len;
            while ((len = is.read(tempBuf)) != -1) {
                bos.write(tempBuf, 0, len);
            }
            byte[] encryptedPayload = bos.toByteArray();

            byte[] keyBytes = CipherUtils.getAESKey();
            byte[] decryptedPayload = CipherUtils.decryptAES(encryptedPayload, keyBytes, iv);

            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(decryptedPayload);
            fos.close();
        }
        is.close();

        byte[] metaCompBytes = readPayloadBytes(metaOffset, metaCompLen);
        byte[] metaBytes = ZlibUtils.decompress(metaCompBytes);
        String metaJsonStr = new String(metaBytes, StandardCharsets.UTF_8);
        JSONObject metaJson = new JSONObject(metaJsonStr);

        metadata = new BookMetadata();
        metadata.id = bookId;
        metadata.title = metaJson.optString("book", fileName);
        metadata.author = metaJson.optString("author", "");
        metadata.publisher = metaJson.optString("publisher", "");
        metadata.isbn = metaJson.optString("isbn", "");
        metadata.language = metaJson.optString("language", "ar");
        metadata.chapterCount = numChapters;
        metadata.wordCount = metaJson.optInt("word_count", 0);
        metadata.paragraphCount = metaJson.optInt("paragraph_count", 0);
    }

    private byte[] readPayloadBytes(long offset, int length) throws Exception {
        byte[] bytes = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(cacheFile, "r")) {
            raf.seek(offset);
            raf.readFully(bytes);
        }
        return bytes;
    }

    public BookMetadata getMetadata() {
        return metadata;
    }

    public int getPageCount() {
        return chaptersIndices.size();
    }

    /**
     * Returns true if the given title is a meaningful TOC entry.
     * Filters out:
     *  - Empty / whitespace-only strings
     *  - Pure page-marker codes like "p11", "p1", "page5", "P11"
     *  - Strings that are purely numeric (e.g. "11", "123")
     *  - Strings shorter than 2 meaningful characters after trimming
     *  - Strings that match pattern: optional letters + digits only (anchor/id patterns)
     */
    private boolean isValidTocTitle(String title) {
        if (title == null || title.trim().isEmpty()) return false;
        String t = title.trim();
        // Too short to be meaningful
        if (t.length() < 2) return false;
        // Pure numeric
        if (t.matches("\\d+")) return false;
        // Page marker pattern: p/P/pg/page followed by digits (e.g. p11, P3, pg5, page11)
        if (t.matches("(?i)p(?:age|g)?\\d+")) return false;
        // Generic anchor/id pattern: 1-4 letters followed by digits only (e.g. h1, h2, s3, ch4)
        if (t.matches("[a-zA-Z]{1,4}\\d+")) return false;
        // Only symbols/punctuation, no real word characters
        if (!t.matches(".*[\\p{L}\\p{N}].*")) return false;
        return true;
    }

    /**
     * Builds a Table of Contents by scanning every chapter.
     * Reads heading level from the first heading block found.
     * Falls back to level 1 if no heading block exists.
     * Skips chapters whose title is not a meaningful heading
     * (e.g. pure page markers like "p11", numeric IDs, etc.).
     */
    public List<TocEntry> buildTableOfContents() {
        List<TocEntry> toc = new ArrayList<>();
        int total = chaptersIndices.size();
        for (int i = 0; i < total; i++) {
            try {
                JSONObject ch = getChapter(i);
                String chTitle = ch.optString("title", "").trim();
                // Skip entries with no meaningful title
                if (!isValidTocTitle(chTitle)) continue;

                int level = 1;
                String pageInfo = "";

                JSONArray blocks = ch.optJSONArray("blocks");
                if (blocks != null) {
                    for (int b = 0; b < blocks.length(); b++) {
                        JSONObject blk = blocks.optJSONObject(b);
                        if (blk == null) continue;
                        String type = blk.optString("type", "");
                        if ("heading".equals(type)) {
                            level = blk.optInt("level", 1);
                            // Normalize: level 1 is largest heading (Bab),
                            // level 2 = Fashl, level 3 = sub-Fashl, etc.
                            // MAI headings use h1=1, h2=2 … but some books start at h2,
                            // so we just store raw level and handle display in UI.
                            break;
                        }
                    }
                    // Look for physical page info in last few blocks
                    for (int b = blocks.length() - 1; b >= Math.max(0, blocks.length() - 3); b--) {
                        JSONObject blk = blocks.optJSONObject(b);
                        if (blk == null) continue;
                        String blkText = blk.optString("text", "");
                        if (blkText.contains("\u0627\u0644\u062c\u0632\u0621") ||
                                blkText.contains("\u0627\u0644\u0635\u0641\u062d\u0629")) {
                            pageInfo = blkText;
                            break;
                        }
                    }
                }

                TocEntry entry = new TocEntry();
                entry.pageIndex = i;
                entry.title = chTitle;
                entry.level = level;
                entry.pageInfo = pageInfo;
                toc.add(entry);
            } catch (Exception ignored) {
            }
        }
        return toc;
    }

    public JSONObject getChapter(int index) throws Exception {
        if (index < 0 || index >= chaptersIndices.size()) {
            throw new IndexOutOfBoundsException("Chapter index out of range");
        }
        ChapterIndex idx = chaptersIndices.get(index);
        byte[] compBytes = readPayloadBytes(idx.offset, idx.compLen);
        byte[] bytes = ZlibUtils.decompress(compBytes);
        String jsonStr = new String(bytes, StandardCharsets.UTF_8);
        return new JSONObject(jsonStr);
    }
}
