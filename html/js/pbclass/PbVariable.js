// PbVariable.js — port of Java PbVariable.java
import * as BH from '../BufferHelper.js';
import { PbVariableFlag } from './PbVariableFlag.js';
import { PbType } from './PbType.js';

export class PbVariable {
    /**
     * @param {import('./PbEntry.js').PbEntry} pbEntry
     * @param {number} index
     * @param {Uint8Array} buffer     — 20-byte variable record
     * @param {Uint8Array} structBuffer — the struct / string buffer for this section
     * @param {boolean} delayParseType
     */
    constructor(pbEntry, index, buffer, structBuffer, delayParseType) {
        this.entry            = pbEntry;
        this._buffer          = buffer;
        this.index            = index;
        this.flagValue        = buffer[17] & 0xFF;
        this.precisionOrSize  = '';
        this.sqlDeclare       = null;
        this.type             = null;
        this.object           = null;

        this.accessString     = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.IsPrivate)
            ? 'private '
            : PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.IsProtected)
                ? 'protected '
                : '';
        this.isShared         = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.IsShared);
        this.isReferencedGlobal = (buffer[16] & 0x40) === 64;
        this.isInstance       = (buffer[0] & 0xF) <= 1;
        this.isIndirect       = (buffer[0] & 2) === 2;
        this.isConstant       = (buffer[0] & 4) === 4;

        if (!delayParseType) this.parseType();

        this.name             = BH.getString(pbEntry.getProject().isUnicode, structBuffer, BH.getUInt(buffer, 8));
        this.arrayString      = PbVariable.getArrayString(BH.getUInt(buffer, 4), structBuffer);
    }

    /** Copy constructor (used by inherit()) */
    static _copy(src) {
        const v = Object.create(PbVariable.prototype);
        v._buffer             = src._buffer;
        v.sqlDeclare          = src.sqlDeclare;
        v.index               = src.index;
        v.flagValue           = src.flagValue;
        v.precisionOrSize     = src.precisionOrSize;
        v.name                = src.name;
        v.arrayString         = src.arrayString;
        v.accessString        = src.accessString;
        v.isReferencedGlobal  = src.isReferencedGlobal;
        v.isShared            = src.isShared;
        v.isInstance          = src.isInstance;
        v.isIndirect          = src.isIndirect;
        v.isConstant          = src.isConstant;
        v.type                = src.type;
        v.entry               = src.entry;
        v.object              = src.object;
        return v;
    }

    /**
     * Returns a copy of this variable with type/entry/object overridden for the control.
     * @param {import('./PbObject.js').PbObject} control
     * @returns {PbVariable}
     */
    inherit(control) {
        const v = PbVariable._copy(this);
        v.type   = control.type;
        v.entry  = control.entry;
        v.object = control;
        return v;
    }

    parseType() {
        this.type = PbType.getPbType(this.entry, BH.getUShort(this._buffer, 18));
        if (this.type.isValueType) {
            if (this.type.name === 'blob') {
                const uShort = BH.getUShort(this._buffer, 12);
                this.precisionOrSize = (uShort === 0) ? '' : `{${BH.getUShort(this._buffer, 12)}}`;
            } else if (this.type.name === 'decimal') {
                const num = this._buffer[16] & 0x3F;
                this.precisionOrSize = (num === 62) ? '' : `{${(this._buffer[16] & 0xFF) >>> 1}}`;
            }
        }
    }

    /**
     * @param {number} offset
     * @param {Uint8Array} buffer
     * @returns {string}
     */
    static getArrayString(offset, buffer) {
        if (offset === 0xFFFF || offset === 0xFFFFFFFF) return '';
        let text = '[';
        const b = buffer[offset] & 0xFF;
        for (let i = 0; i < b; i++) {
            if (i !== 0) text += ',';
            const lo = BH.getUInt(buffer, offset + 4 + i * 8);
            const hi = BH.getUInt(buffer, offset + 8 + i * 8);
            if (lo === 1) {
                text += hi;
            } else if (lo !== hi || hi !== 0) {
                text += `${lo} to ${hi}`;
            }
        }
        return text + ']';
    }

    /** @returns {number} */
    get globalIndex() {
        if (!this.isShared) return 0xFFFF;
        return BH.getUShort(this._buffer, 12);
    }

    /**
     * @param {Uint8Array} valueBuffer
     * @returns {string|null}
     */
    getValue(valueBuffer) {
        if (!PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.IsCustom)) return null;
        if (!this.type.isValueType && this.type.pbEnum === null) return null;
        if (this.isIndirect || this.isReferencedGlobal) return null;

        if (PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.IsArray)) {
            if (PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid)) return null;
            const list  = this._getList(valueBuffer);
            const list2 = list.map(o => this._getValueFor(o, valueBuffer, true));
            // strip trailing nulls
            while (list2.length > 0 && list2[list2.length - 1] === null) list2.pop();
            for (let i = 0; i < list2.length; i++) {
                if (list2[i] === null) list2[i] = this._getValueFor(list[i], valueBuffer, false);
            }
            return `{${list2.join(',')}}`;
        }
        return this._getValueFor(BH.getUInt(this._buffer, 12), valueBuffer, true);
    }

    /**
     * @param {number} code
     * @param {Uint8Array} valueBuffer
     * @param {boolean} checkIsDefault
     * @returns {string|null}
     */
    _getValueFor(code, valueBuffer, checkIsDefault) {
        const project = this.entry.getProject();
        if (this.type.pbEnum !== null) {
            const key = code & 0xFFFF;
            if (!checkIsDefault || key !== 0) return this.type.pbEnum.items.get(key) ?? null;
            return null;
        }
        switch (this.type.name) {
            case 'integer': {
                const v = ((code & 0xFFFF) << 16) >> 16; // sign-extend 16-bit
                if (!checkIsDefault || v !== 0) return String(v);
                return null;
            }
            case 'uint': {
                const v = code & 0xFFFF;
                if (!checkIsDefault || v !== 0) return String(v);
                return null;
            }
            case 'long': {
                // treat code as unsigned 32-bit, then interpret as signed 32-bit
                const v = (code | 0);
                if (!checkIsDefault || code !== 0) return String(v);
                return null;
            }
            case 'ulong': {
                if (!checkIsDefault || code !== 0) return String(code >>> 0);
                return null;
            }
            case 'char': {
                const v = code & 0xFFFF;
                if (!checkIsDefault || v !== 0) return `'${String.fromCharCode(v)}'`;
                return null;
            }
            case 'byte': {
                const v = (code & 0xFF) << 24 >> 24; // sign-extend 8-bit
                if (!checkIsDefault || v !== 0) return String(v);
                return null;
            }
            case 'boolean': {
                const v = (code & 0xFF) !== 0;
                if (!checkIsDefault || (code & 0xFF) !== 0) return String(v);
                return null;
            }
            case 'real': {
                if (!checkIsDefault || code !== 0) return BH.getReal(code);
                return null;
            }
            case 'string': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                if (!checkIsDefault || (!invalid && BH.getString(project.isUnicode, valueBuffer, code) !== ''))
                    return BH.getEscapeString(project.isUnicode, valueBuffer, code);
                return null;
            }
            case 'decimal': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                const dec = BH.getDecimal(valueBuffer, code);
                if (!checkIsDefault || (!invalid && dec !== '0.0')) return dec;
                return null;
            }
            case 'double': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                const d = BH.getDouble(valueBuffer, code);
                if (!checkIsDefault || (!invalid && d !== '0')) return d;
                return null;
            }
            case 'longlong': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                const ll = BH.getLongLong(valueBuffer, code);
                if (!checkIsDefault || (!invalid && ll !== '0')) return ll;
                return null;
            }
            case 'date': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                const d = BH.getDate(valueBuffer, code);
                if (!checkIsDefault || (!invalid && d !== '1900-01-01')) return d;
                return null;
            }
            case 'time': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                const t = BH.getTime(valueBuffer, code);
                if (!checkIsDefault || (!invalid && t !== '00:00:00')) return t;
                return null;
            }
            case 'datetime': {
                const invalid = PbVariableFlag.hasFlag(this.flagValue, PbVariableFlag.Invalid);
                const dt = BH.getDateTime(valueBuffer, code);
                if (!checkIsDefault || (!invalid && dt !== 'datetime(1900-01-01,00:00:00)')) return dt;
                return null;
            }
            default:
                return null;
        }
    }

    /**
     * @param {Uint8Array} valueBuffer
     * @returns {number[]}
     */
    _getList(valueBuffer) {
        const list   = [];
        const uShort  = BH.getUShort(this._buffer, 12);
        const uShort2 = BH.getUShort(valueBuffer, uShort + 14);
        const num     = uShort + 28 + uShort2 * 8;
        const uInt    = BH.getUInt(valueBuffer, num);
        for (let i = 0; i < uInt; i++) {
            list.push(BH.getUInt(valueBuffer, num + 4 + 8 * i));
        }
        return list;
    }

    setCursorParams(paramList, sqlcaStr) {
        if (this.sqlDeclare === null) {
            const project = this.entry.getProject();
            this.sqlDeclare = BH.getCursor(project.isUnicode, this.entry.variableBuffer,
                BH.getUInt(this._buffer, 12), paramList);
            this.sqlDeclare = `declare ${this.name} cursor for ${this.sqlDeclare} using ${sqlcaStr} ;`;
        }
    }

    setDynamicCursorParams(sqlsaStr) {
        if (this.sqlDeclare === null) {
            const project = this.entry.getProject();
            this.sqlDeclare = BH.getCursor(project.isUnicode, this.entry.variableBuffer,
                BH.getUInt(this._buffer, 12), null);
            this.sqlDeclare = `declare ${this.name} dynamic cursor ${this.sqlDeclare} for ${sqlsaStr} ;`;
        }
    }

    setProcedureParams(paramList, sqlcaStr) {
        if (this.sqlDeclare === null) {
            const project = this.entry.getProject();
            this.sqlDeclare = BH.getCursor(project.isUnicode, this.entry.variableBuffer,
                BH.getUInt(this._buffer, 12), paramList).replace('execute ', '');
            this.sqlDeclare = `declare ${this.name} procedure for ${this.sqlDeclare} using ${sqlcaStr} ;`;
        }
    }

    setDynamicProcedureParams(sqlsaStr) {
        if (this.sqlDeclare === null) {
            const project = this.entry.getProject();
            this.sqlDeclare = BH.getCursor(project.isUnicode, this.entry.variableBuffer,
                BH.getUInt(this._buffer, 12), null);
            this.sqlDeclare = `declare ${this.name} dynamic procedure ${this.sqlDeclare} for ${sqlsaStr} ;`;
        }
    }

    /**
     * @param {Uint8Array|null} valueBuffer
     * @param {boolean} debug
     * @returns {string}
     */
    toDisplayString(valueBuffer, debug) {
        let text = `${this.accessString}${this.type.name}${this.precisionOrSize} ${this.name}${this.arrayString}`;
        if (this.sqlDeclare !== null) text = this.sqlDeclare;
        if (this.isConstant) text = 'constant ' + text;
        if (debug) {
            if (this.isReferencedGlobal) text = 'global ' + text;
            else if (this.isShared) text = 'shared ' + text;
            text = BH.getHexString(this._buffer) + '  ' + text;
        }
        if (valueBuffer !== null && valueBuffer !== undefined) {
            let text2 = null;
            if (!this.isReferencedGlobal) text2 = this.getValue(valueBuffer);
            if (text2 !== null) text += ` = ${text2}`;
        }
        return text;
    }

    toString() {
        return this.toDisplayString(null, false);
    }
}
