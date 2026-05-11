// PCodeHelper.js — port of Java PCodeHelper.java
import * as BH from './BufferHelper.js';
import { JmpType } from './JmpType.js';
import { CodeLine } from './CodeLine.js';
import { CodeArea } from './CodeArea.js';
import { PCodeParser90 }  from './pcode/PCodeParser90.js';
import { PCodeParser100 } from './pcode/PCodeParser100.js';
import { PCodeParser105 } from './pcode/PCodeParser105.js';
import { PCodeParser110 } from './pcode/PCodeParser110.js';

/** @type {Map<number,number>} */
export const COUNT         = new Map();
/** @type {Map<number,number>} */
export const GOODCOUNT     = new Map();
/** @type {Map<number,Set<number>>} */
export const USED_PCODE_LIST     = new Map();
/** @type {Map<number,Set<number>>} */
export const UNPARSED_PCODE_LIST = new Map();

export class PCodeHelper {
    /**
     * @param {import('./pbclass/PbFunction.js').PbFunction} pbFunction
     * @param {boolean} depth
     * @returns {string[]}
     */
    static parsePCode(pbFunction, depth) {
        if (pbFunction.project.isDebug && !depth) {
            return PCodeHelper._privateParseCode(pbFunction, false);
        }
        const list = [];
        for (const item of PCodeHelper._privateParseCode(pbFunction, depth)) {
            for (const text of item.split(/[\r\n]+/)) {
                let text2 = text;
                if (!pbFunction.project.isDebug) {
                    if (text.length < 48) continue;
                    text2 = text.slice(48);
                    if (!text2.trim()) continue;
                }
                list.push(text2);
            }
        }
        return list;
    }

    /**
     * @param {import('./pbclass/PbFunction.js').PbFunction} pbFunction
     * @param {boolean} depth
     * @returns {string[]}
     */
    static _privateParseCode(pbFunction, depth) {
        const version = pbFunction.project.version;
        PCodeHelper._init(version);

        /** @type {Map<number, CodeLine>} */
        const dictionary = new Map();

        if (pbFunction.pCodeBytes.length === 0) {
            return Array.from(dictionary.values()).map(cl => cl.toString());
        }

        const pCodeParse = PCodeHelper._getPCodeParser(pbFunction, version);

        // Build debug map: pcode-position -> line number
        const debugMap = new Map();
        for (let i = 0; i < pbFunction.debugBytes.length / 4; i++) {
            const buf = BH.getBuffer(pbFunction.debugBytes, i * 4, 4);
            debugMap.set(BH.getUShort(buf, 2), BH.getUShort(buf, 0));
        }

        let flag     = false;
        let num      = 0;
        let codeLine = null;

        while (num < pbFunction.pCodeBytes.length) {
            const uShort   = BH.getUShort(pbFunction.pCodeBytes, num);
            const pCodeLen = pCodeParse !== null ? pCodeParse.getPCodeLen(uShort) : 255;
            if ((pCodeLen & 0xFF) === 255) { flag = true; break; }

            const codeLine2 = new CodeLine();
            codeLine2.pCodePosition = num;
            codeLine2.debugLine     = debugMap.has(num) ? debugMap.get(num) : null;
            codeLine2.pCodeOp       = uShort;
            codeLine2.pCodeParam    = BH.getBuffer(pbFunction.pCodeBytes, num + 2, pCodeLen * 2);

            if (codeLine !== null) {
                codeLine.nextCodeLine    = codeLine2;
                codeLine2.preCodeLine    = codeLine;
            }
            codeLine = codeLine2;

            if (depth) {
                try {
                    if (!flag) {
                        pCodeParse.parsePCode(codeLine2);
                        if (codeLine2.sCode !== null && codeLine2.sCode.startsWith('--') &&
                                !UNPARSED_PCODE_LIST.get(version).has(codeLine2.pCodeOp)) {
                            UNPARSED_PCODE_LIST.get(version).add(codeLine2.pCodeOp);
                        }
                    }
                } catch (e) {
                    flag = true;
                }
            }

            USED_PCODE_LIST.get(version).add(codeLine2.pCodeOp);
            dictionary.set(codeLine2.pCodePosition, codeLine2);
            num = (num + 2 + pCodeLen * 2) & 0xFFFF;
            if (num > pbFunction.pCodeBytes.length) break;
        }

        if (depth) {
            try {
                PCodeHelper._parseJmp(pbFunction, dictionary);
            } catch (e) {
                flag = true;
            }
        }

        if (!flag) GOODCOUNT.set(version, (GOODCOUNT.get(version) ?? 0) + 1);
        COUNT.set(version, (COUNT.get(version) ?? 0) + 1);

        return Array.from(dictionary.values()).map(cl => cl.toString());
    }

    // ── Jump / control-flow post-processing ─────────────────────────────────

    static _parseJmp(pbFunction, list) {
        const areas = [];
        if (pbFunction.project.version >= 283) {
            PCodeHelper._parseReturn(list);
        }
        PCodeHelper._parseTryCatchFinally(list, areas);
        PCodeHelper._parseChoose(list, areas);
        PCodeHelper._parseForNext(list, areas);
        PCodeHelper._parseDoLoop(list, areas);
        PCodeHelper._parseExitContinue(list, areas);
        PCodeHelper._parseIfElse(list);
        PCodeHelper._parseGoto(list);
        PCodeHelper._parseEventReturn(list);
        PCodeHelper._parseIndent(pbFunction, list);
    }

    static _parseReturn(list) {
        for (const value of list.values()) {
            if (value.sCode && value.sCode.startsWith('return ') &&
                    value.nextCodeLine !== null &&
                    value.nextCodeLine.jmpType === JmpType.Jmp &&
                    list.has(value.nextCodeLine.jmpPosition) &&
                    list.get(value.nextCodeLine.jmpPosition).sCode === 'return') {
                value.nextCodeLine.sCode   = '';
                value.nextCodeLine.jmpType = JmpType.None;
            }
        }
    }

    static _parseTryCatchFinally(list, areas) {
        for (const value of list.values()) {
            if (!value.sCode) continue;
            if (value.jmpType === JmpType.JmpIfFalse && value.condition && value.condition.startsWith('catch (')) {
                value.sCode   = value.condition;
                value.jmpType = JmpType.None;
                const jmpTarget = list.get(value.jmpPosition);
                if (jmpTarget && jmpTarget.preCodeLine &&
                        !jmpTarget.sCode.startsWith('end try ') && !jmpTarget.sCode.startsWith('enter finally ') &&
                        jmpTarget.preCodeLine.jmpType === JmpType.Jmp) {
                    const preJmpTarget = list.get(jmpTarget.preCodeLine.jmpPosition);
                    if (preJmpTarget && (preJmpTarget.sCode.startsWith('end try ') || preJmpTarget.sCode.startsWith('enter finally '))) {
                        jmpTarget.preCodeLine.sCode   = '';
                        jmpTarget.preCodeLine.jmpType = JmpType.None;
                    }
                }
            }
            if (value.jmpType === JmpType.Jmp && list.has(value.jmpPosition)) {
                const target = list.get(value.jmpPosition);
                if (target.sCode.startsWith('end try ') || target.sCode.startsWith('enter finally ')) {
                    value.sCode   = '';
                    value.jmpType = JmpType.None;
                }
            }
            if (value.sCode === 'enter finally ') {
                value.sCode = '';
                const finallyTarget = list.get(value.jmpPosition);
                if (finallyTarget) finallyTarget.labelSCode.push('finally ');
            }
        }
    }

    static _parseChoose(list, areas) {
        const dictionary = new Map();  // string -> CodeArea
        for (const value of list.values()) {
            if (!value.sCode) continue;
            if (value.sCode.startsWith('case')) {
                const text = value.sCode.slice(0, value.sCode.indexOf('=')).trim();
                value.sCode = value.sCode.replace(text + ' = ', 'choose case ');
                dictionary.set(text, new CodeArea('choose', value.pCodePosition, 0));
            }
            if (value.jmpType !== JmpType.JmpIfFalse || value.jmpPosition <= value.pCodePosition ||
                    !value.condition || !value.condition.includes('')) {
                continue;
            }
            let text2 = '';
            for (const [key] of dictionary) {
                if (value.condition.endsWith(key) || value.condition.includes(key + ' ')) {
                    text2 = key; break;
                }
            }
            if (!text2) continue;

            if (value.condition.includes(` <= ${text2} and `)) {
                value.sCode = 'case ' + value.condition
                    .replace(` <= ${text2} and `, ' to ')
                    .replace(` >= ${text2}`, '');
            } else if (value.condition.endsWith(` = ${text2}`)) {
                value.sCode = 'case ' + value.condition.replace(` = ${text2}`, '');
            } else if (value.condition.includes(` <= ${text2}`)) {
                value.sCode = 'case is >= ' + value.condition.replace(` <= ${text2}`, '');
            } else if (value.condition.includes(` >= ${text2}`)) {
                value.sCode = 'case is <= ' + value.condition.replace(` >= ${text2}`, '');
            } else if (value.condition.includes(` < ${text2}`)) {
                value.sCode = 'case is > ' + value.condition.replace(` < ${text2}`, '');
            } else if (value.condition.includes(` > ${text2}`)) {
                value.sCode = 'case is < ' + value.condition.replace(` > ${text2}`, '');
            }
            value.jmpType = JmpType.None;

            let preCodeLine = value.preCodeLine;
            while (preCodeLine !== null && (!preCodeLine.sCode || !preCodeLine.sCode)) {
                preCodeLine = preCodeLine.preCodeLine;
            }
            if (preCodeLine !== null && preCodeLine.sCode === 'case else ') {
                preCodeLine.sCode = '';
            }
            const jmpTarget = list.get(value.jmpPosition);
            if (jmpTarget && jmpTarget.preCodeLine && jmpTarget.preCodeLine.jmpType === JmpType.Jmp) {
                jmpTarget.preCodeLine.sCode   = 'case else ';
                jmpTarget.preCodeLine.jmpType = JmpType.None;
                const area = dictionary.get(text2);
                if (area.end === 0) {
                    const endTarget = list.get(jmpTarget.preCodeLine.jmpPosition);
                    if (endTarget) {
                        endTarget.labelSCode.unshift('end choose ');
                        dictionary.set(text2, new CodeArea(area.type, area.start, jmpTarget.preCodeLine.pCodePosition));
                    }
                }
            } else if (jmpTarget) {
                const area = dictionary.get(text2);
                if (area.end === 0) {
                    jmpTarget.labelSCode.unshift('end choose ');
                    const preJmpTarget = jmpTarget.preCodeLine;
                    dictionary.set(text2, new CodeArea(area.type, area.start, preJmpTarget ? preJmpTarget.pCodePosition : 0));
                }
            }
        }
        for (const a of dictionary.values()) areas.push(a);
    }

    static _parseForNext(list, areas) {
        for (const value of list.values()) {
            if (!value.sCode) continue;
            if (value.jmpType !== JmpType.JmpIfFalse) continue;
            if (value.jmpPosition <= value.pCodePosition) continue;
            const jmpTarget = list.get(value.jmpPosition);
            if (!jmpTarget || !jmpTarget.preCodeLine) continue;
            if (jmpTarget.preCodeLine.jmpType !== JmpType.Jmp) continue;
            if (jmpTarget.preCodeLine.jmpPosition >= jmpTarget.preCodeLine.pCodePosition) continue;
            if (jmpTarget.preCodeLine.jmpPosition >= value.pCodePosition) continue;
            const innerPreCodeLine = list.get(jmpTarget.preCodeLine.jmpPosition);
            if (!innerPreCodeLine) continue;
            const preCodeLine = innerPreCodeLine.preCodeLine;
            if (!preCodeLine || preCodeLine.jmpType !== JmpType.Jmp) continue;

            const parts = (value.condition ? value.condition.split(/[><= ]+/) : []).filter(x => x);
            if (parts.length > 1) {
                let preCodeLine2 = value.preCodeLine;
                while (preCodeLine2 !== null && (!preCodeLine2.sCode)) {
                    preCodeLine2 = preCodeLine2.preCodeLine;
                }
                let arg = '';
                if (preCodeLine2 && preCodeLine2.sCode &&
                        preCodeLine2.sCode.startsWith(`${parts[1]} += `)) {
                    arg = 'step ' + preCodeLine2.sCode.slice(`${parts[1]} += `.length);
                }
                if (preCodeLine2) preCodeLine2.sCode = '';
                const initSCode = (preCodeLine && preCodeLine.preCodeLine) ? preCodeLine.preCodeLine.sCode : '';
                value.sCode   = `for ${initSCode} to ${parts[0]} ${arg}`.trim();
                value.jmpType = JmpType.None;
                if (preCodeLine && preCodeLine.preCodeLine) preCodeLine.preCodeLine.sCode = '';
                if (preCodeLine) {
                    preCodeLine.sCode   = '';
                    preCodeLine.jmpType = JmpType.None;
                }
                jmpTarget.preCodeLine.sCode   = 'next ';
                jmpTarget.preCodeLine.jmpType = JmpType.None;
                areas.push(new CodeArea('for', value.pCodePosition, jmpTarget.preCodeLine.pCodePosition));
            }
        }
    }

    static _parseDoLoop(list, areas) {
        for (const value of list.values()) {
            if (!value.sCode) continue;
            if (value.jmpType === JmpType.JmpIfFalse) {
                if (value.jmpPosition > value.pCodePosition) {
                    const jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget && jmpTarget.preCodeLine &&
                            jmpTarget.preCodeLine.jmpType === JmpType.Jmp &&
                            jmpTarget.preCodeLine.jmpPosition < jmpTarget.preCodeLine.pCodePosition &&
                            jmpTarget.preCodeLine.jmpPosition < value.pCodePosition) {
                        value.sCode   = `do while ${value.condition}`;
                        value.jmpType = JmpType.None;
                        jmpTarget.preCodeLine.sCode   = 'loop ';
                        jmpTarget.preCodeLine.jmpType = JmpType.None;
                        areas.push(new CodeArea('do', value.pCodePosition, jmpTarget.preCodeLine.pCodePosition));
                    }
                } else {
                    const jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget) jmpTarget.labelSCode.push('do ');
                    value.sCode   = `loop until ${value.condition}`;
                    value.jmpType = JmpType.None;
                    if (jmpTarget) areas.push(new CodeArea('do', jmpTarget.pCodePosition, value.pCodePosition));
                }
            } else if (value.jmpType === JmpType.JmpIfTrue) {
                if (value.jmpPosition > value.pCodePosition) {
                    const jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget && jmpTarget.preCodeLine &&
                            jmpTarget.preCodeLine.jmpType === JmpType.Jmp &&
                            jmpTarget.preCodeLine.jmpPosition < jmpTarget.preCodeLine.pCodePosition &&
                            jmpTarget.preCodeLine.jmpPosition < value.pCodePosition) {
                        value.sCode   = `do until ${value.condition}`;
                        value.jmpType = JmpType.None;
                        jmpTarget.preCodeLine.sCode   = 'loop ';
                        jmpTarget.preCodeLine.jmpType = JmpType.None;
                        areas.push(new CodeArea('do', value.pCodePosition, jmpTarget.preCodeLine.pCodePosition));
                    }
                } else {
                    const jmpTarget = list.get(value.jmpPosition);
                    if (jmpTarget) jmpTarget.labelSCode.push('do ');
                    value.sCode   = `loop while ${value.condition}`;
                    value.jmpType = JmpType.None;
                    if (jmpTarget) areas.push(new CodeArea('do', jmpTarget.pCodePosition, value.pCodePosition));
                }
            }
        }
    }

    static _parseExitContinue(list, areas) {
        if (areas.length === 0) return;
        for (const codeLine of list.values()) {
            if (!codeLine.sCode) continue;
            if (codeLine.jmpType !== JmpType.Jmp) continue;
            if (codeLine.jmpPosition <= codeLine.pCodePosition) continue;

            // Find nearest area (minimise Manhattan distance from codeLine.pCodePosition to area bounds)
            let codeArea = null;
            let bestDist = Infinity;
            for (const a of areas) {
                const d = Math.abs(codeLine.pCodePosition - a.start) + Math.abs(a.end - codeLine.pCodePosition);
                if (d < bestDist) { bestDist = d; codeArea = a; }
            }
            if (!codeArea) continue;
            if (codeLine.pCodePosition >= codeArea.start && codeLine.pCodePosition <= codeArea.end) {
                const jmpTarget = list.get(codeLine.jmpPosition);
                if (jmpTarget && jmpTarget.preCodeLine &&
                        jmpTarget.preCodeLine.pCodePosition === codeArea.end) {
                    codeLine.sCode   = 'exit';
                    codeLine.jmpType = JmpType.None;
                } else if (jmpTarget && jmpTarget.pCodePosition === codeArea.end) {
                    codeLine.sCode   = 'continue';
                    codeLine.jmpType = JmpType.None;
                }
            }
        }
    }

    static _parseIfElse(list) {
        for (const value of list.values()) {
            if (!value.sCode) continue;
            if (value.jmpType !== JmpType.JmpIfFalse) continue;
            if (value.jmpPosition <= value.pCodePosition) continue;

            const jmpTarget = list.get(value.jmpPosition);
            if (!jmpTarget) continue;

            if (jmpTarget.preCodeLine && jmpTarget.preCodeLine.jmpType === JmpType.Jmp &&
                    jmpTarget.preCodeLine.jmpPosition > jmpTarget.preCodeLine.pCodePosition) {
                value.sCode   = `if ${value.condition} then `;
                value.jmpType = JmpType.None;
                if (jmpTarget.preCodeLine.sCode === 'exit' || jmpTarget.preCodeLine.sCode === 'continue') {
                    jmpTarget.preCodeLine.jmpType = JmpType.None;
                    jmpTarget.labelSCode.unshift('end if ');
                } else {
                    jmpTarget.preCodeLine.sCode   = 'else ';
                    jmpTarget.preCodeLine.jmpType = JmpType.None;
                    const endIfTarget = list.get(jmpTarget.preCodeLine.jmpPosition);
                    if (endIfTarget) endIfTarget.labelSCode.unshift('end if ');
                }
            } else {
                value.sCode   = `if ${value.condition} then`;
                value.jmpType = JmpType.None;
                jmpTarget.labelSCode.unshift('end if ');
            }
        }

        let num = 0;
        const hashSet = new Set();
        for (const value2 of list.values()) {
            if (value2.sCode === null || value2.sCode === undefined) continue;
            if (value2.sCode.trimStart().startsWith('if ')) {
                num++;
                const firstValidPre = PCodeHelper._getFirstValidPreCodeLine(value2.preCodeLine);
                if (firstValidPre && firstValidPre.sCode === 'else ') {
                    firstValidPre.sCode += value2.sCode;
                    value2.sCode = '';
                    hashSet.add(num);
                }
            }
            for (let i = 0; i < value2.labelSCode.length; i++) {
                if (value2.labelSCode[i].trimStart().startsWith('end if')) {
                    if (hashSet.has(num)) {
                        value2.labelSCode[i] = '';
                        hashSet.delete(num);
                    }
                    num--;
                }
            }
            if (value2.sCode.trimStart().startsWith('end if')) {
                if (hashSet.has(num)) {
                    value2.sCode = '';
                    hashSet.delete(num);
                }
                num--;
            }
        }
    }

    static _parseGoto(list) {
        // No-op (mirrors Java/C# which also no-ops)
    }

    static _parseEventReturn(list) {
        for (const value of list.values()) {
            if (!value.sCode) continue;
            if (!value.sCode.trimStart().startsWith('if isvalid(::message) then goto ')) continue;
            const next1 = PCodeHelper._getFirstValidNextCodeLine(value);
            if (!next1 || next1.sCode.trim() !== 'return 0') continue;
            const next2 = PCodeHelper._getFirstValidNextCodeLine(next1);
            if (next2 && next2.sCode.trim().startsWith('goto ')) {
                const next3 = PCodeHelper._getFirstValidNextCodeLine(next2);
                if (next3 && next3.sCode.trim() === 'return ::message.returnvalue') {
                    value.sCode = '';
                    next1.sCode = '';
                    next2.sCode = '';
                    next3.sCode = '';
                }
            }
        }
    }

    static _parseIndent(pbFunction, list) {
        const indent = [0];
        for (const value of list.values()) {
            try {
                for (let i = 0; i < value.labelSCode.length; i++) {
                    value.labelSCode[i] = PCodeHelper._parseIndentString(value.labelSCode[i], indent);
                }
                if (value.sCode) {
                    value.sCode = PCodeHelper._parseIndentString(value.sCode, indent);
                }
            } catch (e) {
                break;
            }
        }
    }

    static _parseIndentString(scode, indent) {
        if (scode.startsWith('try ')) {
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('catch ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('finally ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('end try ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith('if ')) {
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('else ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('end if ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith('for ')) {
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('next ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith('choose case ')) {
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0] += 2;
        } else if (scode.startsWith('case ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('end choose ')) {
            indent[0] -= 2;
            scode = ' '.repeat(indent[0] * 4) + scode;
        } else if (scode.startsWith('do ')) {
            scode = ' '.repeat(indent[0] * 4) + scode; indent[0]++;
        } else if (scode.startsWith('loop ')) {
            indent[0]--;
            scode = ' '.repeat(indent[0] * 4) + scode;
        } else if (scode) {
            scode = ' '.repeat(indent[0] * 4) + scode;
        }
        return scode;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static _init(version) {
        if (!COUNT.has(version)) {
            COUNT.set(version, 0);
            GOODCOUNT.set(version, 0);
            USED_PCODE_LIST.set(version, new Set());
            UNPARSED_PCODE_LIST.set(version, new Set());
        }
    }

    /**
     * @param {import('./pbclass/PbFunction.js').PbFunction} pbFunction
     * @param {number} version
     * @returns {import('./pcode/PCodeParserBase.js').PCodeParserBase|null}
     */
    static _getPCodeParser(pbFunction, version) {
        switch (version) {
            case 79: case 114: case 146: case 166: case 193: case 196:
                return new PCodeParser90(pbFunction);
            case 238:
                return new PCodeParser100(pbFunction);
            case 283:
                return new PCodeParser105(pbFunction);
            case 316: case 319: case 321: case 322: case 325: case 333: case 334:
                return new PCodeParser110(pbFunction);
            default:
                return null;
        }
    }

    static _getFirstValidPreCodeLine(codeLine) {
        if (!codeLine) return null;
        let pre = codeLine.preCodeLine;
        while (pre !== null && (!pre.sCode)) pre = pre.preCodeLine;
        return pre;
    }

    static _getFirstValidNextCodeLine(codeLine) {
        if (!codeLine) return null;
        let next = codeLine.nextCodeLine;
        while (next !== null && (!next.sCode)) next = next.nextCodeLine;
        return next;
    }
}
