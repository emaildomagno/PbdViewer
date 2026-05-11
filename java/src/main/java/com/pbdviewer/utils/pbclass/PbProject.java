package com.pbdviewer.utils.pbclass;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PbProject {

    private final String filePath;
    private final String dir;
    public boolean isDebug;

    private final List<PbFile> files = new ArrayList<>();
    private final Map<String, PbObject> objects = new HashMap<>();
    private final Map<Integer, PbType> systemTypes = new HashMap<>();
    private final Map<Integer, PbEnum> enums = new HashMap<>();
    private PbEntry systemEntry;
    private boolean isUnicode;
    private boolean isPb5;
    private int version;

    public PbProject(String filePath) {
        this.filePath = filePath;
        this.dir = new File(filePath).getParent();
        PbFile item = new PbFile(this, filePath);
        files.add(0, item);
        for (int i = 0; i < files.size(); i++) {
            for (PbEntry entry : files.get(i).getEntries()) {
                entry.parseObject();
            }
        }
        if (systemEntry != null) systemEntry.parseInherit();
        for (int i = 0; i < files.size(); i++) {
            for (PbEntry entry : files.get(i).getEntries()) {
                entry.parseInherit();
            }
        }
    }

    public String getString(byte[] buffer, int offset, int size) {
        if (!isUnicode) return new String(buffer, offset, size, Charset.defaultCharset());
        return new String(buffer, offset, size, StandardCharsets.UTF_16LE);
    }

    public String getString(byte[] buffer) {
        if (!isUnicode) return new String(buffer, Charset.defaultCharset());
        return new String(buffer, StandardCharsets.UTF_16LE);
    }

    public void onNewLibrary(String libpath, boolean isFullPath) {
        if (!isFullPath) libpath = Paths.get(dir, libpath).toString();
        if (!filePath.equalsIgnoreCase(libpath) && new File(libpath).exists()) {
            files.add(new PbFile(this, libpath));
        }
    }

    public PbFile onSystemLibrary(int version) {
        if (this.version == 0) {
            this.version = version;
            return new PbFile(this, this.version);
        }
        if (this.version != version) throw new RuntimeException("two version library in one project??");
        return null;
    }

    public void onSystemEntry(PbEntry pbEntry) {
        this.systemEntry = pbEntry;
    }

    public void onNewSystemType(PbType pbType) {
        systemTypes.put(pbType.getIndex(), pbType);
    }

    public void onNewObject(PbObject pbObject, String name) {
        objects.put(name != null ? name : pbObject.getType().getName(), pbObject);
    }

    public void onNewObject(PbObject pbObject) {
        onNewObject(pbObject, null);
    }

    public void onNewEnumItem(PbType type, int index, String itemName) {
        if (!enums.containsKey(type.getIndex())) {
            PbEnum pbEnum = new PbEnum();
            pbEnum.setIndex(type.getIndex());
            pbEnum.setName(type.getName());
            enums.put(type.getIndex(), pbEnum);
        }
        enums.get(type.getIndex()).getItems().put(index, itemName + "!");
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public List<PbFile> getFiles() {
        return files;
    }

    public Map<String, PbObject> getObjects() {
        return objects;
    }

    public Map<Integer, PbType> getSystemTypes() {
        return systemTypes;
    }

    public Map<Integer, PbEnum> getEnums() {
        return enums;
    }

    public PbEntry getSystemEntry() {
        return systemEntry;
    }

    public void setSystemEntry(PbEntry systemEntry) {
        this.systemEntry = systemEntry;
    }

    public boolean isUnicode() {
        return isUnicode;
    }

    public void setUnicode(boolean unicode) {
        this.isUnicode = unicode;
    }

    public boolean isPb5() {
        return isPb5;
    }

    public void setPb5(boolean pb5) {
        this.isPb5 = pb5;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
