// CodeLine.js — port of Java CodeLine.java
import { getHexString } from './BufferHelper.js';
import { JmpType } from './JmpType.js';

export class CodeLine {
    constructor() {
        /** @type {number} */
        this.pCodePosition  = 0;
        /** @type {number|null} */
        this.debugLine      = null;
        /** @type {number} */
        this.pCodeOp        = 0;
        /** @type {Uint8Array} */
        this.pCodeParam     = new Uint8Array(0);
        /** @type {string[]} */
        this.labelSCode     = [];
        /** @type {string} */
        this.sCode          = '';
        /** @type {number} JmpType value */
        this.jmpType        = JmpType.None;
        /** @type {number} */
        this.jmpPosition    = 0;
        /** @type {string|null} */
        this.condition      = null;
        /** @type {CodeLine|null} */
        this.nextCodeLine   = null;
        /** @type {CodeLine|null} */
        this.preCodeLine    = null;
    }

    toString() {
        // Label lines: blank-padded to 47 chars, then the label text
        const labelLines = this.labelSCode
            .map(o => `${''.padEnd(47)} ${o}\r\n`)
            .join('');

        // Debug line (4 hex digits or spaces)
        const debugLineHex = this.debugLine !== null
            ? this.debugLine.toString(16).padStart(4, '0').toUpperCase()
            : '    ';

        // Hex dump of pCodeParam (first bytes)
        const hexParam = getHexString(this.pCodeParam);

        // Compose: "DDDD PPPP:  OOOO  HH HH HH ..."
        // then right-pad to 47 chars, then sCode
        const prefix = `${debugLineHex} ${this.pCodePosition.toString(16).padStart(4,'0').toUpperCase()}:  `;
        const opHex  = this.pCodeOp.toString(16).padStart(4,'0').toUpperCase();
        let line = prefix + `${opHex}  ${hexParam}`;
        // pad to 47, then append sCode
        if (line.length < 47) {
            line = line.padEnd(47, ' ');
        }
        line = `${line.padEnd(47)} ${this.sCode}`;

        return labelLines + line;
    }
}
