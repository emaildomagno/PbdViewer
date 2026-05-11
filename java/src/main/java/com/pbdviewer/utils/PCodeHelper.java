package com.pbdviewer.utils;

import com.pbdviewer.utils.pbclass.PbFunction;
import com.pbdviewer.utils.pcode.*;

import java.util.*;
import java.util.stream.Collectors;

public class PCodeHelper {

    public static final Map<Integer, Integer> COUNT = new HashMap<>();
    public static final Map<Integer, Integer> GOODCOUNT = new HashMap<>();
    public static final Map<Integer, Set<Integer>> USED_PCODE_LIST = new HashMap<>();
    public static final Map<Integer, Set<Integer>> UNPARSED_PCODE_LIST = new HashMap<>();

    public static Iterable<String> parsePCode(PbFunction pbFunction, boolean depth) {
        if (pbFunction.getProject().isDebug && !depth) {
            return privateParseCode(pbFunction, false);
        }
        List<String> list = new ArrayList<>();
        for (String item : privateParseCode(pbFunction, depth)) {
            String[] array = item.split("[\r\n]+");
            for (String text : array) {
                String text2 = text;
                if (!pbFunction.getProject().isDebug) {
                    if (text.length() < 48) continue;
                    text2 = text.substring(48);
                    if (text2.isBlank()) {
                        continue;
                    }
                }
                list.add(text2);
            }
        }
        return list;
    }

    private static Iterable<String> privateParseCode(PbFunction pbFunction, boolean depth) {
        int version = pbFunction.getProject().getVersion();
        init(version);
        Map<Integer, CodeLine> dictionary = new LinkedHashMap<>();

        if (pbFunction.getPCodeBytes().length == 0) {
            return dictionary.values().stream().map(CodeLine::toString).collect(Collectors.toList());
        }

        PCodeParserBase pCodeParse = getPCodeParse(pbFunction, version);
        Map<Integer, Integer> debugMap = new HashMap<>();
        for (int i = 0; i < pbFunction.getDebugBytes().length / 4; i++) {
            byte[] buffer = BufferHelper.getBuffer(pbFunction.getDebugBytes(), i * 4, 4);
            debugMap.put(BufferHelper.getUShort(buffer, 2L), BufferHelper.getUShort(buffer, 0L));
        }

        boolean flag = false;
        int num = 0;
        CodeLine codeLine = null;

        while (num < pbFunction.getPCodeBytes().length) {
            int uShort = BufferHelper.getUShort(pbFunction.getPCodeBytes(), num);
            int pCodeLen = pCodeParse != null ? pCodeParse.getPCodeLen(uShort) : 255;
            if ((pCodeLen & 0xFF) == 255) {
                flag = true;
                break;
            }
            CodeLine codeLine2 = new CodeLine();
            codeLine2.pCodePosition = num;
            codeLine2.debugLine = debugMap.containsKey(num) ? debugMap.get(num) : null;
            codeLine2.pCodeOp = uShort;
            codeLine2.pCodeParam = BufferHelper.getBuffer(pbFunction.getPCodeBytes(), num + 2, pCodeLen * 2);

            if (codeLine != null) {
                codeLine.nextCodeLine = codeLine2;
                codeLine2.preCodeLine = codeLine;
            }
            codeLine = codeLine2;

            if (depth) {
                try {
                    if (!flag) {
                        pCodeParse.parsePCode(codeLine2);
                        if (codeLine2.sCode != null && codeLine2.sCode.startsWith("--") &&
                                !UNPARSED_PCODE_LIST.get(version).contains(codeLine2.pCodeOp)) {
                            UNPARSED_PCODE_LIST.get(version).add(codeLine2.pCodeOp);
                        }
                    }
                } catch (Exception e) {
                    flag = true;
                }
            }

            USED_PCODE_LIST.get(version).add(codeLine2.pCodeOp);
            dictionary.put(codeLine2.pCodePosition, codeLine2);
            num = (num + 2 + pCodeLen * 2) & 0xFFFF;
            if (num > pbFunction.getPCodeBytes().length) break;
        }

        if (depth) {
            try {
                parseJmp(pbFunction, dictionary);
            } catch (Exception e) {
                flag = true;
            }
        }

        if (!flag) {
            GOODCOUNT.merge(version, 1, Integer::sum);
        }
        COUNT.merge(version, 1, Integer::sum);

        return dictionary.values().stream().map(CodeLine::toString).collect(Collectors.toList());
    }

    private static void parseJmp(PbFunction pbFunction, Map<Integer, CodeLine> list) {
        List<CodeArea> areas = new ArrayList<>();
        if (pbFunction.getProject().getVersion() >= 283) {
            parseReturn(list);
        }
        parseTryCatchFinally(list, areas);
        parseChoose(list, areas);
        parseForNext(list, areas);
        parseDoLoop(list, areas);
        parseExitContinue(list, areas);
        parseIfElse(list);
        parseGoto(list);
        parseEventReturn(list);
        parseIndent(pbFunction, list);
    }

    private static void parseReturn(Map<Integer, CodeLine> list) {
        for (CodeLine value : list.values()) {
            if (value.sCode != null && value.sCode.startsWith("return ") &&
                    value.nextCodeLine != null && value.nextCodeLine.jmpType == JmpType.Jmp &&
                    list.containsKey(value.nextCodeLine.jmpPosition) &&
                    "return".equals(list.get(value.nextCodeLine.jmpPosition).sCode)) {
                value.nextCodeLine.sCode = "";
                value.nextCodeLine.jmpType = JmpType.None;
            }
        }
    }

    private static void parseTryCatchFinally(Map<Integer, CodeLine> list, List<CodeArea> areas) {
        for (CodeLine value : list.values()) {
            if (value.sCode == null || value.sCode.isEmpty()) {
                continue;
            }
            if (value.jmpType == JmpType.JmpIfFalse && value.condition != null && value.condition.startsWith("catch (")) {
                value.sCode = value.condition;
                value.jmpType = JmpType.None;
                CodeLine jmpTarget = list.get(value.jmpPosition);
                if (jmpTarget != null && jmpTarget.preCodeLine != null &&
                        !jmpTarget.sCode.startsWith("end try ") && !jmpTarget.sCode.startsWith("enter finally ") &&
                        jmpTarget.preCodeLine.jmpType == JmpType.Jmp) {
                    CodeLine preJmpTarget = list.get(jmpTarget.preCodeLine.jmpPosition);
                    if (preJmpTarget != null &&
                            (preJmpTarget.sCode.startsWith("end try ") || preJmpTarget.sCode.startsWith("enter finally "))) {
                        jmpTarget.preCodeLine.sCode = "";
                        jmpTarget.preCodeLine.jmpType = JmpType.None;
                    }
                }
            }
            if (value.jmpType == JmpType.Jmp && list.containsKey(value.jmpPosition)) {
                CodeLine target = list.get(value.jmpPosition);
                if (target.sCode.startsWith("end try ") || target.sCode.startsWith("enter finally ")) {
                    value.sCode = "";
                    value.jmpType = JmpType.None;
                }
            }
            if ("enter finally ".equals(value.sCode)) {
                value.sCode = "";
                CodeLine finallyTarget = list.get(value.jmpPosition);
                if (finallyTarget != null) {
                    finallyTarget.labelSCode.add("finally ");
                }
            }
        }
    }

    private static void parseChoose(Map<Integer, CodeLine> list, List<CodeArea> areas) {
        Map<String, CodeArea> dictionary = new LinkedHashMap<>();
        for (CodeLine value : list.values()) {
            if (value.sCode == null || value.sCode.isEmpty()) {
                continue;
            }
            if (value.sCode.startsWith("case")) {
                String text = value.sCode.substring(0, value.sCode.indexOf('=')).trim();
                value.sCode = value.sCode.replace(text + " = ", "choose case ");
                dictionary.put(text, new CodeArea("choose", value.pCodePosition, 0));
            }
            if (value.jmpType != JmpType.JmpIfFalse || value.jmpPosition <= value.pCodePosition ||
                    value.condition == null || !value.condition.contains("")) {
                continue;
            }
            String text2 = "";
            for (Map.Entry<String, CodeArea> entry : dictionary.entrySet()) {
                String key = entry.getKey();
                if (value.condition.endsWith(key) || value.condition.contains(key + " ")) {
                    text2 = key;
                    break;
                }
            }
            if (text2.isEmpty()) {
                continue;
            }
            if (value.condition.contains(String.format(" <= %s and ", text2))) {
                value.sCode = "case " + value.condition
                        .replace(String.format(" <= %s and ", text2), " to ")
                        .replace(String.format(" >= %s", text2), "");
            } else if (value.condition.endsWith(String.format(" = %s", text2))) {
                value.sCode = "case " + value.condition.replace(String.format(" = %s", text2), "");
            } else if (value.condition.contains(String.format(" <= %s", text2))) {
                value.sCode = "case is >= " + value.condition.replace(String.format(" <= %s", text2), "");
            } else if (value.condition.contains(String.format(" >= %s", text2))) {
                value.sCode = "case is <= " + value.condition.replace(String.format(" >= %s", text2), "");
            } else if (value.condition.contains(String.format(" < %s", text2))) {
                value.sCode = "case is > " + value.condition.replace(String.format(" < %s", text2), "");
            } else if (value.condition.contains(String.format(" > %s", text2))) {
                value.sCode = "case is < " + value.condition.replace(String.format(" > %s", text2), "");
            }
            value.jmpType = JmpType.None;
            CodeLine preCodeLine = value.preCodeLine;
            while (preCodeLine != null && (preCodeLine.sCode == null || preCodeLine.sCode.isEmpty())) {
                preCodeLine = preCodeLine.preCodeLine;
            }
            if (preCodeLine != null && "case else ".equals(preCodeLine.sCode)) {
                preCodeLine.sCode = "";
            }
            CodeLine jmpTarget = list.get(value.jmpPosition);
            if (jmpTarget != null && jmpTarget.preCodeLine != null && jmpTarget.preCodeLine.jmpType == JmpType.Jmp) {
                jmpTarget.preCodeLine.sCode = "case else ";
                jmpTarget.preCodeLine.jmpType = JmpType.None;
                CodeArea area = dictionary.get(text2);
                if (area.end() == 0) {
                    CodeLine endTarget = list.get(jmpTarget.preCodeLine.jmpPosition);
                    if (endTarget != null) {
                        endTarget.labelSCode.add(0, "end choose ");
                        dictionary.put(text2, new CodeArea(area.type(), area.start(), jmpTarget.preCodeLine.pCodePosition));
                    }
                }
            } else if (jmpTarget != null) {
                CodeArea area = dictionary.get(text2);
                if (area.end() == 0) {
                    jmpTarget.labelSCode.add(0, "end choose ");
                    CodeLine preJmpTarget = jmpTarget.preCodeLine;
                    dictionary.put(text2, new CodeArea(area.type(), area.start(), preJmpTarget != null ? preJmpTarget.pCodePosition : 0));
                }
            }
        }
        areas.addAll(dictionary.values());
    }

    private static void parseForNext(Map<Integer, CodeLine> list, List<CodeArea> areas) {
        for (CodeLine value : list.values()) {
            if (value.sCode == null || value.sCode.isEmpty()) continue;
            if (value.jmpType != JmpType.JmpIfFalse) continue;
            if (value.jmpPosition <= value.pCodePosition) continue;
            CodeLine jmpTarget = list.get(value.jmpPosition);
            if (jmpTarget == null || jmpTarget.preCodeLine == null) continue;
            if (jmpTarget.preCodeLine.jmpType != JmpType.Jmp) continue;
            if (jmpTarget.preCodeLine.jmpPosition >= jmpTarget.preCodeLine.pCodePosition) continue;
            if (jmpTarget.preCodeLine.jmpPosition >= value.pCodePosition) continue;
            CodeLine innerPreCodeLine = list.get(jmpTarget.preCodeLine.jmpPosition);
            if (innerPreCodeLine == null) continue;
            CodeLine preCodeLine = innerPreCodeLine.preCodeLine;
            if (preCodeLine == null || preCodeLine.jmpType != JmpType.Jmp) {
                continue;
            }
            String[] array = value.condition != null ? value.condition.split("[><= ]+") : new String[0];
            // filter empty
            List<String> parts = new ArrayList<>();
            for (String p : array) if (!p.isEmpty()) parts.add(p);
            if (parts.size() > 1) {
                CodeLine preCodeLine2 = value.preCodeLine;
                while (preCodeLine2 != null && (preCodeLine2.sCode == null || preCodeLine2.sCode.isEmpty())) {
                    preCodeLine2 = preCodeLine2.preCodeLine;
                }
                String arg = "";
                if (preCodeLine2 != null && preCodeLine2.sCode != null &&
                        preCodeLine2.sCode.startsWith(String.format("%s += ", parts.get(1)))) {
                    arg = "step " + preCodeLine2.sCode.substring(String.format("%s += ", parts.get(1)).length());
                }
                if (preCodeLine2 != null) preCodeLine2.sCode = "";
                String initSCode = preCodeLine != null && preCodeLine.preCodeLine != null ? preCodeLine.preCodeLine.sCode : "";
                value.sCode = String.format("for %s to %s %s", initSCode, parts.get(0), arg).trim();
                value.jmpType = JmpType.None;
                if (preCodeLine != null && preCodeLine.preCodeLine != null) preCodeLine.preCodeLine.sCode = "";
                if (preCodeLine != null) {
                    preCodeLine.sCode = "";
                    preCodeLine.jmpType = JmpType.None;
                }
                jmpTarget.preCodeLine.sCode = "next ";
                jmpTarget.preCodeLine.jmpType = JmpType.None;
                areas.add(new CodeArea("for", value.pCodePosition, jmpTarget.preCodeLine.pCodePosition));
            }
        }
    }

    private static void parseDoLoop(Map<Integer, CodeLine> list, List<CodeArea> areas) {
        for (CodeLine value : list.values()) {
            if (value.sCode == null || value.sCode.isEmpty()) {
                continue;
            }
            if (value.jmpType == JmpType.JmpIfFalse) {
                if (value.jmpPosition > value.pCodePosition) {
                    CodeLine jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget != null && jmpTarget.preCodeLine != null &&
                            jmpTarget.preCodeLine.jmpType == JmpType.Jmp &&
                            jmpTarget.preCodeLine.jmpPosition < jmpTarget.preCodeLine.pCodePosition &&
                            jmpTarget.preCodeLine.jmpPosition < value.pCodePosition) {
                        value.sCode = String.format("do while %s", value.condition);
                        value.jmpType = JmpType.None;
                        jmpTarget.preCodeLine.sCode = "loop ";
                        jmpTarget.preCodeLine.jmpType = JmpType.None;
                        areas.add(new CodeArea("do", value.pCodePosition, jmpTarget.preCodeLine.pCodePosition));
                    }
                } else {
                    CodeLine jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget != null) {
                        jmpTarget.labelSCode.add("do ");
                    }
                    value.sCode = String.format("loop until %s", value.condition);
                    value.jmpType = JmpType.None;
                    if (jmpTarget != null) {
                        areas.add(new CodeArea("do", jmpTarget.pCodePosition, value.pCodePosition));
                    }
                }
            } else if (value.jmpType == JmpType.JmpIfTrue) {
                if (value.jmpPosition > value.pCodePosition) {
                    CodeLine jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget != null && jmpTarget.preCodeLine != null &&
                            jmpTarget.preCodeLine.jmpType == JmpType.Jmp &&
                            jmpTarget.preCodeLine.jmpPosition < jmpTarget.preCodeLine.pCodePosition &&
                            jmpTarget.preCodeLine.jmpPosition < value.pCodePosition) {
                        value.sCode = String.format("do until %s", value.condition);
                        value.jmpType = JmpType.None;
                        jmpTarget.preCodeLine.sCode = "loop ";
                        jmpTarget.preCodeLine.jmpType = JmpType.None;
                        areas.add(new CodeArea("do", value.pCodePosition, jmpTarget.preCodeLine.pCodePosition));
                    }
                } else {
                    CodeLine jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget != null) {
                        jmpTarget.labelSCode.add("do ");
                    }
                    value.sCode = String.format("loop while %s", value.condition);
                    value.jmpType = JmpType.None;
                    if (jmpTarget != null) {
                        areas.add(new CodeArea("do", jmpTarget.pCodePosition, value.pCodePosition));
                    }
                }
            }
        }
    }

    private static void parseExitContinue(Map<Integer, CodeLine> list, List<CodeArea> areas) {
        if (areas.isEmpty()) {
            return;
        }
        for (CodeLine codeLine : list.values()) {
            if (codeLine.sCode == null || codeLine.sCode.isEmpty()) continue;
            if (codeLine.jmpType != JmpType.Jmp) continue;
            if (codeLine.jmpPosition <= codeLine.pCodePosition) continue;

            // Find nearest area (minimizing distance sum)
            CodeArea codeArea = areas.stream()
                    .min(Comparator.comparingInt(o ->
                            Math.abs(codeLine.pCodePosition - o.start()) + Math.abs(o.end() - codeLine.pCodePosition)))
                    .orElse(null);

            if (codeArea == null) continue;
            if (codeLine.pCodePosition >= codeArea.start() && codeLine.pCodePosition <= codeArea.end()) {
                CodeLine jmpTarget = list.get(codeLine.jmpPosition);
                if (jmpTarget != null && jmpTarget.preCodeLine != null &&
                        jmpTarget.preCodeLine.pCodePosition == codeArea.end()) {
                    codeLine.sCode = "exit";
                    codeLine.jmpType = JmpType.None;
                } else if (jmpTarget != null && jmpTarget.pCodePosition == codeArea.end()) {
                    codeLine.sCode = "continue";
                    codeLine.jmpType = JmpType.None;
                }
            }
        }
    }

    private static void parseIfElse(Map<Integer, CodeLine> list) {
        for (CodeLine value : list.values()) {
            if (value.sCode == null || value.sCode.isEmpty()) continue;
            if (value.jmpType != JmpType.JmpIfFalse) continue;
            if (value.jmpPosition <= value.pCodePosition) continue;

            CodeLine jmpTarget = list.get(value.jmpPosition);
            if (jmpTarget == null) continue;

            if (jmpTarget.preCodeLine != null && jmpTarget.preCodeLine.jmpType == JmpType.Jmp &&
                    jmpTarget.preCodeLine.jmpPosition > jmpTarget.preCodeLine.pCodePosition) {
                value.sCode = String.format("if %s then ", value.condition);
                value.jmpType = JmpType.None;
                if ("exit".equals(jmpTarget.preCodeLine.sCode) || "continue".equals(jmpTarget.preCodeLine.sCode)) {
                    jmpTarget.preCodeLine.jmpType = JmpType.None;
                    jmpTarget.labelSCode.add(0, "end if ");
                } else {
                    jmpTarget.preCodeLine.sCode = "else ";
                    jmpTarget.preCodeLine.jmpType = JmpType.None;
                    CodeLine endIfTarget = list.get(jmpTarget.preCodeLine.jmpPosition);
                    if (endIfTarget != null) {
                        endIfTarget.labelSCode.add(0, "end if ");
                    }
                }
            } else {
                value.sCode = String.format("if %s then", value.condition);
                value.jmpType = JmpType.None;
                jmpTarget.labelSCode.add(0, "end if ");
            }
        }

        int num = 0;
        Set<Integer> hashSet = new HashSet<>();
        for (CodeLine value2 : list.values()) {
            if (value2.sCode == null) {
                continue;
            }
            if (value2.sCode.trim().startsWith("if ")) {
                num++;
                CodeLine firstValidPreCodeLine = getFirstValidPreCodeLine(value2.preCodeLine);
                if (firstValidPreCodeLine != null && "else ".equals(firstValidPreCodeLine.sCode)) {
                    firstValidPreCodeLine.sCode += value2.sCode;
                    value2.sCode = "";
                    hashSet.add(num);
                }
            }
            for (int i = 0; i < value2.labelSCode.size(); i++) {
                if (value2.labelSCode.get(i).trim().startsWith("end if")) {
                    if (hashSet.contains(num)) {
                        value2.labelSCode.set(i, "");
                        hashSet.remove(num);
                    }
                    num--;
                }
            }
            if (value2.sCode.trim().startsWith("end if")) {
                if (hashSet.contains(num)) {
                    value2.sCode = "";
                    hashSet.remove(num);
                }
                num--;
            }
        }
    }

    private static void parseGoto(Map<Integer, CodeLine> list) {
        // No-op: mirrors C# which only reads but doesn't modify
        for (CodeLine value : list.values()) {
            if (value.sCode != null && !value.sCode.isEmpty()) {
                // nothing to do
            }
        }
    }

    private static void parseEventReturn(Map<Integer, CodeLine> list) {
        for (CodeLine value : list.values()) {
            if (value.sCode == null) continue;
            if (!value.sCode.trim().startsWith("if isvalid(::message) then goto ")) {
                continue;
            }
            CodeLine next1 = getFirstValidNextCodeLine(value);
            if (next1 == null || !"return 0".equals(next1.sCode.trim())) {
                continue;
            }
            CodeLine next2 = getFirstValidNextCodeLine(next1);
            if (next2 != null && next2.sCode.trim().startsWith("goto ")) {
                CodeLine next3 = getFirstValidNextCodeLine(next2);
                if (next3 != null && "return ::message.returnvalue".equals(next3.sCode.trim())) {
                    value.sCode = "";
                    next1.sCode = "";
                    next2.sCode = "";
                    next3.sCode = "";
                }
            }
        }
    }

    private static void parseIndent(PbFunction pbFunction, Map<Integer, CodeLine> list) {
        int[] indent = {0};
        for (CodeLine value : list.values()) {
            try {
                for (int i = 0; i < value.labelSCode.size(); i++) {
                    value.labelSCode.set(i, parseIndentString(value.labelSCode.get(i), indent));
                }
                if (value.sCode != null && !value.sCode.isEmpty()) {
                    value.sCode = parseIndentString(value.sCode, indent);
                }
            } catch (Exception e) {
                break;
            }
        }
    }

    private static String parseIndentString(String scode, int[] indent) {
        if (scode.startsWith("try ")) {
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("catch ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("finally ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("end try ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith("if ")) {
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("else ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("end if ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith("for ")) {
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("next ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith("choose case ")) {
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
            indent[0]++;
        } else if (scode.startsWith("case ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("end choose ")) {
            indent[0]--;
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith("do ")) {
            scode = " ".repeat(indent[0] * 4) + scode;
            indent[0]++;
        } else if (scode.startsWith("loop ")) {
            indent[0]--;
            scode = " ".repeat(indent[0] * 4) + scode;
        } else if (!scode.isEmpty()) {
            scode = " ".repeat(indent[0] * 4) + scode;
        }
        return scode;
    }

    private static void init(int version) {
        if (!COUNT.containsKey(version)) {
            COUNT.put(version, 0);
            GOODCOUNT.put(version, 0);
            USED_PCODE_LIST.put(version, new HashSet<>());
            UNPARSED_PCODE_LIST.put(version, new HashSet<>());
        }
    }

    private static PCodeParserBase getPCodeParse(PbFunction pbFunction, int version) {
        return switch (version) {
            case 79, 114, 146, 166, 193, 196 -> new PCodeParser90(pbFunction);
            case 238 -> new PCodeParser100(pbFunction);
            case 283 -> new PCodeParser105(pbFunction);
            case 316, 319, 321, 322, 325, 333, 334 -> new PCodeParser110(pbFunction);
            default -> null;
        };
    }

    private static CodeLine getFirstValidPreCodeLine(CodeLine codeLine) {
        if (codeLine == null) return null;
        CodeLine pre = codeLine.preCodeLine;
        while (pre != null && (pre.sCode == null || pre.sCode.isEmpty())) {
            pre = pre.preCodeLine;
        }
        return pre;
    }

    private static CodeLine getFirstValidNextCodeLine(CodeLine codeLine) {
        if (codeLine == null) return null;
        CodeLine next = codeLine.nextCodeLine;
        while (next != null && (next.sCode == null || next.sCode.isEmpty())) {
            next = next.nextCodeLine;
        }
        return next;
    }
}
