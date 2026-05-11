// CodeArea.js — port of Java CodeArea.java (record)
export class CodeArea {
    /**
     * @param {string} type
     * @param {number} start
     * @param {number} end
     */
    constructor(type = '', start = 0, end = 0) {
        this.type  = type;
        this.start = start;
        this.end   = end;
    }
}
