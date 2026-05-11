package com.pbdviewer.utils.pbclass;

public enum PbFunctionFlag {
    IsEvent(0x01),
    IsExternal(0x04),
    IsPrivate(0x10),
    IsProtected(0x20);

    public final int value;

    PbFunctionFlag(int value) {
        this.value = value;
    }

    public static boolean hasFlag(int flags, PbFunctionFlag flag) {
        return (flags & flag.value) == flag.value;
    }
}
