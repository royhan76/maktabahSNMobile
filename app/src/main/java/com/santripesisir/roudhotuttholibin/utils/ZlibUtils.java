package com.santripesisir.roudhotuttholibin.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

public class ZlibUtils {
    public static byte[] decompress(byte[] data) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) {
                break;
            }
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        inflater.end();
        return outputStream.toByteArray();
    }
}
