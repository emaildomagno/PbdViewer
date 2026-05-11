// PbFunction.js — port of Java PbFunction.java
export class PbFunction {
    /**
     * @param {import('./PbObject.js').PbObject} object
     */
    constructor(object) {
        this.object  = object;
        this.entry   = object.entry;
        this.project = object.entry.getProject();

        /** @type {number} */
        this.index       = 0;
        /** @type {import('./PbFunctionDefinition.js').PbFunctionDefinition|null} */
        this.definition  = null;
        /** @type {Uint8Array} */
        this.pCodeBytes  = new Uint8Array(0);
        /** @type {Uint8Array} */
        this.debugBytes  = new Uint8Array(0);
        /** @type {Uint8Array} */
        this.buffer      = new Uint8Array(0);
        /** @type {import('./PbVariable.js').PbVariable[]} */
        this.variables   = [];
    }

    toString() {
        const defName = this.definition !== null
            ? this.definition.name
            : `#${this.index.toString(16).padStart(4,'0').toUpperCase()}`;
        return `${this.object}/${defName}`;
    }
}
