// PbObject.js — port of Java PbObject.java
export class PbObject {
    /**
     * @param {import('./PbEntry.js').PbEntry} entry
     * @param {number} index
     * @param {import('./PbType.js').PbType} type
     */
    constructor(entry, index, type) {
        this.entry                  = entry;
        this.index                  = index;
        this.type                   = type;
        this.project                = entry.getProject();

        /** @type {import('./PbType.js').PbType|null} */
        this.inheritType            = null;
        /** @type {PbObject|null} */
        this.inheritObject          = null;
        /** @type {import('./PbType.js').PbType|null} */
        this.parentType             = null;
        /** @type {PbObject|null} */
        this.parentObject           = null;
        /** @type {import('./PbFunction.js').PbFunction[]} */
        this.functions              = [];
        /** @type {import('./PbVariable.js').PbVariable[]} */
        this.variables              = [];
        /** @type {import('./PbReferencedFunction.js').PbReferencedFunction[]} */
        this.referencedFunctions    = [];
        /** @type {import('./PbFunctionDefinition.js').PbFunctionDefinition[]} */
        this.functionDefinitions    = [];
        /** @type {(import('./PbVariable.js').PbVariable|null)[]} */
        this.allVariables           = [];
        /** @type {(import('./PbFunctionDefinition.js').PbFunctionDefinition|null)[]} */
        this.allFunctionDefinitions = [];
        /** @type {PbObject[]} */
        this.controls               = [];

        this._parsedInherit = false;
    }

    parseInherit() {
        if (this._parsedInherit) return;

        this.inheritObject = (this.inheritType !== null) ? this.inheritType.getObject(this.entry) : null;
        this.parentObject  = (this.parentType  !== null) ? this.parentType.getObject(this.entry)  : null;

        if (this.inheritObject !== null) {
            this.inheritObject.parseInherit();
            const num = Math.min(this.inheritObject.allVariables.length, this.allVariables.length);
            for (let i = 0; i < num; i++) this.allVariables[i] = this.inheritObject.allVariables[i];
            for (const def of this.inheritObject.allFunctionDefinitions) {
                if (def !== null && def !== undefined) {
                    this.allFunctionDefinitions[def.globalIndex] = def;
                }
            }
        }

        // Instance variables in reverse order fill from the back
        const instanceVars = this.variables.filter(v => v.isInstance);
        instanceVars.reverse();
        for (let k = 0; k < instanceVars.length; k++) {
            this.allVariables[this.allVariables.length - 1 - k] = instanceVars[k];
        }

        for (const def of this.functionDefinitions) {
            this.allFunctionDefinitions[def.globalIndex] = def;
        }

        // controls = sibling objects in same entry whose parentType === this.type
        this.controls = Array.from(this.entry.objects.values())
            .filter(o => o.parentType === this.type);

        // Match controls to allVariables
        for (let l = 0; l < this.allVariables.length; l++) {
            const variable = this.allVariables[l];
            if (variable !== null && variable !== undefined) {
                let matchingControl = null;
                for (const ctrl of this.controls) {
                    if (ctrl.type.name === variable.name) { matchingControl = ctrl; break; }
                }
                if (matchingControl !== null && matchingControl.type !== variable.type) {
                    this.allVariables[l] = variable.inherit(matchingControl);
                }
            }
        }

        this._parsedInherit = true;
    }

    toString() {
        return `${this.entry}/${this.type.name}`;
    }
}
