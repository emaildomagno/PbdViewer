package com.pbdviewer.utils.pbclass;

import com.pbdviewer.utils.BufferHelper;

public class PbReferencedFunction {

    private final byte[] buffer;
    private String name;
    private int index;
    private int globalIndex;
    private boolean isGlobalFunction;

    public PbReferencedFunction(int index, byte[] buffer) {
        this.index = index;
        this.buffer = buffer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isGlobalFunction() {
        return isGlobalFunction;
    }

    public void setGlobalFunction(boolean globalFunction) {
        isGlobalFunction = globalFunction;
    }

    public String toString(boolean isDebug) {
        String text = name;
        if (isDebug) {
            text = String.format("%s\t%04X: %s", BufferHelper.getHexString(buffer), globalIndex, text);
        }
        return text;
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
