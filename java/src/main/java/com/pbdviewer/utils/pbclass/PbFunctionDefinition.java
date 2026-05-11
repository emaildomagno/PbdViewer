package com.pbdviewer.utils.pbclass;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PbFunctionDefinition {

    private PbObject object;
    private int index;
    private int globalIndex;
    private int refIndex;
    private int eventCode;
    private int flagValue;
    private PbType returnType;
    private String name;
    private PbFunctionParam[] params;
    private String library;
    private String alias;
    private PbType throwsType;

    public boolean isEvent() {
        return PbFunctionFlag.hasFlag(flagValue, PbFunctionFlag.IsEvent);
    }

    public boolean isExternal() {
        return PbFunctionFlag.hasFlag(flagValue, PbFunctionFlag.IsExternal);
    }

    @Override
    public String toString() {
        String text = "";
        if (!isEvent()) {
            if (PbFunctionFlag.hasFlag(flagValue, PbFunctionFlag.IsPrivate)) {
                text += "private ";
            } else if (!PbFunctionFlag.hasFlag(flagValue, PbFunctionFlag.IsProtected)) {
                text += "public ";
            } else {
                text += "protected ";
            }
            text += returnType.getIndex() != 0 ? "function " : "subroutine ";
        } else {
            text += "event ";
        }
        text += returnType.getName() + " ";
        String paramList = Arrays.stream(params).map(Object::toString).collect(Collectors.joining(","));
        text += String.format("%s(%s)", name, paramList);
        if (throwsType != null) text += String.format(" throws %s", throwsType.getName());
        if (library != null) text += String.format(" library \"%s\" alias for \"%s\"", library, alias);
        return text;
    }

    public PbObject getObject() {
        return object;
    }

    public void setObject(PbObject object) {
        this.object = object;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getGlobalIndex() {
        return globalIndex;
    }

    public void setGlobalIndex(int globalIndex) {
        this.globalIndex = globalIndex;
    }

    public int getRefIndex() {
        return refIndex;
    }

    public void setRefIndex(int refIndex) {
        this.refIndex = refIndex;
    }

    public int getEventCode() {
        return eventCode;
    }

    public void setEventCode(int eventCode) {
        this.eventCode = eventCode;
    }

    public int getFlag() {
        return flagValue;
    }

    public void setFlag(int flagValue) {
        this.flagValue = flagValue;
    }

    public PbType getReturnType() {
        return returnType;
    }

    public void setReturnType(PbType returnType) {
        this.returnType = returnType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PbFunctionParam[] getParams() {
        return params;
    }

    public void setParams(PbFunctionParam[] params) {
        this.params = params;
    }

    public String getLibrary() {
        return library;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public PbType getThrowsType() {
        return throwsType;
    }

    public void setThrowsType(PbType throwsType) {
        this.throwsType = throwsType;
    }
}
