package com.pbdviewer.utils.pbclass;

import com.pbdviewer.utils.BufferHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PbVariable {

    private final byte[] buffer;
    private String sqlDeclare;

    private int index;
    private int flagValue;
    private String precisionOrSize = "";
    private String name;
    private String arrayString;
    private String accessString;
    private boolean isReferencedGlobal;
    private boolean isShared;
    private boolean isInstance;
    private boolean isIndirect;
    private boolean isConstant;
    private PbType type;
    private PbEntry entry;
    private PbObject object;

    public PbVariable(PbEntry pbEntry, int index, byte[] buffer, byte[] structBuffer, boolean delayParseType) {
        this.entry = pbEntry;
        this.buffer = buffer;
        this.index = index;
        this.flagValue = buffer[17] & 0xFF;
        this.accessString = PbVariableFlag.hasFlag(flagValue, PbVariableFlag.IsPrivate) ? "private "
                : PbVariableFlag.hasFlag(flagValue, PbVariableFlag.IsProtected) ? "protected " : "";
        this.isShared = PbVariableFlag.hasFlag(flagValue, PbVariableFlag.IsShared);
        this.isReferencedGlobal = (buffer[16] & 0x40) == 64;
        this.isInstance = (buffer[0] & 0xF) <= 1;
        this.isIndirect = (buffer[0] & 2) == 2;
        this.isConstant = (buffer[0] & 4) == 4;
        if (!delayParseType) parseType();
        this.name = BufferHelper.getString(pbEntry.getProject().isUnicode(), structBuffer,
                (int) BufferHelper.getUInt(buffer, 8L));
        this.arrayString = getArrayString(BufferHelper.getUInt(buffer, 4L), structBuffer);
    }

    // Private constructor used by inherit()
    private PbVariable(PbVariable source) {
        this.buffer = source.buffer;
        this.sqlDeclare = source.sqlDeclare;
        this.index = source.index;
        this.flagValue = source.flagValue;
        this.precisionOrSize = source.precisionOrSize;
        this.name = source.name;
        this.arrayString = source.arrayString;
        this.accessString = source.accessString;
        this.isReferencedGlobal = source.isReferencedGlobal;
        this.isShared = source.isShared;
        this.isInstance = source.isInstance;
        this.isIndirect = source.isIndirect;
        this.isConstant = source.isConstant;
        this.type = source.type;
        this.entry = source.entry;
        this.object = source.object;
    }

    public PbVariable inherit(PbObject control) {
        PbVariable obj = new PbVariable(this);
        obj.type = control.getType();
        obj.entry = control.getEntry();
        obj.object = control;
        return obj;
    }

    public void parseType() {
        type = PbType.getPbType(entry, BufferHelper.getUShort(buffer, 18L));
        if (type.isValueType()) {
            if ("blob".equals(type.getName())) {
                int uShort = BufferHelper.getUShort(buffer, 12L);
                precisionOrSize = (uShort == 0) ? "" : String.format("{%d}", BufferHelper.getUShort(buffer, 12L));
            } else if ("decimal".equals(type.getName())) {
                int num = buffer[16] & 0x3F;
                precisionOrSize = (num == 62) ? "" : String.format("{%d}", (buffer[16] & 0xFF) / 2);
            }
        }
    }

    public static String getArrayString(long offset, byte[] buffer) {
        String text = "";
        if (offset != 65535L) {
            text += "[";
            int b = buffer[(int) offset] & 0xFF;
            for (int i = 0; i < b; i++) {
                if (i != 0) text += ",";
                long uInt = BufferHelper.getUInt(buffer, offset + 4 + i * 8);
                long uInt2 = BufferHelper.getUInt(buffer, offset + 8 + i * 8);
                if (uInt == 1) {
                    text += uInt2;
                } else if (uInt != uInt2 || uInt2 != 0) {
                    text = text + uInt + " to " + uInt2;
                }
            }
            text += "]";
        }
        return text;
    }

    public String getValue(byte[] valueBuffer) {
        if (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.IsCustom)) return null;
        if (!type.isValueType() && type.getPbEnum() == null) return null;
        if (isIndirect || isReferencedGlobal) return null;
        if (PbVariableFlag.hasFlag(flagValue, PbVariableFlag.IsArray)) {
            if (PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)) return null;
            List<Long> list = getList(valueBuffer);
            List<String> list2 = list.stream()
                    .map(o -> getValue(o, valueBuffer, true))
                    .collect(Collectors.toCollection(ArrayList::new));
            while (!list2.isEmpty() && list2.get(list2.size() - 1) == null) {
                list2.remove(list2.size() - 1);
            }
            for (int i = 0; i < list2.size(); i++) {
                if (list2.get(i) == null) list2.set(i, getValue(list.get(i), valueBuffer, false));
            }
            return String.format("{%s}", String.join(",", list2));
        }
        return getValue(BufferHelper.getUInt(buffer, 12L), valueBuffer, true);
    }

    private String getValue(long code, byte[] valueBuffer, boolean checkIsDefault) {
        if (type.getPbEnum() != null) {
            if (!checkIsDefault || (int) (code & 0xFFFF) != 0) return type.getPbEnum().getItems().get((int) (code & 0xFFFF));
            return null;
        }
        switch (type.getName()) {
            case "integer":
                if (!checkIsDefault || (short) (code & 0xFFFF) != 0) return String.format("%d", (short) (code & 0xFFFF));
                return null;
            case "uint":
                if (!checkIsDefault || (int) (code & 0xFFFF) != 0) return String.format("%d", (int) (code & 0xFFFF));
                return null;
            case "long":
                if (!checkIsDefault || code != 0) return String.format("%d", (int) (code & 0xFFFFFFFFL));
                return null;
            case "ulong":
                if (!checkIsDefault || code != 0) return String.format("%d", code);
                return null;
            case "char":
                if (!checkIsDefault || (int) (code & 0xFFFF) != 0) return String.format("'%c'", (char) (code & 0xFFFF));
                return null;
            case "byte":
                if (!checkIsDefault || (int) (code & 0xFFFF) != 0) return String.format("%d", (byte) (code & 0xFF));
                return null;
            case "boolean":
                if (!checkIsDefault || (byte) (code & 0xFF) != 0)
                    return String.valueOf((byte) (code & 0xFF) != 0).toLowerCase();
                return null;
            case "real":
                if (!checkIsDefault || code != 0) return BufferHelper.getReal(code);
                return null;
            case "string":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getString(entry.getProject().isUnicode(), valueBuffer, code).equals("")))
                    return String.format("%s", BufferHelper.getEscapeString(entry.getProject().isUnicode(), valueBuffer, code));
                return null;
            case "decimal":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getDecimal(valueBuffer, code).equals("0.0")))
                    return String.format("%s", BufferHelper.getDecimal(valueBuffer, code));
                return null;
            case "double":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getDouble(valueBuffer, code).equals("0")))
                    return BufferHelper.getDouble(valueBuffer, code);
                return null;
            case "longlong":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getLongLong(valueBuffer, code).equals("0")))
                    return BufferHelper.getLongLong(valueBuffer, code);
                return null;
            case "date":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getDate(valueBuffer, code).equals("1900-01-01")))
                    return String.format("%s", BufferHelper.getDate(valueBuffer, code));
                return null;
            case "time":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getTime(valueBuffer, code).equals("00:00:00")))
                    return String.format("%s", BufferHelper.getTime(valueBuffer, code));
                return null;
            case "datetime":
                if (!checkIsDefault || (!PbVariableFlag.hasFlag(flagValue, PbVariableFlag.Invalid)
                        && !BufferHelper.getDateTime(valueBuffer, code).equals("datetime(1900-01-01,00:00:00)")))
                    return String.format("%s", BufferHelper.getDateTime(valueBuffer, code));
                return null;
            default:
                return null;
        }
    }

    private List<Long> getList(byte[] valueBuffer) {
        List<Long> list = new ArrayList<>();
        int uShort = BufferHelper.getUShort(buffer, 12L);
        int uShort2 = BufferHelper.getUShort(valueBuffer, uShort + 14);
        long num = uShort + 28 + uShort2 * 8L;
        long uInt = BufferHelper.getUInt(valueBuffer, num);
        for (int i = 0; i < uInt; i++) {
            list.add(BufferHelper.getUInt(valueBuffer, num + 4 + 8L * i));
        }
        return list;
    }

    public void setCursorParams(Iterable<String> paramList, String sqlcaStr) {
        if (sqlDeclare == null) {
            sqlDeclare = BufferHelper.getCursor(entry.getProject().isUnicode(), entry.getVariableBuffer(),
                    BufferHelper.getUInt(buffer, 12L), paramList);
            sqlDeclare = String.format("declare %s cursor for %s using %s ;", name, sqlDeclare, sqlcaStr);
        }
    }

    public void setDynamicCursorParams(String sqlsaStr) {
        if (sqlDeclare == null) {
            sqlDeclare = BufferHelper.getCursor(entry.getProject().isUnicode(), entry.getVariableBuffer(),
                    BufferHelper.getUInt(buffer, 12L), null);
            sqlDeclare = String.format("declare %s dynamic cursor %s for %s ;", name, sqlDeclare, sqlsaStr);
        }
    }

    public void setProcedureParams(Iterable<String> paramList, String sqlcaStr) {
        if (sqlDeclare == null) {
            sqlDeclare = BufferHelper.getCursor(entry.getProject().isUnicode(), entry.getVariableBuffer(),
                    BufferHelper.getUInt(buffer, 12L), paramList).replace("execute ", "");
            sqlDeclare = String.format("declare %s procedure for %s using %s ;", name, sqlDeclare, sqlcaStr);
        }
    }

    public void setDynamicProcedureParams(String sqlsaStr) {
        if (sqlDeclare == null) {
            sqlDeclare = BufferHelper.getCursor(entry.getProject().isUnicode(), entry.getVariableBuffer(),
                    BufferHelper.getUInt(buffer, 12L), null);
            sqlDeclare = String.format("declare %s dynamic procedure %s for %s ;", name, sqlDeclare, sqlsaStr);
        }
    }

    public String toDisplayString(byte[] valueBuffer, boolean debug) {
        String text = String.format("%s%s%s %s%s", accessString, type.getName(), precisionOrSize, name, arrayString);
        if (sqlDeclare != null) text = sqlDeclare;
        if (isConstant) text = "constant " + text;
        if (debug) {
            if (isReferencedGlobal) text = "global " + text;
            else if (isShared) text = "shared " + text;
            text = BufferHelper.getHexString(buffer) + "  " + text;
        }
        if (valueBuffer != null) {
            String text2 = null;
            if (!isReferencedGlobal) text2 = getValue(valueBuffer);
            if (text2 != null) text += String.format(" = %s", text2);
        }
        return text;
    }

    @Override
    public String toString() {
        return toDisplayString(null, false);
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public byte[] getBuffer() {
        return buffer;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getGlobalIndex() {
        if (!isShared) return 0xFFFF;
        return BufferHelper.getUShort(buffer, 12L);
    }

    public PbType getType() {
        return type;
    }

    public void setType(PbType type) {
        this.type = type;
    }

    public int getFlagValue() {
        return flagValue;
    }

    public void setFlagValue(int flagValue) {
        this.flagValue = flagValue;
    }

    public String getPrecisionOrSize() {
        return precisionOrSize;
    }

    public void setPrecisionOrSize(String precisionOrSize) {
        this.precisionOrSize = precisionOrSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArrayString() {
        return arrayString;
    }

    public void setArrayString(String arrayString) {
        this.arrayString = arrayString;
    }

    public String getAccessString() {
        return accessString;
    }

    public void setAccessString(String accessString) {
        this.accessString = accessString;
    }

    public boolean isReferencedGlobal() {
        return isReferencedGlobal;
    }

    public void setReferencedGlobal(boolean referencedGlobal) {
        this.isReferencedGlobal = referencedGlobal;
    }

    public boolean isShared() {
        return isShared;
    }

    public void setShared(boolean shared) {
        this.isShared = shared;
    }

    public boolean isInstance() {
        return isInstance;
    }

    public void setInstance(boolean instance) {
        this.isInstance = instance;
    }

    public boolean isIndirect() {
        return isIndirect;
    }

    public void setIndirect(boolean indirect) {
        this.isIndirect = indirect;
    }

    public boolean isConstant() {
        return isConstant;
    }

    public void setConstant(boolean constant) {
        this.isConstant = constant;
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
