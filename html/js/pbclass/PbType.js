// PbType.js — port of Java PbType.java
// Static valueTypes map is module-level. getPbType() acts as the factory.

/** @type {Map<number, PbType>} */
const VALUE_TYPES = new Map();

export class PbType {
    /**
     * @param {import('./PbEntry.js').PbEntry|null} entry
     * @param {number} index
     * @param {string} name
     * @param {boolean} isReferencedObject
     * @param {boolean} isSystemEntry
     */
    constructor(entry, index, name, isReferencedObject, isSystemEntry) {
        this.name               = name;
        this.entry              = entry;
        this.isReferencedObject = isReferencedObject;
        this.isValueType        = false;
        this.isSystemType       = false;
        this.pbEnum             = null;
        this.object             = null;
        this._isFinded          = false;

        if (isSystemEntry) {
            if (isReferencedObject) throw new Error("system entry can't reference other object");
            this.isSystemType = true;
            this.index = 0x4000 | index;
            entry.getProject().onNewSystemType(this);
            return;
        }
        if (isReferencedObject) {
            const project = entry.getProject();
            for (const e of project.enums.values()) {
                if (e.name === name) { this.pbEnum = e; break; }
            }
        }
        this.index = 0x8000 | index;
        entry.onNewType(this);
    }

    /** Private constructor path for value types (index < 0x1000) */
    static _makeValueType(index) {
        const t = Object.create(PbType.prototype);
        t.index             = index;
        t.name              = PbType._getValueTypeName(index);
        t.isValueType       = true;
        t.isSystemType      = false;
        t.isReferencedObject = false;
        t.pbEnum            = null;
        t.object            = null;
        t.entry             = null;
        t._isFinded         = false;
        return t;
    }

    static _getValueTypeName(low) {
        switch (low) {
            case 0:  return '';
            case 1:  return 'integer';
            case 2:  return 'long';
            case 3:  return 'real';
            case 4:  return 'double';
            case 5:  return 'decimal';
            case 6:  return 'string';
            case 7:  return 'boolean';
            case 8:  return 'any';
            case 9:  return 'uint';
            case 10: return 'ulong';
            case 11: return 'blob';
            case 12: return 'date';
            case 13: return 'time';
            case 14: return 'datetime';
            case 15: return 'cursor';
            case 16: return 'procedure';
            case 18: return 'char';
            case 19: return 'objhandle';
            case 20: return 'longlong';
            case 21: return 'byte';
            default: return index.toString(16).padStart(4, '0').toUpperCase();
        }
    }

    /**
     * @param {import('./PbEntry.js').PbEntry} pbEntry
     * @param {number} index
     * @returns {PbType}
     */
    static getPbType(pbEntry, index) {
        const hi = index >> 12;
        switch (hi) {
            case 0: {
                if (!VALUE_TYPES.has(index)) VALUE_TYPES.set(index, PbType._makeValueType(index));
                return VALUE_TYPES.get(index);
            }
            case 4:
                return pbEntry.getProject().systemTypes.get(index);
            case 8:
                return pbEntry.types.get(index);
            case 12:
                return PbType._makeValueType(0);   // unknown -> ""
            default:
                throw new Error(`Unknown Type ${index.toString(16).padStart(4,'0').toUpperCase()}`);
        }
    }

    /**
     * @param {import('./PbEntry.js').PbEntry} pbEntry
     * @returns {import('./PbObject.js').PbObject|null}
     */
    getObject(pbEntry) {
        if (this.object !== null) return this.object;
        if (this.isValueType) return null;
        if (this._isFinded) return null;
        if (this.name.includes('`') || this.isSystemType || this.isReferencedObject) {
            const obj = pbEntry.getProject().objects.get(this.name);
            if (obj !== undefined) this.object = obj;
        } else {
            const effectiveEntry = (this.entry !== null) ? this.entry : pbEntry;
            const obj = effectiveEntry.objects.get(this.index);
            if (obj !== undefined) this.object = obj;
        }
        this._isFinded = true;
        return this.object;
    }
}
