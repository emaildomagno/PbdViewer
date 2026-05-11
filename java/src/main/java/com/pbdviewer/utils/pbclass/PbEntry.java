package com.pbdviewer.utils.pbclass;

import com.pbdviewer.utils.BufferHelper;
import com.pbdviewer.utils.PCodeHelper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PbEntry {

    // -------------------------------------------------------------------------
    // Inner class: IndentBlock (try-with-resources equivalent of C# using block)
    // -------------------------------------------------------------------------

    private class IndentBlock implements AutoCloseable {
        public IndentBlock(String blockName) {
            printString(blockName + ": {");
            indent++;
        }

        @Override
        public void close() {
            indent--;
            printString("}");
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final byte[] entryData;
    private boolean isParsed;
    private int flag;          // ushort in C#
    private int indent;
    private byte[] dataBuffer;
    private long position;
    private final StringBuilder sb = new StringBuilder();
    private boolean isDebug = true;

    private final Map<Integer, PbType> types = new HashMap<>();
    private final Map<Integer, PbObject> objects = new HashMap<>();
    private final PbProject project;
    private final PbFile file;
    private final String entryName;
    private final String name;
    private final String suffix;

    private byte[] variableBuffer;
    private byte[] functionBuffer;
    private byte[] paramBuffer;

    private PbVariable[] variables;
    private PbObject entryObject;
    private String source;
    private Date modifiedTime;
    private Date compiledTime;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PbEntry(PbFile file, String entryName, byte[] entryData) {
        this.entryData = entryData;
        this.file = file;
        this.entryName = entryName;
        this.project = file.getProject();

        int dotIdx = entryName.lastIndexOf('.');
        this.name = (dotIdx >= 0) ? entryName.substring(0, dotIdx) : entryName;
        this.suffix = (dotIdx >= 0) ? entryName.substring(dotIdx + 1) : "";

        switch (suffix) {
            case "ico":
            case "jpg":
            case "png":
            case "bmp":
                // No BitmapImage support in Java — just mark as parsed
                isParsed = true;
                break;
            case "exe":
                parseExe();
                isParsed = true;
                break;
            case "srj":
                parseSrj();
                isParsed = true;
                break;
            case "grp":
                project.onSystemEntry(this);
                parseObject(true);
                isParsed = true;
                break;
            case "apl":
            case "str":
            case "fun":
            case "win":
            case "men":
            case "udo":
                project.onSystemLibrary(BufferHelper.getUShort(entryData, 0L));
                break;
            case "dwo":
                source = "DataWindow可以通过PB接口函数导出";
                isParsed = true;
                break;
            default:
                source = project.getString(entryData);
                isParsed = true;
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Public parse methods
    // -------------------------------------------------------------------------

    public void parseObject() {
        parseObject(false);
    }

    public void parseObject(boolean isSystem) {
        if (isParsed) {
            return;
        }
        sb.setLength(0);
        dataBuffer = entryData;
        position = 0L;

        printString(String.format("Pdb Version: %X", readUShort()));
        flag = readUShort();
        printString(String.format("Flag: %X", flag));
        long num = readUInt();
        printString(String.format("EntryType: %X", num));
        long num2 = readUInt();
        printString(String.format("Unkown: %X", num2));

        modifiedTime = getTime(readUInt());
        if (project.getVersion() >= 334) {
            readUInt();
        }
        printString(String.format("Last Modify Time: %s", modifiedTime));

        compiledTime = getTime(readUInt());
        if (project.getVersion() >= 334) {
            readUInt();
        }
        printString(String.format("Last Complied Time: %s", compiledTime));

        long num3 = readUInt();
        printString(String.format("Unkown: %X", num3));

        int num4 = readUShort();
        byte[][] array = new byte[num4][];
        for (int i = 0; i < num4; i++) {
            array[i] = readBuffer(12);
        }

        variableBuffer = readStructBuffer();
        variables = readVariables(true);

        int num5 = readUShort();
        int num6 = readUShort();

        functionBuffer = readStructBuffer();
        paramBuffer = readStructBuffer();

        try (IndentBlock ib = new IndentBlock("Types")) {
            readTypes(isSystem);
        }

        for (PbVariable variable : variables) {
            variable.parseType();
        }

        try (IndentBlock ib = new IndentBlock("Globel and Shared Variables")) {
            for (int k = 0; k < variables.length; k++) {
                printString(String.format("%02X:  ", k) + variables[k].toDisplayString(variableBuffer, isDebug));
            }
        }

        PbVariable[] array2 = readVariables();
        try (IndentBlock ib = new IndentBlock("Enums")) {
            for (int l = 0; l < array2.length; l++) {
                printString(String.format("%02X:  ", l) + array2[l].toDisplayString(null, isDebug));
                project.onNewEnumItem(array2[l].getType(),
                        BufferHelper.getUShort(array2[l].getBuffer(), 12L),
                        array2[l].getName());
            }
        }

        int size = project.isPb5() ? 8 : 16;
        byte[][] array3 = new byte[num5][];
        for (int m = 0; m < num5; m++) {
            array3[m] = readBuffer(size);
        }
        byte[][] array4 = new byte[num6][];
        for (int n = 0; n < num6; n++) {
            array4[n] = readBuffer(32);
        }

        int num7 = 0;
        try (IndentBlock ib = new IndentBlock(String.format("Objects: %d", num5))) {
            for (int num8 = 0; num8 < num5; num8++) {
                byte[] array5 = array3[num8];
                PbType pbType = PbType.getPbType(this, BufferHelper.getUShort(array5, 2L));
                PbObject pbObject = new PbObject(this, num8, pbType);
                int num9 = (array5[0] >> 1) & 7;

                if (num9 == 0) {
                    byte[] array6 = array4[num7++];
                    pbObject.setInheritType(PbType.getPbType(this, BufferHelper.getUShort(array6, 0L)));
                    pbObject.setParentType(PbType.getPbType(this, BufferHelper.getUShort(array6, 2L)));
                    objects.put(pbObject.getType().getIndex(), pbObject);
                    if (isSystem) {
                        project.onNewObject(pbObject);
                    } else if (pbObject.getType().getName().equals(name)) {
                        entryObject = pbObject;
                        project.onNewObject(pbObject);
                    } else if (!pbObject.getType().getName().contains("`")) {
                        project.onNewObject(pbObject, name + "`" + pbObject.getType().getName());
                    }
                    try (IndentBlock ib2 = new IndentBlock(String.format("Object[%d] %s:%s",
                            num8, pbType.getName(), pbObject.getInheritType().getName()))) {
                        printBuffer(array5, 16);
                        printBuffer(array6, array6.length);
                        readObject(pbObject, array6);
                    }
                } else {
                    try (IndentBlock ib2 = new IndentBlock(String.format("Object[%d] %s", num8, pbType.getName()))) {
                        printBuffer(array5, 16);
                        switch (num9) {
                            case 1: {
                                int uShort = BufferHelper.getUShort(array5, 4L);
                                byte[][] array7 = new byte[uShort][];
                                for (int num10 = 0; num10 < uShort; num10++) {
                                    array7[num10] = readBuffer(8);
                                    printBuffer(array7[num10], 16);
                                }
                                break;
                            }
                            case 6:
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }

        if (project.isDebug) {
            source = sb.toString();
        }
        sb.setLength(0);
        if (position != dataBuffer.length) {
            throw new RuntimeException("读取错误");
        }
        isParsed = true;
    }

    public void parseInherit() {
        for (PbObject value : objects.values()) {
            value.parseInherit();
        }
    }

    public void onNewType(PbType pbType) {
        types.put(pbType.getIndex(), pbType);
    }

    // -------------------------------------------------------------------------
    // Private read helpers
    // -------------------------------------------------------------------------

    private void readObject(PbObject pbObject, byte[] buffer) {
        int num = readUShort();
        pbObject.setFunctions(new PbFunction[num]);

        try (IndentBlock ib = new IndentBlock(String.format("Functions: %d", num))) {
            byte[][] array = new byte[num][];
            for (int i = 0; i < num; i++) {
                array[i] = readBuffer(4);
            }
            for (int j = 0; j < num; j++) {
                pbObject.getFunctions()[j] = new PbFunction(pbObject);
                readFunction(pbObject.getFunctions()[j], array[j]);
            }
        }

        int numA = BufferHelper.getUShort(buffer, 24L);
        readBuffer(6 * numA);
        int numB = BufferHelper.getUShort(buffer, 22L);
        readBuffer(4 * numB);

        pbObject.setReferencedFunctions(readReferencedFunctions());
        try (IndentBlock ib = new IndentBlock(String.format("Referenced Functions And Events: %d",
                pbObject.getReferencedFunctions().length))) {
            for (int k = 0; k < pbObject.getReferencedFunctions().length; k++) {
                printString(String.format("%02X:  ", k) + pbObject.getReferencedFunctions()[k].toString(isDebug));
            }
        }

        pbObject.setVariables(readVariables());
        try (IndentBlock ib = new IndentBlock(String.format("Properties Or Controls: %d",
                pbObject.getVariables().length))) {
            for (int l = 0; l < pbObject.getVariables().length; l++) {
                pbObject.getVariables()[l].setObject(pbObject);
                printString(String.format("%02X:  ", l) + pbObject.getVariables()[l].toDisplayString(variableBuffer, isDebug));
            }
        }

        int uShort = BufferHelper.getUShort(buffer, 28L);
        readBuffer(8 * uShort);
        pbObject.setAllVariables(new PbVariable[uShort]);

        int num2 = project.isPb5() ? 12 : 16;
        int numC = BufferHelper.getUShort(buffer, 26L);
        byte[] buff = readBuffer(num2 * numC);
        printBuffer(buff, num2);

        int numD = BufferHelper.getUShort(buffer, 4L);
        int num3 = (project.getVersion() > 146) ? 48 : (project.isPb5() ? 32 : 44);
        pbObject.setFunctionDefinitions(new PbFunctionDefinition[numD]);
        pbObject.setAllFunctionDefinitions(new PbFunctionDefinition[BufferHelper.getUShort(buffer, 16L)]);

        try (IndentBlock ib = new IndentBlock("Events And Functions")) {
            for (int num4 = 0; num4 < numD; num4++) {
                byte[] array2 = readBuffer(num3);
                printBuffer(array2, num3);

                PbFunctionDefinition pbFunctionDefinition = new PbFunctionDefinition();
                pbObject.getFunctionDefinitions()[num4] = pbFunctionDefinition;
                pbFunctionDefinition.setObject(pbObject);
                pbFunctionDefinition.setIndex(num4);

                int flagByte = project.isPb5() ? 27 : 31;
                pbFunctionDefinition.setFlag(array2[flagByte] & 0xFF);

                int retTypeOffset = project.isPb5() ? 24 : 28;
                pbFunctionDefinition.setReturnType(PbType.getPbType(this, BufferHelper.getUShort(array2, retTypeOffset)));

                String defName = BufferHelper.getString(project.isUnicode(), functionBuffer,
                        BufferHelper.getUInt(array2, 0L));
                if (defName.startsWith("+")) {
                    defName = defName.substring(1);
                }
                pbFunctionDefinition.setName(defName);

                int globalIdxOffset = project.isPb5() ? 16 : 20;
                pbFunctionDefinition.setGlobalIndex(BufferHelper.getUShort(array2, globalIdxOffset));

                int refIdxOffset = project.isPb5() ? 18 : 22;
                pbFunctionDefinition.setRefIndex(BufferHelper.getUShort(array2, refIdxOffset));

                int eventCodeOffset = project.isPb5() ? 28 : 32;
                pbFunctionDefinition.setEventCode(BufferHelper.getUShort(array2, eventCodeOffset));

                pbFunctionDefinition.setParams(new PbFunctionParam[0]);

                long uInt = BufferHelper.getUInt(array2, project.isPb5() ? 4 : 8);
                if (uInt != 65535L) {
                    int paramCountByte = project.isPb5() ? 26 : 30;
                    int b = array2[paramCountByte] & 0xFF;
                    pbFunctionDefinition.setParams(new PbFunctionParam[b]);
                    for (int m = 0; m < b; m++) {
                        PbFunctionParam pbFunctionParam = new PbFunctionParam();
                        pbFunctionDefinition.getParams()[m] = pbFunctionParam;
                        byte[] buffer2 = BufferHelper.getBuffer(paramBuffer, (int) uInt + m * 12, 12);
                        if ((buffer2[10] & 4) == 4) {
                            pbFunctionParam.setReadOnly(true);
                        } else if ((buffer2[10] & 2) == 2) {
                            pbFunctionParam.setReference(true);
                        }
                        pbFunctionParam.setType(PbType.getPbType(this, BufferHelper.getUShort(buffer2, 8L)));
                        pbFunctionParam.setName(BufferHelper.getString(project.isUnicode(), functionBuffer,
                                (int) BufferHelper.getUInt(buffer2, 0L)));
                        pbFunctionParam.setArrayString(PbVariable.getArrayString(
                                BufferHelper.getUInt(buffer2, 4L), functionBuffer));
                    }
                }

                long uInt2 = BufferHelper.getUInt(array2, project.isPb5() ? 8 : 12);
                if (uInt2 != 65535L) {
                    long uInt3 = BufferHelper.getUInt(array2, project.isPb5() ? 12 : 16);
                    pbFunctionDefinition.setLibrary(BufferHelper.getString(project.isUnicode(), functionBuffer, (int) uInt3));
                    pbFunctionDefinition.setAlias(BufferHelper.getString(project.isUnicode(), functionBuffer, (int) uInt2));
                }

                if (project.getVersion() > 146) {
                    int uShort2 = BufferHelper.getUShort(array2, 44L);
                    if (uShort2 != 0xFFFF) {
                        pbFunctionDefinition.setThrowsType(PbType.getPbType(this,
                                BufferHelper.getUShort(functionBuffer, uShort2)));
                    }
                }

                printString(pbFunctionDefinition.toString());
            }
        }
    }

    private void readFunction(PbFunction pbFunction, byte[] index) {
        String indexHex = IntStream.range(0, index.length)
                .mapToObj(i -> String.format("%04X", index[i] & 0xFF))
                .collect(Collectors.joining(" "));

        try (IndentBlock ib = new IndentBlock(String.format("Function :%s", indexHex))) {
            pbFunction.setIndex(BufferHelper.getUShort(index, 2L));
            int num = readUShort();
            int num2 = readUShort();
            printString(String.format("%04X %04X %04X", num, num2, readUShort()));
            pbFunction.setPCodeBytes(readBuffer(num));
            pbFunction.setDebugBytes(readBuffer(num2 * 4));

            try (IndentBlock ib2 = new IndentBlock("PCodes")) {
                for (String item : PCodeHelper.parsePCode(pbFunction, false)) {
                    for (String line : item.split("[\r\n]+")) {
                        if (!line.isEmpty()) printString(line);
                    }
                }
            }

            pbFunction.setVariables(readVariables());
            pbFunction.setBuffer(readStructBuffer());

            try (IndentBlock ib2 = new IndentBlock("Stack")) {
                printBuffer(pbFunction.getBuffer(), 16);
            }

            try (IndentBlock ib2 = new IndentBlock(String.format("Variable %d", pbFunction.getVariables().length))) {
                for (int i = 0; i < pbFunction.getVariables().length; i++) {
                    pbFunction.getVariables()[i].setObject(pbFunction.getObject());
                    printString(String.format("%04X: %s", i,
                            pbFunction.getVariables()[i].toDisplayString(pbFunction.getBuffer(), isDebug)));
                }
            }
        }
    }

    private int readUShort() {
        position += 2L;
        return BufferHelper.getUShort(dataBuffer, position - 2);
    }

    private long readUInt() {
        position += 4L;
        return BufferHelper.getUInt(dataBuffer, position - 4);
    }

    private byte[] readBuffer(int size) {
        position += size;
        return BufferHelper.getBuffer(dataBuffer, position - size, size);
    }

    private byte[] readStructBuffer() {
        long size = readUInt();
        long size2 = readUInt();
        byte[] result = readBuffer((int) size);
        readBuffer((int) size2);
        return result;
    }

    private PbType[] readTypes(boolean isSystemEntry) {
        readBuffer(6);
        byte[] buffer = readStructBuffer();
        int num = readUShort() / 20;
        PbType[] array = new PbType[num];
        for (int num2 = 0; num2 < num; num2++) {
            byte[] array2 = readBuffer(20);
            array[num2] = new PbType(this, num2,
                    BufferHelper.getString(project.isUnicode(), buffer, BufferHelper.getUInt(array2, 8L)),
                    array2[16] == 64,
                    isSystemEntry);
            printString(String.format("%04X %s %s", num2, BufferHelper.getHexString(array2), array[num2].getName()));
        }
        return array;
    }

    private PbVariable[] readVariables() {
        return readVariables(false);
    }

    private PbVariable[] readVariables(boolean delayParseType) {
        readBuffer(6);
        byte[] structBuffer = readStructBuffer();
        int num = readUShort() / 20;
        PbVariable[] array = new PbVariable[num];
        for (int num2 = 0; num2 < num; num2++) {
            byte[] buffer = readBuffer(20);
            array[num2] = new PbVariable(this, num2, buffer, structBuffer, delayParseType);
        }
        return array;
    }

    private PbReferencedFunction[] readReferencedFunctions() {
        readBuffer(6);
        byte[] buffer = readStructBuffer();
        int num = readUShort() / 20;
        PbReferencedFunction[] array = new PbReferencedFunction[num];
        for (int num2 = 0; num2 < num; num2++) {
            byte[] array2 = readBuffer(20);
            PbReferencedFunction rf = new PbReferencedFunction(num2, array2);
            rf.setName(BufferHelper.getString(project.isUnicode(), buffer, BufferHelper.getUInt(array2, 8L)));
            rf.setGlobalIndex(BufferHelper.getUShort(array2, 12L));
            rf.setGlobalFunction(array2[16] == 2);
            array[num2] = rf;
        }
        return array;
    }

    // -------------------------------------------------------------------------
    // Private print helpers
    // -------------------------------------------------------------------------

    private void printString(String str) {
        for (int i = 0; i < indent; i++) {
            sb.append('\t');
        }
        sb.append(str);
        sb.append("\r\n");
    }

    private void printBuffer(byte[] buff, int step) {
        if (step <= 0) return;
        int num = buff.length / step;
        for (int i = 0; i < num; i++) {
            printString(String.format("%04X:%04X   ", i, i * step)
                    + BufferHelper.getHexString(buff, (long) (i * step), step));
        }
        int num2 = buff.length - num * step;
        if (num2 > 0) {
            printString(String.format("%04X:%04X   ", num, num * step)
                    + BufferHelper.getHexString(buff, (long) (num * step), num2));
        }
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    private static Date getTime(long timeStamp) {
        return new Date(timeStamp * 1000L);
    }

    // -------------------------------------------------------------------------
    // ParseSrj / ParseExe
    // -------------------------------------------------------------------------

    private void parseSrj() {
        source = project.getString(entryData);
        String[] array = source.split("[\r\n]+");
        for (String text : array) {
            if (text.startsWith("PBD:")) {
                project.onNewLibrary(text.substring(4).split(",")[0], true);
            }
        }
    }

    private void parseExe() {
        List<String> list = new ArrayList<>();
        List<String> list2 = new ArrayList<>();

        if (project.isUnicode()) {
            int num = 0;
            int num2 = ((entryData[num + 1] & 0xFF) << 8) | (entryData[num] & 0xFF);
            num += 2;
            while (num2 > 0) {
                while (entryData[num] != 0 || entryData[num + 1] != 0) {
                    num += 2;
                }
                num += 2;
                num2--;
            }
            num2 = ((entryData[num + 1] & 0xFF) << 8) | (entryData[num] & 0xFF);
            num += 2;
            int num3 = num;
            while (num2 > 0) {
                while (entryData[num] != 0 || entryData[num + 1] != 0) {
                    num += 2;
                }
                list.add(new String(entryData, num3, num - num3, StandardCharsets.UTF_16LE));
                num += 2;
                num3 = num;
                num2--;
            }
            num2 = ((entryData[num + 1] & 0xFF) << 8) | (entryData[num] & 0xFF);
            num += 2;
            num3 = num;
            while (num2 > 0) {
                while (entryData[num] != 0 || entryData[num + 1] != 0) {
                    num += 2;
                }
                list2.add(new String(entryData, num3, num - num3, StandardCharsets.UTF_16LE));
                num += 2;
                num3 = num;
                num2--;
            }
        } else {
            int j;
            int num4;
            if (project.isPb5()) {
                j = 0;
                num4 = 1;
            } else {
                j = 1;
                num4 = entryData[0] & 0xFF;
            }
            while (num4 > 0) {
                while (entryData[j] != 0) {
                    j++;
                }
                j++;
                num4--;
            }
            num4 = ((entryData[j + 1] & 0xFF) << 8) | (entryData[j] & 0xFF);
            j += 2;
            int num5 = j;
            while (num4 > 0) {
                while (entryData[j] != 0) {
                    j++;
                }
                list.add(new String(entryData, num5, j - num5, Charset.defaultCharset()));
                j++;
                num5 = j;
                num4--;
            }
            num4 = ((entryData[j + 1] & 0xFF) << 8) | (entryData[j] & 0xFF);
            j += 2;
            num5 = j;
            while (num4 > 0) {
                while (entryData[j] != 0) {
                    j++;
                }
                list2.add(new String(entryData, num5, j - num5, Charset.defaultCharset()));
                j++;
                num5 = j;
                num4--;
            }
        }

        for (String item : list) {
            project.onNewLibrary(item, false);
        }

        StringBuilder libSb = new StringBuilder();
        libSb.append(String.format("Libraries %04X:\r\n\t", list.size()));
        libSb.append(IntStream.range(0, list.size())
                .mapToObj(i -> String.format("%04X:\t%s", i, list.get(i)))
                .collect(Collectors.joining("\r\n\t")));
        libSb.append(String.format("\r\nEntries %04X:\r\n\t", list2.size()));
        libSb.append(IntStream.range(0, list2.size())
                .mapToObj(i -> String.format("%04X:\t%s", i, list2.get(i)))
                .collect(Collectors.joining("\r\n\t")));
        source = libSb.toString();
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return file.getFileName() + "/" + entryName;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Map<Integer, PbType> getTypes() {
        return types;
    }

    public Map<Integer, PbObject> getObjects() {
        return objects;
    }

    public PbProject getProject() {
        return project;
    }

    public PbFile getFile() {
        return file;
    }

    public String getEntryName() {
        return entryName;
    }

    public String getName() {
        return name;
    }

    public String getSuffix() {
        return suffix;
    }

    public byte[] getVariableBuffer() {
        return variableBuffer;
    }

    public void setVariableBuffer(byte[] variableBuffer) {
        this.variableBuffer = variableBuffer;
    }

    public PbVariable[] getVariables() {
        return variables;
    }

    public void setVariables(PbVariable[] variables) {
        this.variables = variables;
    }

    public PbObject getEntryObject() {
        return entryObject;
    }

    public void setEntryObject(PbObject entryObject) {
        this.entryObject = entryObject;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Date getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public Date getCompiledTime() {
        return compiledTime;
    }

    public void setCompiledTime(Date compiledTime) {
        this.compiledTime = compiledTime;
    }
}
