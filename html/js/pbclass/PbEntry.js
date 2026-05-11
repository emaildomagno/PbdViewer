// PbEntry.js — port of Java PbEntry.java
import * as BH from '../BufferHelper.js';
import { PCodeHelper } from '../PCodeHelper.js';
import { PbType } from './PbType.js';
import { PbObject } from './PbObject.js';
import { PbFunction } from './PbFunction.js';
import { PbVariable } from './PbVariable.js';
import { PbFunctionDefinition } from './PbFunctionDefinition.js';
import { PbFunctionParam } from './PbFunctionParam.js';
import { PbReferencedFunction } from './PbReferencedFunction.js';

export class PbEntry {
    /**
     * @param {import('./PbFile.js').PbFile} file
     * @param {string} entryName
     * @param {Uint8Array} entryData
     */
    constructor(file, entryName, entryData) {
        this._entryData  = entryData;
        this._isParsed   = false;
        this._flag       = 0;
        this._indent     = 0;
        this._dataBuffer = null;
        this._position   = 0;
        this._sb         = [];
        this._isDebug    = true;

        this.file      = file;
        this.entryName = entryName;
        this.project   = file.project;

        const dotIdx = entryName.lastIndexOf('.');
        this.name   = (dotIdx >= 0) ? entryName.slice(0, dotIdx) : entryName;
        this.suffix = (dotIdx >= 0) ? entryName.slice(dotIdx + 1) : '';

        /** @type {Map<number, PbType>} */
        this.types   = new Map();
        /** @type {Map<number, PbObject>} */
        this.objects = new Map();

        /** @type {Uint8Array} */
        this.variableBuffer = new Uint8Array(0);
        /** @type {Uint8Array} */
        this.functionBuffer = new Uint8Array(0);
        /** @type {Uint8Array} */
        this.paramBuffer    = new Uint8Array(0);

        /** @type {PbVariable[]} */
        this.variables  = [];
        /** @type {PbObject|null} */
        this.entryObject = null;
        /** @type {string|null} */
        this.source     = null;
        /** @type {Date|null} */
        this.modifiedTime  = null;
        /** @type {Date|null} */
        this.compiledTime  = null;

        switch (this.suffix) {
            case 'ico':
            case 'jpg':
            case 'png':
            case 'bmp':
                this._isParsed = true;
                break;
            case 'exe':
                this._parseExe();
                this._isParsed = true;
                break;
            case 'srj':
                this._parseSrj();
                this._isParsed = true;
                break;
            case 'grp':
                this.project.onSystemEntry(this);
                this.parseObject(true);
                this._isParsed = true;
                break;
            case 'apl':
            case 'str':
            case 'fun':
            case 'win':
            case 'men':
            case 'udo':
                this.project.onSystemLibrary(BH.getUShort(entryData, 0));
                break;
            case 'dwo':
                this.source = 'DataWindow可以通过PB接口函数导出';
                this._isParsed = true;
                break;
            default:
                this.source = this.project.getString(entryData);
                this._isParsed = true;
                break;
        }
    }

    getProject() { return this.project; }

    // ── Public parse methods ────────────────────────────────────────────────

    parseObject(isSystem = false) {
        if (this._isParsed) return;
        this._sb       = [];
        this._dataBuffer = this._entryData;
        this._position  = 0;

        this._printString(`Pdb Version: ${this._readUShort().toString(16).toUpperCase()}`);
        this._flag = this._readUShort();
        this._printString(`Flag: ${this._flag.toString(16).toUpperCase()}`);
        const num  = this._readUInt();
        this._printString(`EntryType: ${num.toString(16).toUpperCase()}`);
        const num2 = this._readUInt();
        this._printString(`Unkown: ${num2.toString(16).toUpperCase()}`);

        this.modifiedTime = new Date(this._readUInt() * 1000);
        if (this.project.version >= 334) this._readUInt();
        this._printString(`Last Modify Time: ${this.modifiedTime}`);

        this.compiledTime = new Date(this._readUInt() * 1000);
        if (this.project.version >= 334) this._readUInt();
        this._printString(`Last Complied Time: ${this.compiledTime}`);

        const num3 = this._readUInt();
        this._printString(`Unkown: ${num3.toString(16).toUpperCase()}`);

        const num4  = this._readUShort();
        const array = [];
        for (let i = 0; i < num4; i++) array.push(this._readBuffer(12));

        this.variableBuffer = this._readStructBuffer();
        this.variables      = this._readVariables(true);

        const num5 = this._readUShort();
        const num6 = this._readUShort();

        this.functionBuffer = this._readStructBuffer();
        this.paramBuffer    = this._readStructBuffer();

        this._withIndent('Types', () => {
            this._readTypes(isSystem);
        });

        for (const variable of this.variables) {
            variable.parseType();
        }

        this._withIndent('Globel and Shared Variables', () => {
            for (let k = 0; k < this.variables.length; k++) {
                this._printString(`${k.toString(16).padStart(2,'0').toUpperCase()}:  ` +
                    this.variables[k].toDisplayString(this.variableBuffer, this._isDebug));
            }
        });

        const array2 = this._readVariables();
        this._withIndent('Enums', () => {
            for (let l = 0; l < array2.length; l++) {
                this._printString(`${l.toString(16).padStart(2,'0').toUpperCase()}:  ` +
                    array2[l].toDisplayString(null, this._isDebug));
                this.project.onNewEnumItem(array2[l].type,
                    BH.getUShort(array2[l]._buffer, 12),
                    array2[l].name);
            }
        });

        const size   = this.project.isPb5 ? 8 : 16;
        const array3 = [];
        for (let m = 0; m < num5; m++) array3.push(this._readBuffer(size));
        const array4 = [];
        for (let n = 0; n < num6; n++) array4.push(this._readBuffer(32));

        let num7 = 0;
        this._withIndent(`Objects: ${num5}`, () => {
            for (let num8 = 0; num8 < num5; num8++) {
                const array5 = array3[num8];
                const pbType = PbType.getPbType(this, BH.getUShort(array5, 2));
                const pbObject = new PbObject(this, num8, pbType);
                const num9 = (array5[0] >> 1) & 7;

                if (num9 === 0) {
                    const array6 = array4[num7++];
                    pbObject.inheritType = PbType.getPbType(this, BH.getUShort(array6, 0));
                    pbObject.parentType  = PbType.getPbType(this, BH.getUShort(array6, 2));
                    this.objects.set(pbObject.type.index, pbObject);
                    if (isSystem) {
                        this.project.onNewObject(pbObject);
                    } else if (pbObject.type.name === this.name) {
                        this.entryObject = pbObject;
                        this.project.onNewObject(pbObject);
                    } else if (!pbObject.type.name.includes('`')) {
                        this.project.onNewObject(pbObject, this.name + '`' + pbObject.type.name);
                    }
                    this._withIndent(
                        `Object[${num8}] ${pbType.name}:${pbObject.inheritType.name}`,
                        () => {
                            this._printBuffer(array5, 16);
                            this._printBuffer(array6, array6.length);
                            this._readObject(pbObject, array6);
                        }
                    );
                } else {
                    this._withIndent(`Object[${num8}] ${pbType.name}`, () => {
                        this._printBuffer(array5, 16);
                        if (num9 === 1) {
                            const uShort  = BH.getUShort(array5, 4);
                            const array7  = [];
                            for (let num10 = 0; num10 < uShort; num10++) {
                                const b7 = this._readBuffer(8);
                                array7.push(b7);
                                this._printBuffer(b7, 16);
                            }
                        }
                        // num9 === 6: no-op
                    });
                }
            }
        });

        if (this.project.isDebug) {
            this.source = this._sb.join('');
        }
        this._sb = [];

        if (this._position !== this._dataBuffer.length) {
            throw new Error('读取错误');
        }
        this._isParsed = true;
    }

    parseInherit() {
        for (const obj of this.objects.values()) {
            obj.parseInherit();
        }
    }

    onNewType(pbType) {
        this.types.set(pbType.index, pbType);
    }

    toString() {
        return `${this.file.fileName}/${this.entryName}`;
    }

    // ── Private read helpers ────────────────────────────────────────────────

    _withIndent(name, fn) {
        this._printString(name + ': {');
        this._indent++;
        fn();
        this._indent--;
        this._printString('}');
    }

    _readObject(pbObject, buffer) {
        const num = this._readUShort();
        pbObject.functions = new Array(num);

        const funcHeaders = [];
        for (let i = 0; i < num; i++) funcHeaders.push(this._readBuffer(4));

        this._withIndent(`Functions: ${num}`, () => {
            for (let j = 0; j < num; j++) {
                pbObject.functions[j] = new PbFunction(pbObject);
                this._readFunction(pbObject.functions[j], funcHeaders[j]);
            }
        });

        const numA = BH.getUShort(buffer, 24);
        this._readBuffer(6 * numA);
        const numB = BH.getUShort(buffer, 22);
        this._readBuffer(4 * numB);

        pbObject.referencedFunctions = this._readReferencedFunctions();
        this._withIndent(
            `Referenced Functions And Events: ${pbObject.referencedFunctions.length}`,
            () => {
                for (let k = 0; k < pbObject.referencedFunctions.length; k++) {
                    this._printString(`${k.toString(16).padStart(2,'0').toUpperCase()}:  ` +
                        pbObject.referencedFunctions[k].toString(this._isDebug));
                }
            }
        );

        pbObject.variables = this._readVariables();
        this._withIndent(
            `Properties Or Controls: ${pbObject.variables.length}`,
            () => {
                for (let l = 0; l < pbObject.variables.length; l++) {
                    pbObject.variables[l].object = pbObject;
                    this._printString(`${l.toString(16).padStart(2,'0').toUpperCase()}:  ` +
                        pbObject.variables[l].toDisplayString(this.variableBuffer, this._isDebug));
                }
            }
        );

        const uShort = BH.getUShort(buffer, 28);
        this._readBuffer(8 * uShort);
        pbObject.allVariables = new Array(uShort).fill(null);

        const num2 = this.project.isPb5 ? 12 : 16;
        const numC = BH.getUShort(buffer, 26);
        const buff = this._readBuffer(num2 * numC);
        this._printBuffer(buff, num2);

        const numD = BH.getUShort(buffer, 4);
        const num3 = (this.project.version > 146) ? 48 : (this.project.isPb5 ? 32 : 44);
        pbObject.functionDefinitions    = new Array(numD);
        pbObject.allFunctionDefinitions = new Array(BH.getUShort(buffer, 16)).fill(null);

        this._withIndent('Events And Functions', () => {
            for (let num4 = 0; num4 < numD; num4++) {
                const array2 = this._readBuffer(num3);
                this._printBuffer(array2, num3);

                const pbFuncDef = new PbFunctionDefinition();
                pbObject.functionDefinitions[num4] = pbFuncDef;
                pbFuncDef.object = pbObject;
                pbFuncDef.index  = num4;

                const flagByte = this.project.isPb5 ? 27 : 31;
                pbFuncDef.flagValue = array2[flagByte] & 0xFF;

                const retTypeOffset = this.project.isPb5 ? 24 : 28;
                pbFuncDef.returnType = PbType.getPbType(this, BH.getUShort(array2, retTypeOffset));

                let defName = BH.getString(this.project.isUnicode, this.functionBuffer,
                    BH.getUInt(array2, 0));
                if (defName.startsWith('+')) defName = defName.slice(1);
                pbFuncDef.name = defName;

                const globalIdxOffset = this.project.isPb5 ? 16 : 20;
                pbFuncDef.globalIndex = BH.getUShort(array2, globalIdxOffset);

                const refIdxOffset = this.project.isPb5 ? 18 : 22;
                pbFuncDef.refIndex = BH.getUShort(array2, refIdxOffset);

                const eventCodeOffset = this.project.isPb5 ? 28 : 32;
                pbFuncDef.eventCode = BH.getUShort(array2, eventCodeOffset);

                pbFuncDef.params = [];

                const uInt = BH.getUInt(array2, this.project.isPb5 ? 4 : 8);
                if (uInt !== 0xFFFFFFFF) {
                    const paramCountByte = this.project.isPb5 ? 26 : 30;
                    const b = array2[paramCountByte] & 0xFF;
                    pbFuncDef.params = new Array(b);
                    for (let m = 0; m < b; m++) {
                        const param   = new PbFunctionParam();
                        pbFuncDef.params[m] = param;
                        const buffer2 = BH.getBuffer(this.paramBuffer, uInt + m * 12, 12);
                        if ((buffer2[10] & 4) === 4) {
                            param.isReadOnly  = true;
                        } else if ((buffer2[10] & 2) === 2) {
                            param.isReference = true;
                        }
                        param.type        = PbType.getPbType(this, BH.getUShort(buffer2, 8));
                        param.name        = BH.getString(this.project.isUnicode, this.functionBuffer,
                            BH.getUInt(buffer2, 0));
                        param.arrayString = PbVariable.getArrayString(
                            BH.getUInt(buffer2, 4), this.functionBuffer);
                    }
                }

                const uInt2 = BH.getUInt(array2, this.project.isPb5 ? 8 : 12);
                if (uInt2 !== 0xFFFFFFFF) {
                    const uInt3 = BH.getUInt(array2, this.project.isPb5 ? 12 : 16);
                    pbFuncDef.library = BH.getString(this.project.isUnicode, this.functionBuffer, uInt3);
                    pbFuncDef.alias   = BH.getString(this.project.isUnicode, this.functionBuffer, uInt2);
                }

                if (this.project.version > 146) {
                    const uShort2 = BH.getUShort(array2, 44);
                    if (uShort2 !== 0xFFFF) {
                        pbFuncDef.throwsType = PbType.getPbType(this,
                            BH.getUShort(this.functionBuffer, uShort2));
                    }
                }

                this._printString(pbFuncDef.toString());
            }
        });
    }

    _readFunction(pbFunction, indexBytes) {
        const indexHex = Array.from(indexBytes)
            .map(b => (b & 0xFF).toString(16).padStart(4,'0').toUpperCase())
            .join(' ');

        this._withIndent(`Function :${indexHex}`, () => {
            pbFunction.index = BH.getUShort(indexBytes, 2);
            const num  = this._readUShort();
            const num2 = this._readUShort();
            this._printString(
                `${num.toString(16).padStart(4,'0').toUpperCase()} ` +
                `${num2.toString(16).padStart(4,'0').toUpperCase()} ` +
                `${this._readUShort().toString(16).padStart(4,'0').toUpperCase()}`
            );
            pbFunction.pCodeBytes = this._readBuffer(num);
            pbFunction.debugBytes = this._readBuffer(num2 * 4);

            this._withIndent('PCodes', () => {
                for (const item of PCodeHelper.parsePCode(pbFunction, false)) {
                    for (const line of item.split(/[\r\n]+/)) {
                        if (line !== '') this._printString(line);
                    }
                }
            });

            pbFunction.variables = this._readVariables();
            pbFunction.buffer    = this._readStructBuffer();

            this._withIndent('Stack', () => {
                this._printBuffer(pbFunction.buffer, 16);
            });

            this._withIndent(`Variable ${pbFunction.variables.length}`, () => {
                for (let i = 0; i < pbFunction.variables.length; i++) {
                    pbFunction.variables[i].object = pbFunction.object;
                    this._printString(`${i.toString(16).padStart(4,'0').toUpperCase()}: ` +
                        pbFunction.variables[i].toDisplayString(pbFunction.buffer, this._isDebug));
                }
            });
        });
    }

    _readUShort() {
        const v = BH.getUShort(this._dataBuffer, this._position);
        this._position += 2;
        return v;
    }

    _readUInt() {
        const v = BH.getUInt(this._dataBuffer, this._position);
        this._position += 4;
        return v;
    }

    _readBuffer(size) {
        const slice = BH.getBuffer(this._dataBuffer, this._position, size);
        this._position += size;
        return slice;
    }

    _readStructBuffer() {
        const size  = this._readUInt();
        const size2 = this._readUInt();
        const result = this._readBuffer(size);
        this._readBuffer(size2);
        return result;
    }

    _readTypes(isSystemEntry) {
        this._readBuffer(6);
        const buffer = this._readStructBuffer();
        const num = this._readUShort() / 20 | 0;
        const array = new Array(num);
        for (let num2 = 0; num2 < num; num2++) {
            const array2 = this._readBuffer(20);
            array[num2] = new PbType(this, num2,
                BH.getString(this.project.isUnicode, buffer, BH.getUInt(array2, 8)),
                array2[16] === 64,
                isSystemEntry);
            this._printString(
                `${num2.toString(16).padStart(4,'0').toUpperCase()} ` +
                `${BH.getHexString(array2)} ${array[num2].name}`
            );
        }
        return array;
    }

    _readVariables(delayParseType = false) {
        this._readBuffer(6);
        const structBuffer = this._readStructBuffer();
        const num = this._readUShort() / 20 | 0;
        const array = new Array(num);
        for (let num2 = 0; num2 < num; num2++) {
            const buffer = this._readBuffer(20);
            array[num2] = new PbVariable(this, num2, buffer, structBuffer, delayParseType);
        }
        return array;
    }

    _readReferencedFunctions() {
        this._readBuffer(6);
        const buffer = this._readStructBuffer();
        const num = this._readUShort() / 20 | 0;
        const array = new Array(num);
        for (let num2 = 0; num2 < num; num2++) {
            const array2 = this._readBuffer(20);
            const rf = new PbReferencedFunction(num2, array2);
            rf.name          = BH.getString(this.project.isUnicode, buffer, BH.getUInt(array2, 8));
            rf.globalIndex   = BH.getUShort(array2, 12);
            rf.isGlobalFunction = array2[16] === 2;
            array[num2] = rf;
        }
        return array;
    }

    // ── Print helpers ────────────────────────────────────────────────────────

    _printString(str) {
        this._sb.push('\t'.repeat(this._indent));
        this._sb.push(str);
        this._sb.push('\r\n');
    }

    _printBuffer(buff, step) {
        if (step <= 0) return;
        const num = (buff.length / step) | 0;
        for (let i = 0; i < num; i++) {
            this._printString(
                `${i.toString(16).padStart(4,'0').toUpperCase()}:` +
                `${(i * step).toString(16).padStart(4,'0').toUpperCase()}   ` +
                BH.getHexString(buff, i * step, step)
            );
        }
        const rem = buff.length - num * step;
        if (rem > 0) {
            this._printString(
                `${num.toString(16).padStart(4,'0').toUpperCase()}:` +
                `${(num * step).toString(16).padStart(4,'0').toUpperCase()}   ` +
                BH.getHexString(buff, num * step, rem)
            );
        }
    }

    // ── parseExe / parseSrj ──────────────────────────────────────────────────

    _parseSrj() {
        this.source = this.project.getString(this._entryData);
        const lines = this.source.split(/[\r\n]+/);
        for (const text of lines) {
            if (text.startsWith('PBD:')) {
                this.project.onNewLibrary(text.slice(4).split(',')[0], true);
            }
        }
    }

    _parseExe() {
        const list  = [];   // library paths
        const list2 = [];   // entry names
        const data  = this._entryData;

        if (this.project.isUnicode) {
            let num = 0;
            let num2 = ((data[num + 1] & 0xFF) << 8) | (data[num] & 0xFF);
            num += 2;
            // skip first block
            while (num2 > 0) {
                while (data[num] !== 0 || data[num + 1] !== 0) num += 2;
                num += 2;
                num2--;
            }
            // read library list
            num2 = ((data[num + 1] & 0xFF) << 8) | (data[num] & 0xFF);
            num += 2;
            let num3 = num;
            while (num2 > 0) {
                while (data[num] !== 0 || data[num + 1] !== 0) num += 2;
                list.push(new TextDecoder('utf-16le').decode(data.slice(num3, num)));
                num += 2;
                num3 = num;
                num2--;
            }
            // read entry list
            num2 = ((data[num + 1] & 0xFF) << 8) | (data[num] & 0xFF);
            num += 2;
            num3 = num;
            while (num2 > 0) {
                while (data[num] !== 0 || data[num + 1] !== 0) num += 2;
                list2.push(new TextDecoder('utf-16le').decode(data.slice(num3, num)));
                num += 2;
                num3 = num;
                num2--;
            }
        } else {
            let j, num4;
            if (this.project.isPb5) {
                j    = 0;
                num4 = 1;
            } else {
                j    = 1;
                num4 = data[0] & 0xFF;
            }
            while (num4 > 0) {
                while (data[j] !== 0) j++;
                j++;
                num4--;
            }
            num4 = ((data[j + 1] & 0xFF) << 8) | (data[j] & 0xFF);
            j += 2;
            let num5 = j;
            while (num4 > 0) {
                while (data[j] !== 0) j++;
                list.push(new TextDecoder('latin1').decode(data.slice(num5, j)));
                j++;
                num5 = j;
                num4--;
            }
            num4 = ((data[j + 1] & 0xFF) << 8) | (data[j] & 0xFF);
            j += 2;
            num5 = j;
            while (num4 > 0) {
                while (data[j] !== 0) j++;
                list2.push(new TextDecoder('latin1').decode(data.slice(num5, j)));
                j++;
                num5 = j;
                num4--;
            }
        }

        for (const item of list) {
            this.project.onNewLibrary(item, false);
        }

        const parts = [];
        parts.push(`Libraries ${list.length.toString(16).padStart(4,'0').toUpperCase()}:\r\n\t`);
        parts.push(list.map((s, i) =>
            `${i.toString(16).padStart(4,'0').toUpperCase()}:\t${s}`).join('\r\n\t'));
        parts.push(`\r\nEntries ${list2.length.toString(16).padStart(4,'0').toUpperCase()}:\r\n\t`);
        parts.push(list2.map((s, i) =>
            `${i.toString(16).padStart(4,'0').toUpperCase()}:\t${s}`).join('\r\n\t'));
        this.source = parts.join('');
    }
}
