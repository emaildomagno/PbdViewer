// PCodeParserBase.js — port of Java PCodeParserBase.java
import * as BH from '../BufferHelper.js';
import { JmpType } from '../JmpType.js';
import { PbType } from '../pbclass/PbType.js';

class StackObject {
    /**
     * @param {string} str
     * @param {import('../pbclass/PbType.js').PbType|null} [type]
     */
    constructor(str, type = null) {
        this.str      = str;
        this.type     = type;
        this.operator = null;
    }

    toString() { return this.str; }
}

export class PCodeParserBase {
    /**
     * @param {import('../pbclass/PbFunction.js').PbFunction} pbFunction
     */
    constructor(pbFunction) {
        this.pbFunction    = pbFunction;
        this._stackObjects = [];   // acts as a stack (push/pop from end)
        this._codeLine     = null;
    }

    // ── Abstract interface ────────────────────────────────────────────────────

    /** @returns {number[]} — subclass must override */
    getPCodeLenArray() { throw new Error('Abstract'); }

    /**
     * @param {number} pCodeOp
     * @param {import('../CodeLine.js').CodeLine} codeLine
     * @returns {boolean}
     */
    onParsePcode(pCodeOp, codeLine) { throw new Error('Abstract'); }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @param {import('../CodeLine.js').CodeLine} codeLine */
    parsePCode(codeLine) {
        this._codeLine  = codeLine;
        codeLine.sCode  = '';
        if (!this.onParsePcode(codeLine.pCodeOp, codeLine)) {
            codeLine.sCode = `-------${codeLine.pCodeOp.toString(16).padStart(4,'0').toUpperCase()}`;
        }
    }

    /** @param {number} pcode @returns {number} */
    getPCodeLen(pcode) {
        return this.onGetPCodeLen(pcode);
    }

    /** @param {number} pcode @returns {number} */
    onGetPCodeLen(pcode) {
        const arr = this.getPCodeLenArray();
        if (pcode < arr.length) return arr[pcode];
        return 255;
    }

    // ── Variable assignment beginners ─────────────────────────────────────────

    beginAssignLocalVariable(index) {
        this._pushVariable(this.pbFunction.variables[index]);
    }

    beginAssignSharedVariable(index) {
        this._pushVariable(this.pbFunction.entry.variables[index]);
    }

    beginAssignGlobalVariable(index) {
        const found = this.pbFunction.variables.find(v => v.globalIndex === index) ?? null;
        this._pushVariable(found);
    }

    beginAssignInstanceVariable() {
        const so  = this._pop();
        const so2 = this._pop();
        if (so2.str === 'entryobject') {
            this._push(new StackObject(so.str, so.type));
        } else {
            this._push(new StackObject(`${so2.str}.${so.str}`, so.type));
        }
    }

    // ── End assign ────────────────────────────────────────────────────────────

    endAssign(isArrayOrOp) {
        if (isArrayOrOp === true) {
            // array assign
            const so  = this._pop();
            const so2 = this._pop();
            this._codeLine.sCode = `${so2.str}[] = ${so.str}`;
        } else if (typeof isArrayOrOp === 'string') {
            // compound assign
            const so  = this._pop();
            const so2 = this._pop();
            this._codeLine.sCode = `${so2.str} ${isArrayOrOp}= ${so.str}`;
        } else {
            // plain assign
            const so  = this._pop();
            const so2 = this._pop();
            this._codeLine.sCode = `${so2.str} = ${so.str}`;
        }
    }

    endAssign2(operator) {
        const so = this._pop();
        this._codeLine.sCode = `${so.str} ${operator}`;
    }

    resetAssign(count) {
        const item  = this._pop();
        const item2 = this._peek();
        this._push(item2);
        this._push(item);
    }

    // ── Push variable helpers ─────────────────────────────────────────────────

    pushLocalVariable(index) {
        this._pushVariable(this.pbFunction.variables[index]);
    }

    pushSharedVariable(index) {
        this._pushVariable(this.pbFunction.entry.variables[index]);
    }

    pushGlobalSharedVariable(index) {
        const found = this.pbFunction.entry.variables.find(v => v.globalIndex === index) ?? null;
        this._pushVariable(found);
    }

    pushGlobalVariable(index) {
        const found = this.pbFunction.variables.find(v => v.globalIndex === index) ?? null;
        this._pushVariable(found);
    }

    pushInstanceVariable(unknown) {
        const so  = this._pop();
        const so2 = this._pop();
        if (so2.str === 'entryobject') {
            this._push(new StackObject(so.str, so.type));
        } else {
            this._push(new StackObject(`${so2.str}.${so.str}`, so.type));
        }
    }

    pushInstanceVariableName(offset) {
        const uInt = offset >>> 0;
        if (uInt >= 1 && uInt <= 7) {
            this._push(new StackObject('entryobject', this.pbFunction.entry.entryObject.type));
            return;
        }
        const nameUInt = BH.getUInt(this.pbFunction.buffer, uInt);
        const topType  = this._peek().type;
        let pbVariable = null;
        if (topType !== null) {
            const obj = topType.getObject(this.pbFunction.entry);
            if (obj !== null) {
                const allVars = obj.allVariables;
                const varIdx  = BH.getUShort(this.pbFunction.buffer, uInt + 4);
                if (varIdx < allVars.length) pbVariable = allVars[varIdx];
            }
        }
        let text;
        if (pbVariable === null) {
            if ((nameUInt & 0xFFFF) === 0xFFFF) {
                text = BH.getUShort(this.pbFunction.buffer, uInt + 4).toString(16).padStart(4,'0').toUpperCase();
            } else {
                text = BH.getString(this.pbFunction.project.isUnicode, this.pbFunction.buffer, nameUInt);
            }
        } else if ((nameUInt & 0xFFFF) !== 0xFFFF) {
            text = BH.getString(this.pbFunction.project.isUnicode, this.pbFunction.buffer, nameUInt);
            if (pbVariable.name.toLowerCase() !== text.toLowerCase()) {
                throw new Error(`PushInstanceVariableName "${pbVariable.name}" != "${text}"`);
            }
        } else {
            text = pbVariable.name;
        }
        this._push(new StackObject(text, pbVariable !== null ? pbVariable.type : null));
    }

    pushConstant(constant) {
        this._push(new StackObject(String(constant)));
    }

    pushThis() {
        this._push(new StackObject('this', this.pbFunction.object.type));
    }

    pushParent() {
        const parentObject = this.pbFunction.object.parentObject;
        this._push(new StackObject('parent', parentObject !== null ? parentObject.type : null));
    }

    pushEnum(enumIndex, itemIndex) {
        let pbEnum = null;
        for (const e of this.pbFunction.project.enums.values()) {
            if (e.index === enumIndex) { pbEnum = e; break; }
        }
        const item = pbEnum !== null
            ? (pbEnum.items.get(itemIndex) ?? `${enumIndex.toString(16).padStart(4,'0').toUpperCase()}!${itemIndex.toString(16).padStart(4,'0').toUpperCase()}`)
            : `${enumIndex.toString(16).padStart(4,'0').toUpperCase()}!${itemIndex.toString(16).padStart(4,'0').toUpperCase()}`;
        this._push(new StackObject(item, PbType.getPbType(this.pbFunction.entry, enumIndex)));
    }

    // ── Stack operations ──────────────────────────────────────────────────────

    operateStack(op) {
        const opLevel = PCodeParserBase._getOperatorLevel(op);
        const so  = this._pop();
        const opLevel2 = PCodeParserBase._getOperatorLevel(so.operator);
        if (opLevel2 >= opLevel) so.str = `(${so.str})`;
        const so2 = this._pop();
        if (PCodeParserBase._getOperatorLevel(so2.operator) > opLevel) so2.str = `(${so2.str})`;
        const str = `${so2.str} ${op} ${so.str}`;
        const result = new StackObject(str);
        result.operator = op;
        this._push(result);
    }

    operateStackSingle(op) {
        const so = this._pop();
        if (PCodeParserBase._getOperatorLevel(so.operator) > 0) so.str = `(${so.str})`;
        const str = `${op} ${so.str}`;
        const result = new StackObject(str);
        result.operator = '$' + op;
        this._push(result);
    }

    // ── Control flow ──────────────────────────────────────────────────────────

    doReturn(p1) {
        this._codeLine.sCode = (p1 === 1)
            ? `return ${this._pop().str}`
            : 'return';
    }

    halt(force) {
        this._codeLine.sCode = (force === 0) ? 'halt close' : 'halt';
    }

    jump(pos, jmpType) {
        this._codeLine.jmpType    = jmpType;
        this._codeLine.jmpPosition = pos;
        switch (jmpType) {
            case JmpType.Jmp:
                this._codeLine.sCode = `goto ${pos.toString(16).padStart(4,'0').toUpperCase()}`;
                break;
            case JmpType.JmpIfTrue:
                this._codeLine.condition = this._pop().str;
                this._codeLine.sCode = `if ${this._codeLine.condition} then goto ${pos.toString(16).padStart(4,'0').toUpperCase()}`;
                break;
            case JmpType.JmpIfFalse:
                this._codeLine.condition = this._pop().str;
                this._codeLine.sCode = `if ${this._codeLine.condition} then not goto ${pos.toString(16).padStart(4,'0').toUpperCase()}`;
                break;
        }
    }

    // ── Exception handling ────────────────────────────────────────────────────

    tryBlock(catchpos, endpos) {
        this._codeLine.sCode = 'try ';
    }

    endTry() {
        this._codeLine.sCode = 'end try ';
    }

    doCatch() {
        const so = this._pop();
        this._push(new StackObject(`catch (${so.type.name} ${so.str})`));
    }

    doThrow() {
        const arg = this._pop();
        this._codeLine.sCode = `throw ${arg}`;
    }

    enterFinally(finallypos) {
        this._codeLine.sCode     = 'enter finally ';
        this._codeLine.jmpPosition = finallypos;
    }

    leaveFinally() { /* no-op */ }

    // ── Object creation / destruction ─────────────────────────────────────────

    createObject(offset) {
        const t = this._getTypeName(offset);
        this._push(new StackObject(`create ${t.name}`, t));
    }

    createObjectUsingName(index) {
        const inner = this._pop();
        this._push(new StackObject(`create using ${inner}`,
            PbType.getPbType(this.pbFunction.entry, 8)));  // type 8 = 'any'
    }

    destroyObject() {
        const so = this._pop();
        this._codeLine.sCode = `destroy(${so.str})`;
    }

    // ── Function calls ────────────────────────────────────────────────────────

    popFunction() {
        this._codeLine.sCode = this._pop().str;
    }

    pushGlobalFunctionName(objIndex, functionIndex) {
        let str = null;
        if ((objIndex & 0x8000) === 0x8000) {
            str = this.pbFunction.object.referencedFunctions[functionIndex].name;
        } else if ((objIndex & 0x4000) === 0x4000) {
            const sysEntry = this.pbFunction.project.systemEntry;
            let pbFuncDef = null;
            if (sysEntry !== null) {
                const pbObj = sysEntry.objects.get(objIndex);
                if (pbObj && functionIndex < pbObj.functionDefinitions.length) {
                    pbFuncDef = pbObj.functionDefinitions[functionIndex];
                }
            }
            str = pbFuncDef === null
                ? `(${objIndex.toString(16).padStart(4,'0').toUpperCase()}${functionIndex.toString(16).padStart(4,'0').toUpperCase()})`
                : pbFuncDef.name;
        }
        this._push(new StackObject(str));
    }

    callGlobalFunction(count, type) {
        const text0 = this._pop().str;
        const source = this._popStack(count);
        let text = text0;
        if (type & 1) text = 'post '    + text;
        if (type & 2) text = 'dynamic ' + text;
        if (type & 4) text = 'event '   + text;
        const params = source.map(o => o.str).join(',');
        this._push(new StackObject(`${text}(${params})`));
    }

    callSuper(functionIndex, paramcount, objType, nameoffset) {
        for (let i = 0; i < paramcount; i++) this._pop();
        const str = 'call super::' + BH.getString(this.pbFunction.project.isUnicode,
            this.pbFunction.buffer, nameoffset);
        this._push(new StackObject(str));
    }

    callFunction(offset, count, type) {
        const uShort  = BH.getUShort(this.pbFunction.buffer, offset);
        const pbType  = PbType.getPbType(this.pbFunction.entry,
            BH.getUShort(this.pbFunction.buffer, offset + 2));
        const uInt    = BH.getUInt(this.pbFunction.buffer, offset + 4);
        if ((uInt & 0xFFFF) === 0xFFFF) throw new Error('CallFunction funnameoffset==0xFFFF');

        const source   = this._popStack(count);
        const stackObj = this._pop();
        let text = BH.getString(this.pbFunction.project.isUnicode, this.pbFunction.buffer, uInt);

        let pbFuncDef = null;
        if (uShort !== 0xFFFF && stackObj.type !== null && stackObj.type.name !== 'any') {
            const obj = stackObj.type.getObject(this.pbFunction.entry);
            if (obj !== null) {
                const allDefs = obj.allFunctionDefinitions;
                if (uShort < allDefs.length) pbFuncDef = allDefs[uShort];
                if (pbFuncDef !== null && pbFuncDef !== undefined) {
                    const defName = pbFuncDef.name;
                    if ((defName ?? '').toLowerCase() !== (text ?? '').toLowerCase()) {
                        pbFuncDef = null;
                    }
                }
            }
        }

        if (type & 1) text = 'post '    + text;
        if (type & 2) text = 'dynamic ' + text;
        if (type & 4) text = 'event '   + text;

        let prefix;
        if (stackObj.str !== 'this' || !pbType.name) {
            prefix = stackObj.str + '.';
        } else {
            prefix = 'super::';
        }

        const params = source.map(o => o.str).join(',');
        const str2   = `${prefix}${text}(${params})`;

        let resultType;
        if (stackObj.type !== null && stackObj.type.name === 'any') {
            resultType = stackObj.type;
        } else {
            resultType = (pbFuncDef !== null && pbFuncDef !== undefined) ? pbFuncDef.returnType : null;
        }
        this._push(new StackObject(str2, resultType));
    }

    callBuiltinFunction(fn, paramcount = 1) {
        const source = this._popStack(paramcount);
        const params = source.map(o => o.str).join(',');
        this._push(new StackObject(`${fn}(${params})`));
    }

    // ── Array operations ──────────────────────────────────────────────────────

    createArray(arraylen) {
        const source = this._popStack(arraylen);
        const params = source.map(o => o.str).join(',');
        this._push(new StackObject(`{${params}}`));
    }

    index() {
        const so  = this._pop();
        const so2 = this._pop();
        this._push(new StackObject(`${so2.str}[${so.str}]`, so2.type));
    }

    index2(p1, p2) {
        const so  = this._pop();
        const so2 = this._pop();
        this._push(new StackObject(`${so2.str}[${so.str}]`, so2.type));
    }

    index3(p1, p2, p3) {
        this._popStack(2);
        const so  = this._pop();
        const so2 = this._pop();
        this._push(new StackObject(`${so2.str}[${so.str}]`, so2.type));
    }

    // ── Cast ──────────────────────────────────────────────────────────────────

    cast(pos) { /* no-op */ }

    // ── SQL operations ────────────────────────────────────────────────────────

    sqlOperateTransaction(fn) {
        const arg = this._pop();
        this._codeLine.sCode = `${fn} using ${arg};`;
    }

    sqlOpen(paramCount) {
        const source = this._popStack(paramCount);
        const so     = this._pop();
        const cursor = this._pop();
        const pbVar  = this.pbFunction.variables.find(v => v.name === cursor.str) ?? null;
        if (pbVar !== null) pbVar.setCursorParams(source.map(o => o.str), so.str);
        this._codeLine.sCode = `open ${cursor};`;
    }

    sqlOpenDynamic(cursorOffset, paramCount) {
        const so     = this._pop();
        const cursor = this._pop();
        const source = this._popStack(paramCount);
        const pbVar  = this.pbFunction.variables.find(v => v.name === cursor.str) ?? null;
        if (pbVar !== null) pbVar.setDynamicCursorParams(so.str);
        const arg = (paramCount > 0)
            ? `using ${source.map(o => `:${o}`).join(',')}`
            : '';
        this._codeLine.sCode = `open dynamic ${cursor} ${arg};`;
    }

    sqlExecute(paramcount) {
        const source    = this._popStack(paramcount);
        const so        = this._pop();
        const procedure = this._pop();
        const pbVar = this.pbFunction.variables.find(v => v.name === procedure.str) ?? null;
        if (pbVar !== null) pbVar.setProcedureParams(source.map(o => o.str), so.str);
        this._codeLine.sCode = `execute ${procedure};`;
    }

    sqlExecuteDynamic(procedureOffset, paramCount) {
        const so        = this._pop();
        const cursor    = this._pop();
        const source    = this._popStack(paramCount);
        const pbVar = this.pbFunction.variables.find(v => v.name === cursor.str) ?? null;
        if (pbVar !== null) pbVar.setDynamicProcedureParams(so.str);
        const arg = (paramCount > 0)
            ? `using ${source.map(o => `:${o}`).join(',')}`
            : '';
        this._codeLine.sCode = `execute dynamic ${cursor} ${arg};`;
    }

    sqlFetch(paramcount) {
        this._pop();
        const so     = this._pop();
        const source = this._popStack(paramcount);
        const into   = source.map(o => `:${o.str}`).join(',');
        this._codeLine.sCode = `fetch ${so.str} into ${into};`;
    }

    sqlClose() {
        this._pop();
        const so = this._pop();
        this._codeLine.sCode = `close ${so.str};`;
    }

    sqlPrepareSqlsa() {
        const arg  = this._pop();
        let text   = this._pop().str;
        if (!text.startsWith('"')) text = ':' + text;
        const arg2 = this._pop();
        this._codeLine.sCode = `prepare ${arg2} from ${text} using ${arg};`;
    }

    sqlExecuteSqlsa(paramcount) {
        const source = this._popStack(paramcount);
        const arg    = this._pop();
        const using  = source.map(o => `:${o}`).join(',');
        this._codeLine.sCode = `execute ${arg} using ${using};`;
    }

    sqlExecuteImmediate() {
        const arg  = this._pop();
        let text   = this._pop().str;
        if (!text.startsWith('"')) text = ':' + text;
        this._codeLine.sCode = `execute immediate ${text} using ${arg};`;
    }

    sqlDescribe() {
        const arg  = this._pop();
        const arg2 = this._pop();
        this._codeLine.sCode = `describe ${arg2} into ${arg};`;
    }

    sqlOpenDynamicDescriptor(cursorOffset) {
        const so     = this._pop();
        const cursor = this._pop();
        const arg    = this._pop();
        const pbVar  = this.pbFunction.variables.find(v => v.name === cursor.str) ?? null;
        if (pbVar !== null) pbVar.setDynamicCursorParams(so.str);
        this._codeLine.sCode = `open dynamic ${cursor} using descriptor ${arg};`;
    }

    sqlExecuteDynamicDescriptor(procedureOffset) {
        const so     = this._pop();
        const cursor = this._pop();
        const arg    = this._pop();
        const pbVar  = this.pbFunction.variables.find(v => v.name === cursor.str) ?? null;
        if (pbVar !== null) pbVar.setDynamicCursorParams(so.str);
        this._codeLine.sCode = `execute dynamic ${cursor} using descriptor ${arg};`;
    }

    sqlFetchDynamicDescriptor() {
        this._pop();
        const arg  = this._pop();
        const arg2 = this._pop();
        this._codeLine.sCode = `fetch ${arg} using descriptor ${arg2};`;
    }

    sqlDirectInsertUpdateDelete(cursorOffset, paramcount) {
        const arg    = this._pop();
        const source = this._popStack(paramcount);
        const cursor = BH.getCursor(this.pbFunction.project.isUnicode,
            this.pbFunction.entry.variableBuffer, cursorOffset,
            source.map(o => o.str));
        this._codeLine.sCode = `${cursor} using ${arg};`;
    }

    sqlDirectSelect(cursorOffset, paramcount1, paramcount2) {
        const arg     = this._pop();
        const source  = this._popStack(paramcount1);
        const source2 = this._popStack(paramcount2);
        let cursor = BH.getCursor(this.pbFunction.project.isUnicode,
            this.pbFunction.entry.variableBuffer, cursorOffset,
            source.map(o => o.str));
        const into = source2.map(o => `:${o}`).join(',');
        cursor = cursor.replace(/ from /i, ` into ${into} from `);
        this._codeLine.sCode = `${cursor} using ${arg};`;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    _getTypeName(offset) {
        const uInt   = BH.getUInt(this.pbFunction.buffer, offset);
        const uShort = BH.getUShort(this.pbFunction.buffer, offset + 4);
        const pbType = PbType.getPbType(this.pbFunction.entry, uShort);
        const str    = BH.getString(this.pbFunction.project.isUnicode, this.pbFunction.buffer, uInt);
        if (pbType.name !== str) {
            throw new Error(`GetTypeName "${pbType.name}" != "${str}"`);
        }
        return pbType;
    }

    static _getOperatorLevel(operator) {
        if (operator === null || operator === undefined) return 0;
        switch (operator) {
            case '+': case '-':    return 5;
            case '*': case '/':    return 4;
            case '^':              return 3;
            case 'and': case 'or': return 2;
            case '=': case '<>': case '>': case '<': case '>=': case '<=': return 1;
            case '$not': case '$-': return 6;
            default: return 0;
        }
    }

    _pushVariable(variable) {
        this._push(new StackObject(variable.name, variable.type));
    }

    _push(so) { this._stackObjects.push(so); }

    _pop() { return this._stackObjects.pop(); }

    _peek() { return this._stackObjects[this._stackObjects.length - 1]; }

    _popStack(count) {
        const arr = new Array(count);
        for (let i = 0; i < count; i++) {
            arr[count - 1 - i] = this._pop();
        }
        return arr;
    }
}
