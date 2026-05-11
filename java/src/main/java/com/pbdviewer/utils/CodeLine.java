package com.pbdviewer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CodeLine {

    public int pCodePosition;
    public Integer debugLine;
    public int pCodeOp;
    public byte[] pCodeParam;
    public String sCode;
    public int jmpPosition;
    public JmpType jmpType;
    public String condition;
    public CodeLine preCodeLine;
    public CodeLine nextCodeLine;
    public final List<String> labelSCode = new ArrayList<>();

    @Override
    public String toString() {
        String labels = labelSCode.stream()
                .map(o -> String.format("%-47s %s\r\n", "", o))
                .collect(Collectors.joining());

        String debugLineHex = debugLine != null
                ? String.format("%04X", debugLine)
                : "    ";
        String line = String.format("%-4s %04X:  ", debugLineHex, pCodePosition)
                + String.format("%04X  %s", pCodeOp, BufferHelper.getHexString(pCodeParam));
        line = String.format("%-47s %s", line, sCode);

        return labels + line;
    }
}
