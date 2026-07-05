package com.santripesisir.roudhotuttholibin.reader;

import android.content.Context;
import com.santripesisir.roudhotuttholibin.utils.CipherUtils;
import com.santripesisir.roudhotuttholibin.utils.ZlibUtils;
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
