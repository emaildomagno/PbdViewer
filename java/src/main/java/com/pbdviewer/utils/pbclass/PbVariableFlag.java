package com.pbdviewer.utils.pbclass;

public enum PbVariableFlag {
    IsCustom(1),
    IsShared(2),
    UShort(4),
    ULong(8),
    Invalid(12),
    IsBuffer(16),
    IsArray(32),
    IsPrivate(64),
    IsProtected(128);

    public final int value;

    PbVariableFlag(int value) {
        this.value = value;
    }

    public static boolean hasFlag(int flags, PbVariableFlag flag) {
        return (flags & flag.value) == flag.value;
    }
}
