// PbFunctionFlag.js — port of Java PbFunctionFlag.java (enum)
// Values taken directly from the Java source:
//   IsEvent(0x01), IsExternal(0x04), IsPrivate(0x10), IsProtected(0x20)
export const PbFunctionFlag = Object.freeze({
    IsEvent:     0x01,
    IsExternal:  0x04,
    IsPrivate:   0x10,
    IsProtected: 0x20,

    /**
     * @param {number} value  — the flags byte
     * @param {number} flag   — one of the constants above
     * @returns {boolean}
     */
    hasFlag(value, flag) {
        return (value & flag) === flag;
    },
});
