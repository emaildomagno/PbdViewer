package com.pbdviewer.utils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BufferHelper {

    public static byte[] getBuffer(byte[] buffer, long offset, int size) {
        offset &= 0x7FFFFFFF;
        size = Math.min(size, buffer.length - (int) offset);
        byte[] array = new byte[size];
        System.arraycopy(buffer, (int) offset, array, 0, size);
        return array;
    }

    public static String getHexString(byte[] buffer) {
        List<String> parts = new ArrayList<>(buffer.length);
        for (byte b : buffer) {
            parts.add(String.format("%02X", b & 0xFF));
        }
        return String.join(" ", parts);
    }

    public static String getHexString(byte[] buffer, long offset, int size) {
        return getHexString(getBuffer(buffer, offset, size));
    }

    public static int getUShort(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        return ((buffer[(int) offset + 1] & 0xFF) << 8) | (buffer[(int) offset] & 0xFF);
    }

    public static long getUInt(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        return ((long) (buffer[(int) offset + 3] & 0xFF) << 24)
             | ((long) (buffer[(int) offset + 2] & 0xFF) << 16)
             | ((long) (buffer[(int) offset + 1] & 0xFF) << 8)
             |  (long) (buffer[(int) offset]     & 0xFF);
    }

    public static String getDate(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        int uShort = getUShort(buffer, offset + 4);
        return String.format("%d-%02d-%02d",
                uShort + 1900,
                (buffer[(int) offset + 6] & 0xFF) + 1,
                buffer[(int) offset + 7] & 0xFF);
    }

    public static String getDateTime(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        return String.format("datetime(%s,%s)", getDate(buffer, offset), getTime(buffer, offset));
    }

    public static String getTime(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        String text = String.format("%02d:%02d:%02d",
                buffer[(int) offset + 8]  & 0xFF,
                buffer[(int) offset + 9]  & 0xFF,
                buffer[(int) offset + 10] & 0xFF);
        long num = getUInt(buffer, offset) / 1000L;
        if (num != 0) {
            text += String.format(".%03d", num);
        }
        return text;
    }

    public static String getEscapeString(boolean isUnicode, byte[] buffer, long offset) {
        String arg = getString(isUnicode, buffer, offset)
                .replace("~",  "~~")
                .replace("\r", "~r")
                .replace("\n", "~n")
                .replace("\t", "~t")
                .replace("\"", "~\"");
        return String.format("\"%s\"", arg);
    }

    public static String getString(boolean isUnicode, byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        long num = offset;
        if (isUnicode) {
            while (num < buffer.length && (buffer[(int) num] != 0 || buffer[(int) num + 1] != 0)) {
                num += 2;
            }
        } else {
            while (num < buffer.length && buffer[(int) num] != 0) {
                num++;
            }
        }
        if (num - offset == 0L) {
            return "";
        }
        if (!isUnicode) {
            return new String(buffer, (int) offset, (int) (num - offset), Charset.defaultCharset());
        }
        return new String(buffer, (int) offset, (int) (num - offset), StandardCharsets.UTF_16LE);
    }

    public static String getDecimal(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        int uShort = getUShort(buffer, offset);
        int b = buffer[(int) offset + 2] & 0xFF;

        BigInteger value = BigInteger.valueOf(getUInt(buffer, offset + 4))
                .add(BigInteger.valueOf(getUInt(buffer, offset + 8)).shiftLeft(32))
                .add(BigInteger.valueOf(getUShort(buffer, offset + 12)).shiftLeft(64));

        String text = value.toString();

        if (b > 0) {
            if (text.length() <= b) {
                text = padLeft(text, b + 1, '0');
            }
            text = text.substring(0, text.length() - b) + "." + text.substring(text.length() - b);
            text = trimTrailingZeros(text);
            if (text.endsWith(".")) {
                text += "0";
            }
        }

        if (uShort > 0) {
            text = "-" + text;
        }
        return text;
    }

    public static String getReal(long code) {
        byte[] bytes = new byte[] {
            (byte)  (code        & 0xFF),
            (byte) ((code >>  8) & 0xFF),
            (byte) ((code >> 16) & 0xFF),
            (byte) ((code >> 24) & 0xFF)
        };
        float f = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        return String.valueOf(f);
    }

    public static String getDouble(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        double d = ByteBuffer.wrap(buffer, (int) offset, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
        return String.valueOf(d);
    }

    public static String getLongLong(byte[] buffer, long offset) {
        offset &= 0x7FFFFFFF;
        long l = ByteBuffer.wrap(buffer, (int) offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        return String.valueOf(l);
    }

    public static String getCursor(boolean isUnicode, byte[] bytes, long offset, Iterable<String> paramList) {
        long num = offset & 0x7FFFFFFFL;
        if (getUInt(bytes, num + 8) != 65535L) {
            return getCursor(isUnicode, bytes, getUInt(bytes, num + 8), paramList);
        }
        String str = getString(isUnicode, bytes, getUInt(bytes, num + 24));
        String text = "";
        if (paramList != null) {
            long num2 = getUInt(bytes, num + 16);
            int num3 = 0;
            for (String param : paramList) {
                int uShort  = getUShort(bytes, num2);
                int uShort2 = getUShort(bytes, num2 + 2);
                num2 += 4;
                if (uShort == 0 && uShort2 == 0) {
                    break;
                }
                text = text + str.substring(num3, uShort) + String.format(":%s", param);
                num3 = uShort2;
            }
            text += str.substring(num3);
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String trimTrailingZeros(String s) {
        int i = s.length();
        while (i > 0 && s.charAt(i - 1) == '0') {
            i--;
        }
        return s.substring(0, i);
    }

    private static String padLeft(String s, int width, char pad) {
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            sb.append(pad);
        }
        sb.append(s);
        return sb.toString();
    }
}
