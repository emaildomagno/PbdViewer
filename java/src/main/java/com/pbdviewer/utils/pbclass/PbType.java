package com.pbdviewer.utils.pbclass;

import java.util.HashMap;
import java.util.Map;

public class PbType {

    private static final Map<Integer, PbType> VALUE_TYPES = new HashMap<>();

    private boolean isFinded;

    private int index;
    private String name;
    private boolean isValueType;
    private boolean isSystemType;
    private boolean isReferencedObject;
    private PbEnum pbEnum;
    private PbEntry entry;
    private PbObject object;

    public PbType(PbEntry entry, int index, String name, boolean isReferencedObject, boolean isSystemEntry) {
        this.name = name;
        this.entry = entry;
        this.isReferencedObject = isReferencedObject;
        if (isSystemEntry) {
            if (isReferencedObject) throw new RuntimeException("system entry can't reference other object");
            this.isSystemType = true;
            this.index = 0x4000 | index;
            entry.getProject().onNewSystemType(this);
            return;
        }
        if (isReferencedObject) {
            this.pbEnum = entry.getProject().getEnums().values().stream()
                    .filter(o -> o.getName().equals(name))
                    .findFirst()
                    .orElse(null);
        }
        this.index = 0x8000 | index;
        entry.onNewType(this);
    }

    private PbType(int index) {
        this.index = index;
        this.name = getValueTypeName(index);
        this.isValueType = true;
    }

    public static PbType getPbType(PbEntry pbEntry, int index) {
        switch (index >> 12) {
            case 0 -> {
                if (!VALUE_TYPES.containsKey(index)) VALUE_TYPES.put(index, new PbType(index));
                return VALUE_TYPES.get(index);
            }
            case 4 -> { return pbEntry.getProject().getSystemTypes().get(index); }
            case 8 -> { return pbEntry.getTypes().get(index); }
            case 12 -> { return new PbType(0); }
            default -> throw new RuntimeException(String.format("Unknown Type %04X", index));
        }
    }

    private String getValueTypeName(int low) {
        return switch (low) {
            case 0  -> "";
            case 1  -> "integer";
            case 2  -> "long";
            case 3  -> "real";
            case 4  -> "double";
            case 5  -> "decimal";
            case 6  -> "string";
            case 7  -> "boolean";
            case 8  -> "any";
            case 9  -> "uint";
            case 10 -> "ulong";
            case 11 -> "blob";
            case 12 -> "date";
            case 13 -> "time";
            case 14 -> "datetime";
            case 15 -> "cursor";
            case 16 -> "procedure";
            case 18 -> "char";
            case 19 -> "objhandle";
            case 20 -> "longlong";
            case 21 -> "byte";
            default -> String.format("%04X", low);
        };
    }

    public PbObject getObject(PbEntry pbEntry) {
        if (object != null) return object;
        if (isValueType) return object;
        if (isFinded) return object;
        if (name.contains("`") || isSystemType || isReferencedObject) {
            if (pbEntry.getProject().getObjects().containsKey(name)) {
                object = pbEntry.getProject().getObjects().get(name);
            }
        } else {
            pbEntry = (entry != null) ? entry : pbEntry;
            if (pbEntry.getObjects().containsKey(index)) {
                object = pbEntry.getObjects().get(index);
            }
        }
        isFinded = true;
        return object;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isValueType() {
        return isValueType;
    }

    public void setValueType(boolean valueType) {
        isValueType = valueType;
    }

    public boolean isSystemType() {
        return isSystemType;
    }

    public void setSystemType(boolean systemType) {
        isSystemType = systemType;
    }

    public boolean isReferencedObject() {
        return isReferencedObject;
    }

    public void setReferencedObject(boolean referencedObject) {
        isReferencedObject = referencedObject;
    }

    public PbEnum getPbEnum() {
        return pbEnum;
    }

    public void setPbEnum(PbEnum pbEnum) {
        this.pbEnum = pbEnum;
    }

    public PbEntry getEntry() {
        return entry;
    }

    public void setEntry(PbEntry entry) {
        this.entry = entry;
    }

    public PbObject getObject() {
        return object;
    }

    public void setObject(PbObject object) {
        this.object = object;
    }
}
