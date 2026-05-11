// PbVariableFlag.js — port of Java PbVariableFlag.java (enum)
// Values taken directly from the Java source:
//   IsCustom(1), IsShared(2), UShort(4), ULong(8), Invalid(12),
//   IsBuffer(16), IsArray(32), IsPrivate(64), IsProtected(128)
export const PbVariableFlag = Object.freeze({
    IsCustom:    1,
    IsShared:    2,
    UShort:      4,
    ULong:       8,
    Invalid:     12,
    IsBuffer:    16,
    IsArray:     32,
    IsPrivate:   64,
    IsProtected: 128,

    /**
     * @param {number} value  — the flags byte
     * @param {number} flag   — one of the constants above
     * @returns {boolean}
     */
    hasFlag(value, flag) {
        return (value & flag) === flag;
    },
});
