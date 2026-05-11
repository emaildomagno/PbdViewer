package com.pbdviewer.utils.pbclass;

public class PbFunction {

    private final PbProject project;
    private final PbEntry entry;
    private final PbObject object;
    private int index;
    private PbFunctionDefinition definition;
    private byte[] pCodeBytes;
    private byte[] debugBytes;
    private byte[] buffer;
    private PbVariable[] variables;

    public PbFunction(PbObject object) {
        this.object = object;
        this.entry = object.getEntry();
        this.project = entry.getProject();
    }

    @Override
    public String toString() {
        String defName = definition != null ? definition.getName() : String.format("#%04X", index);
        return object + "/" + defName;
    }

    public PbProject getProject() {
        return project;
    }

    public PbEntry getEntry() {
        return entry;
    }

    public PbObject getObject() {
        return object;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public PbFunctionDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(PbFunctionDefinition definition) {
        this.definition = definition;
    }

    public byte[] getPCodeBytes() {
        return pCodeBytes;
    }

    public void setPCodeBytes(byte[] pCodeBytes) {
        this.pCodeBytes = pCodeBytes;
    }

    public byte[] getDebugBytes() {
        return debugBytes;
    }

    public void setDebugBytes(byte[] debugBytes) {
        this.debugBytes = debugBytes;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    public PbVariable[] getVariables() {
        return variables;
    }

    public void setVariables(PbVariable[] variables) {
        this.variables = variables;
    }
}
