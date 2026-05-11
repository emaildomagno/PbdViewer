// PbReferencedFunction.js — port of Java PbReferencedFunction.java
import { getHexString } from '../BufferHelper.js';

export class PbReferencedFunction {
    /**
     * @param {number} index
     * @param {Uint8Array} buffer — 20-byte record
     */
    constructor(index, buffer) {
        this.index            = index;
        this._buffer          = buffer;
        this.name             = '';
        this.globalIndex      = 0;
        this.isGlobalFunction = false;
    }

    /**
     * @param {boolean} isDebug
     * @returns {string}
     */
    toString(isDebug = false) {
        let text = this.name;
        if (isDebug) {
            const hex = getHexString(this._buffer);
            text = `${hex}\t${this.globalIndex.toString(16).padStart(4,'0').toUpperCase()}: ${text}`;
        }
        return text;
    }
}
