// PbFunctionDefinition.js — port of Java PbFunctionDefinition.java
import { PbFunctionFlag } from './PbFunctionFlag.js';

export class PbFunctionDefinition {
    constructor() {
        /** @type {import('./PbObject.js').PbObject|null} */
        this.object      = null;
        /** @type {number} */
        this.index       = 0;
        /** @type {number} */
        this.globalIndex = 0;
        /** @type {number} */
        this.refIndex    = 0;
        /** @type {number} */
        this.eventCode   = 0;
        /** @type {number} */
        this.flagValue   = 0;
        /** @type {import('./PbType.js').PbType|null} */
        this.returnType  = null;
        /** @type {string} */
        this.name        = '';
        /** @type {import('./PbFunctionParam.js').PbFunctionParam[]} */
        this.params      = [];
        /** @type {string|null} */
        this.library     = null;
        /** @type {string|null} */
        this.alias       = null;
        /** @type {import('./PbType.js').PbType|null} */
        this.throwsType  = null;
    }

    isEvent()    { return PbFunctionFlag.hasFlag(this.flagValue, PbFunctionFlag.IsEvent); }
    isExternal() { return PbFunctionFlag.hasFlag(this.flagValue, PbFunctionFlag.IsExternal); }

    toString() {
        let text = '';
        if (!this.isEvent()) {
            if (PbFunctionFlag.hasFlag(this.flagValue, PbFunctionFlag.IsPrivate)) {
                text += 'private ';
            } else if (!PbFunctionFlag.hasFlag(this.flagValue, PbFunctionFlag.IsProtected)) {
                text += 'public ';
            } else {
                text += 'protected ';
            }
            text += (this.returnType.index !== 0) ? 'function ' : 'subroutine ';
        } else {
            text += 'event ';
        }
        text += this.returnType.name + ' ';
        const paramList = this.params.map(p => p.toString()).join(',');
        text += `${this.name}(${paramList})`;
        if (this.throwsType !== null)  text += ` throws ${this.throwsType.name}`;
        if (this.library   !== null)   text += ` library "${this.library}" alias for "${this.alias}"`;
        return text;
    }
}
