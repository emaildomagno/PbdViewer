package com.pbdviewer.utils.pbclass;

import com.pbdviewer.utils.BufferHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class PbFile {

    private final PbProject project;
    private final String filePath;
    private final String fileName;
    private final List<PbEntry> entries = new ArrayList<>();

    // Constructor for real file
    public PbFile(PbProject project, String filePath) {
        this.project = project;
        this.filePath = filePath;
        String[] parts = filePath.replace('\\', '/').split("/");
        this.fileName = parts[parts.length - 1];

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            for (long node : getNodeList(raf)) {
                byte[] buffer = new byte[32];
                raf.seek(node);
                raf.read(buffer, 0, 32);
                long current = raf.getFilePointer();
                int uShort = BufferHelper.getUShort(buffer, 20L);
                int num = (!project.isUnicode()) ? 1 : 2;
                int num2 = 4 + num * 4;
                int num3 = num2 + 16;
                byte[] array = new byte[num3];
                for (int i = 0; i < uShort; i++) {
                    raf.seek(current);
                    raf.read(array, 0, num3);
                    if (!new String(array, 0, 4, StandardCharsets.US_ASCII).equals("ENT*") ||
                        (!project.getString(array, 4, num * 4).equals("0600") &&
                         !project.getString(array, 4, num * 4).equals("0500"))) {
                        throw new RuntimeException("格式错误");
                    }
                    long uInt = BufferHelper.getUInt(array, num2);
                    long uInt2 = BufferHelper.getUInt(array, num2 + 4);
                    int uShort2 = BufferHelper.getUShort(array, num2 + 14);
                    byte[] buffer2 = new byte[uShort2];
                    raf.read(buffer2, 0, uShort2);
                    current = raf.getFilePointer();
                    String entryName = project.getString(buffer2, 0, uShort2 - num);
                    entries.add(new PbEntry(this, entryName, readData(raf, uInt, uInt2)));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read PBD file: " + filePath, e);
        }
    }

    // Constructor for system library (from embedded resource)
    public PbFile(PbProject project, int version) {
        this.project = project;
        this.filePath = null;
        this.fileName = "system";

        String resourcePath = String.format("/resoures/%04x.bin", version);
        InputStream stream = PbFile.class.getResourceAsStream(resourcePath);
        if (stream != null) {
            try {
                byte[] array = new byte[4096];
                GZIPInputStream gzipStream = new GZIPInputStream(stream);
                ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
                int num;
                do {
                    num = gzipStream.read(array, 0, array.length);
                    if (num > 0) memoryStream.write(array, 0, num);
                } while (num > 0);
                gzipStream.close();
                stream.close();
                byte[] entryData = memoryStream.toByteArray();
                memoryStream.close();
                entries.add(new PbEntry(this, "_typedef.grp", entryData));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read system library resource", e);
            }
        }
    }

    private static byte[] readData(RandomAccessFile raf, long start, long size) throws IOException {
        byte[] array = new byte[(int) size];
        int num = 0;
        long offset = start;
        byte[] array2 = new byte[10];
        while (num < size) {
            raf.seek(offset);
            raf.read(array2, 0, 10);
            if (!new String(array2, 0, 4, StandardCharsets.US_ASCII).equals("DAT*")) break;
            int uShort = BufferHelper.getUShort(array2, 8L);
            raf.read(array, num, uShort);
            num += uShort;
            offset = BufferHelper.getUInt(array2, 4L);
        }
        return array;
    }

    private List<Long> getNodeList(RandomAccessFile raf) throws IOException {
        List<Long> list = new ArrayList<>();
        byte[] array = new byte[512];
        long num = 0L;
        boolean flag = false;
        raf.seek(0L);
        int num2 = raf.read(array, 0, 512);
        while (num2 > 0) {
            if (new String(array, 0, 4, StandardCharsets.US_ASCII).equals("HDR*")) {
                if (new String(array, 4, 12, StandardCharsets.US_ASCII).equals("PowerBuilder")) {
                    if (new String(array, 18, 4, StandardCharsets.US_ASCII).equals("0500")) {
                        project.setPb5(true);
                        flag = true;
                        break;
                    }
                    if (new String(array, 18, 4, StandardCharsets.US_ASCII).equals("0600")) {
                        flag = true;
                        break;
                    }
                }
                if (new String(array, 4, 24, StandardCharsets.UTF_16LE).equals("PowerBuilder") &&
                    new String(array, 32, 8, StandardCharsets.UTF_16LE).equals("0600")) {
                    flag = true;
                    project.setUnicode(true);
                    break;
                }
            }
            num += num2;
            num2 = raf.read(array, 0, 512);
        }
        if (flag) {
            num += (project.isUnicode() ? 1536 : 1024);
            raf.seek(num);
            num2 = raf.read(array, 0, 512);
            if (num2 != 512 || !new String(array, 0, 4, StandardCharsets.US_ASCII).equals("NOD*")) {
                throw new RuntimeException("格式错误");
            }
            list.add(num);
            long uInt = BufferHelper.getUInt(array, 4L);
            long uInt2 = BufferHelper.getUInt(array, 12L);
            while (uInt != 0) {
                raf.seek(uInt);
                num2 = raf.read(array, 0, 512);
                if (num2 != 512 || !new String(array, 0, 4, StandardCharsets.US_ASCII).equals("NOD*")) {
                    throw new RuntimeException("格式错误");
                }
                list.add(uInt);
                uInt = BufferHelper.getUInt(array, 4L);
            }
            while (uInt2 != 0) {
                raf.seek(uInt2);
                num2 = raf.read(array, 0, 512);
                if (num2 != 512 || !new String(array, 0, 4, StandardCharsets.US_ASCII).equals("NOD*")) {
                    throw new RuntimeException("格式错误");
                }
                list.add(uInt2);
                uInt2 = BufferHelper.getUInt(array, 12L);
            }
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public PbProject getProject() {
        return project;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public List<PbEntry> getEntries() {
        return entries;
    }
}
