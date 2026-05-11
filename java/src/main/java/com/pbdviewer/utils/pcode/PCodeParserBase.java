package com.pbdviewer.utils.pcode;

import com.pbdviewer.utils.pbclass.*;
import com.pbdviewer.utils.BufferHelper;
import com.pbdviewer.utils.CodeLine;
import com.pbdviewer.utils.JmpType;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class PCodeParserBase {

    private static class StackObject {
        PbType type;
        String str;
        String operator;

        StackObject(String str, PbType type) {
            this.type = type;
            this.str = str;
        }

        StackObject(String str) {
            this(str, null);
        }

        @Override
        public String toString() {
            return str;
        }
    }

    private final Deque<StackObject> stackObjects = new ArrayDeque<>();

    private CodeLine codeLine;

    protected final PbFunction pbFunction;

    protected PCodeParserBase(PbFunction pbFunction) {
        this.pbFunction = pbFunction;
    }

    protected abstract int[] getPCodeLenArray();

    protected abstract boolean onParsePcode(int pCodeOp, CodeLine codeLine);

    public void parsePCode(CodeLine codeLine) {
        this.codeLine = codeLine;
        codeLine.sCode = "";
        if (!onParsePcode(codeLine.pCodeOp, codeLine)) {
            codeLine.sCode = String.format("-------%04X", codeLine.pCodeOp);
        }
    }

    public int getPCodeLen(int pcode) {
        return onGetPCodeLen(pcode);
    }

    protected int onGetPCodeLen(int pcode) {
        int[] arr = getPCodeLenArray();
        if (pcode < arr.length) {
            return arr[pcode];
        }
        return 255;
    }

    // -------------------------------------------------------------------------
    // Variable assignment beginners
    // -------------------------------------------------------------------------

    protected void beginAssignLocalVariable(int index) {
        pushVariable(pbFunction.getVariables()[index]);
    }

    protected void beginAssignSharedVariable(int index) {
        pushVariable(pbFunction.getEntry().getVariables()[index]);
    }

    protected void beginAssignGlobalVariable(int index) {
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable found = null;
        for (PbVariable v : vars) {
            if (v.getGlobalIndex() == index) { found = v; break; }
        }
        pushVariable(found);
    }

    protected void beginAssignInstanceVariable() {
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        if ("entryobject".equals(stackObject2.str)) {
            stackObjects.push(new StackObject(String.format("%s", stackObject.str), stackObject.type));
        } else {
            stackObjects.push(new StackObject(String.format("%s.%s", stackObject2.str, stackObject.str), stackObject.type));
        }
    }

    // -------------------------------------------------------------------------
    // End assign
    // -------------------------------------------------------------------------

    protected void endAssign(boolean isArray) {
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        if (isArray) {
            codeLine.sCode = String.format("%s[] = %s", stackObject2.str, stackObject.str);
        } else {
            codeLine.sCode = String.format("%s = %s", stackObject2.str, stackObject.str);
        }
    }

    protected void endAssign() {
        endAssign(false);
    }

    protected void endAssign(String operator) {
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        codeLine.sCode = String.format("%s %s= %s", stackObject2.str, operator, stackObject.str);
    }

    protected void endAssign2(String operator) {
        StackObject stackObject = stackObjects.pop();
        codeLine.sCode = String.format("%s %s", stackObject.str, operator);
    }

    protected void resetAssign(int count) {
        StackObject item = stackObjects.pop();
        StackObject item2 = stackObjects.peek();
        stackObjects.push(item2);
        stackObjects.push(item);
    }

    // -------------------------------------------------------------------------
    // Push variable helpers
    // -------------------------------------------------------------------------

    protected void pushLocalVariable(int index) {
        pushVariable(pbFunction.getVariables()[index]);
    }

    protected void pushSharedVariable(int index) {
        pushVariable(pbFunction.getEntry().getVariables()[index]);
    }

    protected void pushGlobalSharedVariable(int index) {
        PbVariable[] vars = pbFunction.getEntry().getVariables();
        PbVariable found = null;
        for (PbVariable v : vars) {
            if (v.getGlobalIndex() == index) { found = v; break; }
        }
        pushVariable(found);
    }

    protected void pushGlobalVariable(int index) {
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable found = null;
        for (PbVariable v : vars) {
            if (v.getGlobalIndex() == index) { found = v; break; }
        }
        pushVariable(found);
    }

    protected void pushInstanceVariable(int unknown) {
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        if ("entryobject".equals(stackObject2.str)) {
            stackObjects.push(new StackObject(String.format("%s", stackObject.str), stackObject.type));
        } else {
            stackObjects.push(new StackObject(String.format("%s.%s", stackObject2.str, stackObject.str), stackObject.type));
        }
    }

    protected void pushInstanceVariableName(int offset) {
        long uInt = offset & 0xFFFFFFFFL;
        if (uInt >= 1 && uInt <= 7) {
            stackObjects.push(new StackObject("entryobject", pbFunction.getEntry().getEntryObject().getType()));
            return;
        }
        long nameUInt = BufferHelper.getUInt(pbFunction.getBuffer(), uInt);
        PbType type = stackObjects.peek().type;
        PbVariable pbVariable = null;
        if (type != null) {
            PbObject obj = type.getObject(pbFunction.getEntry());
            if (obj != null) {
                PbVariable[] allVars = obj.getAllVariables();
                int varIdx = BufferHelper.getUShort(pbFunction.getBuffer(), uInt + 4);
                if (varIdx < allVars.length) {
                    pbVariable = allVars[varIdx];
                }
            }
        }
        String text;
        if (pbVariable == null) {
            if ((nameUInt & 0xFFFFL) == 65535L) {
                text = String.format("%04X", BufferHelper.getUShort(pbFunction.getBuffer(), uInt + 4));
            } else {
                text = BufferHelper.getString(pbFunction.getProject().isUnicode(), pbFunction.getBuffer(), nameUInt);
            }
        } else if ((nameUInt & 0xFFFFL) != 65535L) {
            text = BufferHelper.getString(pbFunction.getProject().isUnicode(), pbFunction.getBuffer(), nameUInt);
            if (!pbVariable.getName().equalsIgnoreCase(text)) {
                throw new RuntimeException(String.format("PushInstanceVariableName \"%s\" != \"%s\"", pbVariable.getName(), text));
            }
        } else {
            text = pbVariable.getName();
        }
        stackObjects.push(new StackObject(text, pbVariable != null ? pbVariable.getType() : null));
    }

    protected void pushConstant(String constant) {
        stackObjects.push(new StackObject(constant));
    }

    protected void pushThis() {
        stackObjects.push(new StackObject("this", pbFunction.getObject().getType()));
    }

    protected void pushParent() {
        PbObject parentObject = pbFunction.getObject().getParentObject();
        stackObjects.push(new StackObject("parent", parentObject != null ? parentObject.getType() : null));
    }

    protected void pushEnum(int enumIndex, int itemIndex) {
        PbEnum pbEnum = pbFunction.getProject().getEnums().values().stream()
                .filter(e -> e.getIndex() == enumIndex)
                .findFirst()
                .orElse(null);
        String item = (pbEnum != null) ? pbEnum.getItems().get(itemIndex) : String.format("%04X!%04X", enumIndex, itemIndex);
        stackObjects.push(new StackObject(item, PbType.getPbType(pbFunction.getEntry(), enumIndex)));
    }

    // -------------------------------------------------------------------------
    // Stack operations
    // -------------------------------------------------------------------------

    protected void operateStack(String op) {
        int operatorLevel = getOperatorLevel(op);
        StackObject stackObject = stackObjects.pop();
        int operatorLevel2 = getOperatorLevel(stackObject.operator);
        if (operatorLevel2 >= operatorLevel) {
            stackObject.str = String.format("(%s)", stackObject.str);
        }
        StackObject stackObject2 = stackObjects.pop();
        if (getOperatorLevel(stackObject2.operator) > operatorLevel) {
            stackObject2.str = String.format("(%s)", stackObject2.str);
        }
        String str = stackObject2.str + " " + op + " " + stackObject.str;
        StackObject result = new StackObject(str);
        result.operator = op;
        stackObjects.push(result);
    }

    protected void operateStackSingle(String op) {
        StackObject stackObject = stackObjects.pop();
        if (getOperatorLevel(stackObject.operator) > 0) {
            stackObject.str = String.format("(%s)", stackObject.str);
        }
        String str = op + " " + stackObject.str;
        StackObject result = new StackObject(str);
        result.operator = "$" + op;
        stackObjects.push(result);
    }

    // -------------------------------------------------------------------------
    // Control flow
    // -------------------------------------------------------------------------

    protected void doReturn(int p1) {
        String sCode = (p1 == 1) ? String.format("return %s", stackObjects.pop().str) : "return";
        codeLine.sCode = sCode;
    }

    protected void halt(int force) {
        String text = "halt";
        if (force == 0) {
            text += " close";
        }
        codeLine.sCode = text;
    }

    protected void jump(int pos, JmpType jmpType) {
        codeLine.jmpType = jmpType;
        codeLine.jmpPosition = pos;
        switch (jmpType) {
            case Jmp:
                codeLine.sCode = String.format("goto %04X", pos);
                break;
            case JmpIfTrue:
                codeLine.condition = stackObjects.pop().str;
                codeLine.sCode = String.format("if %s then goto %04X", codeLine.condition, pos);
                break;
            case JmpIfFalse:
                codeLine.condition = stackObjects.pop().str;
                codeLine.sCode = String.format("if %s then not goto %04X", codeLine.condition, pos);
                break;
            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Exception handling
    // -------------------------------------------------------------------------

    protected void tryBlock(int catchpos, int endpos) {
        codeLine.sCode = "try ";
    }

    protected void endTry() {
        codeLine.sCode = "end try ";
    }

    protected void doCatch() {
        StackObject stackObject = stackObjects.pop();
        stackObjects.push(new StackObject(String.format("catch (%s %s)", stackObject.type.getName(), stackObject.str)));
    }

    protected void doThrow() {
        StackObject arg = stackObjects.pop();
        codeLine.sCode = String.format("throw %s", arg);
    }

    protected void enterFinally(int finallypos) {
        codeLine.sCode = "enter finally ";
        codeLine.jmpPosition = finallypos;
    }

    protected void leaveFinally() {
    }

    // -------------------------------------------------------------------------
    // Object creation / destruction
    // -------------------------------------------------------------------------

    protected void createObject(long offset) {
        PbType typeName = getTypeName(offset);
        stackObjects.push(new StackObject(String.format("create %s", typeName.getName()), typeName));
    }

    protected void createObjectUsingName(long index) {
        stackObjects.push(new StackObject(String.format("create using %s", stackObjects.pop()), PbType.getPbType(pbFunction.getEntry(), 8)));
    }

    protected void destroyObject() {
        StackObject stackObject = stackObjects.pop();
        codeLine.sCode = String.format("destroy(%s)", stackObject.str);
    }

    // -------------------------------------------------------------------------
    // Function calls
    // -------------------------------------------------------------------------

    protected void popFunction() {
        codeLine.sCode = String.format("%s", stackObjects.pop().str);
    }

    protected void pushGlobalFunctionName(int objIndex, int functionIndex) {
        String str = null;
        if ((objIndex & 0x8000) == 0x8000) {
            str = pbFunction.getObject().getReferencedFunctions()[functionIndex].getName();
        } else if ((objIndex & 0x4000) == 0x4000) {
            PbEntry systemEntry = pbFunction.getProject().getSystemEntry();
            PbFunctionDefinition pbFunctionDefinition = null;
            if (systemEntry != null) {
                PbObject[] objValues = systemEntry.getObjects().values().toArray(new PbObject[0]);
                if (objIndex < objValues.length) {
                    PbObject pbObj = objValues[objIndex];
                    if (pbObj != null) {
                        PbFunctionDefinition[] defs = pbObj.getFunctionDefinitions();
                        if (functionIndex < defs.length) {
                            pbFunctionDefinition = defs[functionIndex];
                        }
                    }
                }
            }
            str = (pbFunctionDefinition == null)
                    ? String.format("(%04X%04X)", objIndex, functionIndex)
                    : pbFunctionDefinition.getName();
        }
        stackObjects.push(new StackObject(str));
    }

    protected void callGlobalFunction(long count, long type) {
        String text = stackObjects.pop().str;
        StackObject[] source = popStack(count);
        if ((type & 1) == 1) {
            text = "post " + text;
        }
        if ((type & 2) == 2) {
            text = "dynamic " + text;
        }
        if ((type & 4) == 4) {
            text = "event " + text;
        }
        String params = Arrays.stream(source).map(o -> o.str).collect(Collectors.joining(","));
        String str = String.format("%s(%s)", text, params);
        stackObjects.push(new StackObject(str));
    }

    protected void callSuper(long functionIndex, int paramcount, int objType, long nameoffset) {
        for (int i = 0; i < paramcount; i++) {
            stackObjects.pop();
        }
        String str = "call super::" + BufferHelper.getString(pbFunction.getProject().isUnicode(), pbFunction.getBuffer(), nameoffset);
        stackObjects.push(new StackObject(str));
    }

    protected void callFunction(long offset, long count, long type) {
        int uShort = BufferHelper.getUShort(pbFunction.getBuffer(), offset);
        PbType pbType = PbType.getPbType(pbFunction.getEntry(), BufferHelper.getUShort(pbFunction.getBuffer(), offset + 2));
        long uInt = BufferHelper.getUInt(pbFunction.getBuffer(), offset + 4);
        if ((uInt & 0xFFFFL) == 65535L) {
            throw new RuntimeException("CallFunction funnameoffset==0xFFFF");
        }
        StackObject[] source = popStack(count);
        StackObject stackObject = stackObjects.pop();
        String str = stackObject.str;
        String text = BufferHelper.getString(pbFunction.getProject().isUnicode(), pbFunction.getBuffer(), uInt);
        PbFunctionDefinition pbFunctionDefinition = null;
        if (uShort != 0xFFFF && stackObject.type != null && !"any".equals(stackObject.type.getName())) {
            PbObject obj = stackObject.type.getObject(pbFunction.getEntry());
            if (obj != null) {
                PbFunctionDefinition[] allDefs = obj.getAllFunctionDefinitions();
                if (uShort < allDefs.length) {
                    pbFunctionDefinition = allDefs[uShort];
                }
                if (pbFunctionDefinition != null) {
                    String defName = pbFunctionDefinition.getName();
                    String defNameLower = (defName != null) ? defName.toLowerCase() : null;
                    String textLower = (text != null) ? text.toLowerCase() : null;
                    if (!java.util.Objects.equals(defNameLower, textLower)) {
                        pbFunctionDefinition = null;
                    }
                }
            }
        }
        if ((type & 1) == 1) {
            text = "post " + text;
        }
        if ((type & 2) == 2) {
            text = "dynamic " + text;
        }
        if ((type & 4) == 4) {
            text = "event " + text;
        }
        String prefix;
        if (!("this".equals(stackObject.str)) || (pbType.getName() != null && !pbType.getName().isEmpty())) {
            prefix = str + ".";
        } else {
            prefix = "super::";
        }
        String params = Arrays.stream(source).map(o -> o.str).collect(Collectors.joining(","));
        String str2 = String.format("%s%s(%s)", prefix, text, params);
        PbType stackType = stackObject.type;
        PbType resultType;
        if (stackType != null && "any".equals(stackType.getName())) {
            resultType = stackObject.type;
        } else {
            resultType = (pbFunctionDefinition != null) ? pbFunctionDefinition.getReturnType() : null;
        }
        stackObjects.push(new StackObject(str2, resultType));
    }

    protected void callBuiltinFunction(String function, int paramcount) {
        StackObject[] source = popStack(paramcount);
        String params = Arrays.stream(source).map(o -> o.str).collect(Collectors.joining(","));
        String str = String.format("%s(%s)", function, params);
        stackObjects.push(new StackObject(str));
    }

    protected void callBuiltinFunction(String function) {
        callBuiltinFunction(function, 1);
    }

    // -------------------------------------------------------------------------
    // Array operations
    // -------------------------------------------------------------------------

    protected void createArray(long arraylen) {
        StackObject[] source = popStack(arraylen);
        String params = Arrays.stream(source).map(o -> o.str).collect(Collectors.joining(","));
        String str = String.format("{%s}", params);
        stackObjects.push(new StackObject(str));
    }

    protected void index() {
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        String str = String.format("%s[%s]", stackObject2.str, stackObject.str);
        stackObjects.push(new StackObject(str, stackObject2.type));
    }

    protected void index2(int p1, int p2) {
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        String str = String.format("%s[%s]", stackObject2.str, stackObject.str);
        stackObjects.push(new StackObject(str, stackObject2.type));
    }

    protected void index3(int p1, int p2, int p3) {
        popStack(2);
        StackObject stackObject = stackObjects.pop();
        StackObject stackObject2 = stackObjects.pop();
        String str = String.format("%s[%s]", stackObject2.str, stackObject.str);
        stackObjects.push(new StackObject(str, stackObject2.type));
    }

    // -------------------------------------------------------------------------
    // Cast
    // -------------------------------------------------------------------------

    protected void cast(int pos) {
    }

    protected void cast() {
        cast(0);
    }

    // -------------------------------------------------------------------------
    // SQL operations
    // -------------------------------------------------------------------------

    protected void sqlOperateTransaction(String function) {
        StackObject arg = stackObjects.pop();
        codeLine.sCode = String.format("%s using %s;", function, arg);
    }

    protected void sqlOpen(int paramCount) {
        StackObject[] source = popStack(paramCount);
        StackObject stackObject = stackObjects.pop();
        StackObject cusor = stackObjects.pop();
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable pbVariable = null;
        for (PbVariable v : vars) {
            if (v.getName().equals(cusor.str)) { pbVariable = v; break; }
        }
        if (pbVariable != null) {
            pbVariable.setCursorParams(
                    Arrays.stream(source).map(o -> o.str).collect(Collectors.toList()),
                    stackObject.str);
        }
        codeLine.sCode = String.format("open %s;", cusor);
    }

    protected void sqlOpenDynamic(long cursorOffset, int paramCount) {
        StackObject stackObject = stackObjects.pop();
        StackObject cusor = stackObjects.pop();
        StackObject[] source = popStack(paramCount);
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable pbVariable = null;
        for (PbVariable v : vars) {
            if (v.getName().equals(cusor.str)) { pbVariable = v; break; }
        }
        if (pbVariable != null) {
            pbVariable.setDynamicCursorParams(stackObject.str);
        }
        String arg = (paramCount > 0)
                ? String.format("using %s", Arrays.stream(source).map(o -> String.format(":%s", o)).collect(Collectors.joining(",")))
                : "";
        codeLine.sCode = String.format("open dynamic %s %s;", cusor, arg);
    }

    protected void sqlExecute(int paramcount) {
        StackObject[] source = popStack(paramcount);
        StackObject stackObject = stackObjects.pop();
        StackObject procedure = stackObjects.pop();
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable pbVariable = null;
        for (PbVariable v : vars) {
            if (v.getName().equals(procedure.str)) { pbVariable = v; break; }
        }
        if (pbVariable != null) {
            pbVariable.setProcedureParams(
                    Arrays.stream(source).map(o -> o.str).collect(Collectors.toList()),
                    stackObject.str);
        }
        codeLine.sCode = String.format("execute %s;", procedure);
    }

    protected void sqlExecuteDynamic(long procedureOffset, int paramCount) {
        StackObject stackObject = stackObjects.pop();
        StackObject cusor = stackObjects.pop();
        StackObject[] source = popStack(paramCount);
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable pbVariable = null;
        for (PbVariable v : vars) {
            if (v.getName().equals(cusor.str)) { pbVariable = v; break; }
        }
        if (pbVariable != null) {
            pbVariable.setDynamicProcedureParams(stackObject.str);
        }
        String arg = (paramCount > 0)
                ? String.format("using %s", Arrays.stream(source).map(o -> String.format(":%s", o)).collect(Collectors.joining(",")))
                : "";
        codeLine.sCode = String.format("execute dynamic %s %s;", cusor, arg);
    }

    protected void sqlFetch(int paramcount) {
        stackObjects.pop();
        StackObject stackObject = stackObjects.pop();
        StackObject[] source = popStack(paramcount);
        String into = Arrays.stream(source).map(o -> String.format(":%s", o.str)).collect(Collectors.joining(","));
        codeLine.sCode = String.format("fetch %s into %s;", stackObject.str, into);
    }

    protected void sqlClose() {
        stackObjects.pop();
        StackObject stackObject = stackObjects.pop();
        codeLine.sCode = String.format("close %s;", stackObject.str);
    }

    protected void sqlPrepareSqlsa() {
        StackObject arg = stackObjects.pop();
        String text = stackObjects.pop().str;
        if (!text.startsWith("\"")) {
            text = ":" + text;
        }
        StackObject arg2 = stackObjects.pop();
        codeLine.sCode = String.format("prepare %s from %s using %s;", arg2, text, arg);
    }

    protected void sqlExecuteSqlsa(int paramcount) {
        StackObject[] source = popStack(paramcount);
        StackObject arg = stackObjects.pop();
        String using = Arrays.stream(source).map(o -> String.format(":%s", o)).collect(Collectors.joining(","));
        codeLine.sCode = String.format("execute %s using %s;", arg, using);
    }

    protected void sqlExecuteImmediate() {
        StackObject arg = stackObjects.pop();
        String text = stackObjects.pop().str;
        if (!text.startsWith("\"")) {
            text = ":" + text;
        }
        codeLine.sCode = String.format("execute immediate %s using %s;", text, arg);
    }

    protected void sqlDescribe() {
        StackObject arg = stackObjects.pop();
        StackObject arg2 = stackObjects.pop();
        codeLine.sCode = String.format("describe %s into %s;", arg2, arg);
    }

    protected void sqlOpenDynamicDescriptor(long cursorOffset) {
        StackObject stackObject = stackObjects.pop();
        StackObject cusor = stackObjects.pop();
        StackObject arg = stackObjects.pop();
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable pbVariable = null;
        for (PbVariable v : vars) {
            if (v.getName().equals(cusor.str)) { pbVariable = v; break; }
        }
        if (pbVariable != null) {
            pbVariable.setDynamicCursorParams(stackObject.str);
        }
        codeLine.sCode = String.format("open dynamic %s using descriptor %s;", cusor, arg);
    }

    protected void sqlExecuteDynamicDescriptor(long procedureOffset) {
        StackObject stackObject = stackObjects.pop();
        StackObject cusor = stackObjects.pop();
        StackObject arg = stackObjects.pop();
        PbVariable[] vars = pbFunction.getVariables();
        PbVariable pbVariable = null;
        for (PbVariable v : vars) {
            if (v.getName().equals(cusor.str)) { pbVariable = v; break; }
        }
        if (pbVariable != null) {
            pbVariable.setDynamicCursorParams(stackObject.str);
        }
        codeLine.sCode = String.format("execute dynamic %s using descriptor %s;", cusor, arg);
    }

    protected void sqlFetchDynamicDescriptor() {
        stackObjects.pop();
        StackObject arg = stackObjects.pop();
        StackObject arg2 = stackObjects.pop();
        codeLine.sCode = String.format("fetch %s using descriptor %s;", arg, arg2);
    }

    protected void sqlDirectInsertUpdateDelete(long cursorOffset, int paramcount) {
        StackObject arg = stackObjects.pop();
        StackObject[] source = popStack(paramcount);
        String cursor = BufferHelper.getCursor(pbFunction.getProject().isUnicode(),
                pbFunction.getEntry().getVariableBuffer(), cursorOffset,
                Arrays.stream(source).map(o -> o.str).collect(Collectors.toList()));
        codeLine.sCode = String.format("%s using %s;", cursor, arg);
    }

    protected void sqlDirectSelect(long cursorOffset, int paramcount1, int paramcount2) {
        StackObject arg = stackObjects.pop();
        StackObject[] source = popStack(paramcount1);
        StackObject[] source2 = popStack(paramcount2);
        String cursor = BufferHelper.getCursor(pbFunction.getProject().isUnicode(),
                pbFunction.getEntry().getVariableBuffer(), cursorOffset,
                Arrays.stream(source).map(o -> o.str).collect(Collectors.toList()));
        String into = Arrays.stream(source2).map(o -> String.format(":%s", o)).collect(Collectors.joining(","));
        cursor = Pattern.compile(" from ", Pattern.CASE_INSENSITIVE)
                .matcher(cursor)
                .replaceFirst(String.format(" into %s from ", into));
        codeLine.sCode = String.format("%s using %s;", cursor, arg);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private PbType getTypeName(long offset) {
        long uInt = BufferHelper.getUInt(pbFunction.getBuffer(), offset);
        int uShort = BufferHelper.getUShort(pbFunction.getBuffer(), offset + 4);
        // uShort2 at offset+6 is checked in C# but not used
        PbType pbType = PbType.getPbType(pbFunction.getEntry(), uShort);
        String string = BufferHelper.getString(pbFunction.getProject().isUnicode(), pbFunction.getBuffer(), uInt);
        if (!pbType.getName().equals(string)) {
            throw new RuntimeException(String.format("GetTypeName \"%s\" != \"%s\"", pbType.getName(), string));
        }
        return pbType;
    }

    private static int getOperatorLevel(String operator) {
        if (operator == null) return 0;
        switch (operator) {
            case "+":
            case "-":
                return 5;
            case "*":
            case "/":
                return 4;
            case "^":
                return 3;
            case "and":
            case "or":
                return 2;
            case "=":
            case "<>":
            case ">":
            case "<":
            case ">=":
            case "<=":
                return 1;
            case "$not":
            case "$-":
                return 6;
            default:
                return 0;
        }
    }

    private void pushVariable(PbVariable variable) {
        stackObjects.push(new StackObject(variable.getName(), variable.getType()));
    }

    private StackObject[] popStack(long count) {
        StackObject[] array = new StackObject[(int) count];
        for (int i = 0; i < count; i++) {
            array[(int) (count - 1 - i)] = stackObjects.pop();
        }
        return array;
    }
}
