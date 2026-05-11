// PbFunctionParam.js — port of Java PbFunctionParam.java
export class PbFunctionParam {
    constructor() {
        /** @type {boolean} */
        this.isReadOnly  = false;
        /** @type {boolean} */
        this.isReference = false;
        /** @type {import('./PbType.js').PbType|null} */
        this.type        = null;
        /** @type {string} */
        this.name        = '';
        /** @type {string} */
        this.arrayString = '';
    }

    toString() {
        let text = '';
        if (this.isReference) text += 'ref ';
        if (this.isReadOnly)  text += 'readonly ';
        return text + `${this.type.name} ${this.name}${this.arrayString}`;
    }
}
