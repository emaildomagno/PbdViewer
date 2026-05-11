// PCodeParser90.js — port of Java PCodeParser90.java
import * as BH from '../BufferHelper.js';
import { JmpType } from '../JmpType.js';
import { PCodeParserBase } from './PCodeParserBase.js';

const PCODE_LEN = [
    2, 1, 1, 1, 0, 0, 0, 0, 0, 1,
    3, 3, 1, 3, 3, 4, 0, 1, 5, 0,
    3, 0, 3, 3, 0, 4, 3, 1, 1, 2,
    0, 0, 0, 0, 0, 0, 1, 0, 3, 2,
    3, 4, 2, 3, 1, 1, 1, 1, 1, 2,
    2, 2, 2, 2, 2, 2, 2, 1, 2, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 2,
    1, 2, 1, 0, 0, 0, 0, 0, 0, 0,
    2, 0, 2, 2, 2, 2, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 2, 0, 2, 2,
    2, 2, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 2, 2, 2, 2, 0, 0, 0, 0,
    0, 0, 0, 0, 2, 2, 2, 2, 0, 0,
    0, 0, 0, 0, 0, 0, 2, 2, 2, 2,
    0, 0, 0, 0, 0, 0, 0, 0, 2, 2,
    2, 2, 0, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 1, 0,
    0, 0, 1, 1, 1, 1, 1, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 0, 0, 1, 1,
    0, 0, 0, 0, 3, 3, 2, 2, 3, 3,
    4, 4, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 2, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 2, 2,
    1, 1, 2, 3, 2, 3, 4, 1, 1, 1,
    1, 1, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 1, 0, 0,
    0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
    0, 1, 0, 1, 1, 0, 1, 1, 1, 0,
    0, 1, 0, 0, 0, 1, 0, 0, 0, 1,
    1, 0, 1, 1, 1, 1, 1, 5, 1, 4,
    1, 0, 2, 3, 3, 5, 3, 5, 1, 4,
    1, 1, 2, 2, 2, 3, 3, 3, 3, 0,
    3, 0, 2, 2, 2, 3, 1, 1, 4, 3,
    1, 1, 1, 0, 0, 2, 1, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 2, 0, 0, 0, 1,
    0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 0, 0, 0, 0, 0,
    0, 2, 1, 1, 1, 1, 1, 1, 1, 2,
    2, 2, 2, 2, 1, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 3, 2, 3, 4,
    3, 1, 1, 0, 1, 1, 0
];

export class PCodeParser90 extends PCodeParserBase {
    getPCodeLenArray() { return PCODE_LEN; }

    constructor(pbFunction) {
        super(pbFunction);
    }

    onGetPCodeLen(pcode) {
        if (this.pbFunction.project.version < 193 && pcode === 297) return 0;
        return super.onGetPCodeLen(pcode);
    }

    onParsePcode(pCodeOp, codeLine) {
        const p = codeLine.pCodeParam;
        switch (pCodeOp) {
            case 0:   this.doReturn(BH.getUShort(p, 0)); break;
            case 1:   this.jump(BH.getUShort(p, 0), JmpType.JmpIfTrue); break;
            case 2:   this.jump(BH.getUShort(p, 0), JmpType.JmpIfFalse); break;
            case 3:   this.jump(BH.getUShort(p, 0), JmpType.Jmp); break;
            case 4:   this.sqlOperateTransaction('connect'); break;
            case 5:   this.sqlOperateTransaction('commit'); break;
            case 6:   this.sqlOperateTransaction('rollback'); break;
            case 7:   this.sqlOperateTransaction('disconnect'); break;
            case 8:   this.sqlClose(); break;
            case 9:   this.sqlOpen(BH.getUShort(p, 0)); break;
            case 10:  this.sqlDirectInsertUpdateDelete(BH.getUInt(p, 0), BH.getUShort(p, 4)); break;
            case 11:  this.sqlDirectInsertUpdateDelete(BH.getUInt(p, 0), BH.getUShort(p, 4)); break;
            case 12:  this.sqlExecute(BH.getUShort(p, 0)); break;
            case 13:  this.sqlFetch(BH.getUShort(p, 4)); break;
            case 14:  this.sqlDirectInsertUpdateDelete(BH.getUInt(p, 0), BH.getUShort(p, 4)); break;
            case 15:  this.sqlDirectSelect(BH.getUInt(p, 0), BH.getUShort(p, 4), BH.getUShort(p, 6)); break;
            case 16:  this.destroyObject(); break;
            case 17:  this.halt(BH.getUShort(p, 0)); break;
            case 18:  this.callSuper(BH.getUShort(p, 0), BH.getUShort(p, 2), BH.getUShort(p, 4), BH.getUInt(p, 6)); break;
            case 19:  this.popFunction(); break;
            case 20:  this.sqlExecuteSqlsa(BH.getUShort(p, 4)); break;
            case 21:  this.sqlPrepareSqlsa(); break;
            case 22:  this.sqlOpenDynamic(BH.getUInt(p, 0), BH.getUShort(p, 4)); break;
            case 23:  this.sqlExecuteDynamic(BH.getUInt(p, 0), BH.getUShort(p, 4)); break;
            case 24:  this.sqlDescribe(); break;
            case 25:  this.sqlDirectSelect(BH.getUInt(p, 0), BH.getUShort(p, 4), BH.getUShort(p, 6)); break;
            case 26:  this.sqlDirectInsertUpdateDelete(BH.getUInt(p, 0), BH.getUShort(p, 4) + 1); break;
            case 27:  this.pushLocalVariable(BH.getUShort(p, 0)); break;
            case 28:  this.pushSharedVariable(BH.getUShort(p, 0)); break;
            case 29:  this.pushInstanceVariableName(BH.getUShort(p, 0)); break;
            case 30:  this.pushThis(); break;
            case 31:  this.pushParent(); break;
            case 33:  this.operateStack('and'); break;
            case 34:  this.operateStack('or'); break;
            case 35:  this.operateStackSingle('not'); break;
            case 36:  this.pushInstanceVariable(BH.getUShort(p, 0)); break;
            case 37:  break;
            case 41:  this.callFunction(BH.getUInt(p, 0), BH.getUShort(p, 4), BH.getUShort(p, 6)); break;
            case 42:  this.createObject(BH.getUInt(p, 0)); break;
            case 44:  this.pushGlobalVariable(BH.getUShort(p, 0)); break;
            case 45:  this.pushLocalVariable(BH.getUShort(p, 0)); break;
            case 46:  this.pushGlobalSharedVariable(BH.getUShort(p, 0)); break;
            case 47:  this.pushConstant(String(((BH.getUShort(p, 0) << 16) >> 16))); break;  // signed short
            case 48:  this.pushConstant(String(BH.getUShort(p, 0))); break;
            case 49:  this.pushConstant(String((BH.getUInt(p, 0) | 0))); break;  // signed int
            case 50:  this.pushConstant(String(BH.getUInt(p, 0))); break;
            case 51:  this.pushConstant(BH.getDecimal(this.pbFunction.buffer, BH.getUInt(p, 0))); break;
            case 52:  this.pushConstant(BH.getReal(BH.getUInt(p, 0))); break;
            case 53:  this.pushConstant(BH.getDouble(this.pbFunction.buffer, BH.getUInt(p, 0))); break;
            case 54:  this.pushConstant(BH.getTime(this.pbFunction.buffer, BH.getUInt(p, 0))); break;
            case 55:  this.pushConstant(BH.getDate(this.pbFunction.buffer, BH.getUInt(p, 0))); break;
            case 56:  this.pushConstant(BH.getEscapeString(this.pbFunction.project.isUnicode, this.pbFunction.buffer, BH.getUInt(p, 0))); break;
            case 57:  this.pushConstant(String(BH.getUShort(p, 0) === 1)); break;
            case 58:  this.pushEnum(BH.getUShort(p, 2), BH.getUShort(p, 0)); break;
            // cast range 59-79
            case 59: case 60: case 61: case 62: case 63: case 64: case 65:
            case 66: case 67: case 68: case 69: case 70: case 71: case 72:
            case 73: case 74: case 75: case 76: case 77: case 78: case 79:
                this.cast(BH.getUShort(p, 0)); break;
            // arithmetic +
            case 80: case 81: case 82: case 83: case 84: case 85: case 86:
                this.operateStack('+'); break;
            // arithmetic -
            case 87: case 88: case 89: case 90: case 91: case 92: case 93:
                this.operateStack('-'); break;
            // arithmetic *
            case 94: case 95: case 96: case 97: case 98: case 99: case 100:
                this.operateStack('*'); break;
            // arithmetic /
            case 101: case 102: case 103: case 104: case 105: case 106: case 107:
                this.operateStack('/'); break;
            // arithmetic ^
            case 108: case 109: case 110: case 111: case 112: case 113: case 114:
                this.operateStack('^'); break;
            // unary -
            case 115: case 116: case 117: case 118: case 119: case 120: case 121:
                this.operateStackSingle('-'); break;
            // string concat +
            case 122: case 123:
                this.operateStack('+'); break;
            case 124:
                this.endAssign(true); break;
            // plain assign
            case 125: case 126: case 127: case 128: case 129: case 130: case 131:
            case 132: case 133: case 134: case 135: case 136: case 137:
                this.endAssign(); break;
            // cast 138-162
            case 138: case 139: case 140: case 141: case 142: case 143: case 144:
            case 145: case 146: case 147: case 148: case 149: case 150: case 151:
            case 152: case 153: case 154: case 155: case 156: case 157: case 158:
            case 159: case 160: case 161: case 162:
                this.cast(BH.getUShort(p, 0)); break;
            // compare =
            case 163: case 164: case 165: case 166: case 167: case 168: case 169:
            case 170: case 171: case 172: case 173: case 174: case 175: case 176:
            case 177: case 178:
                this.operateStack('='); break;
            // compare <>
            case 179: case 180: case 181: case 182: case 183: case 184: case 185:
            case 186: case 187: case 188: case 189: case 190: case 191: case 192:
            case 193: case 194:
                this.operateStack('<>'); break;
            // compare >
            case 195: case 196: case 197: case 198: case 199: case 200: case 201:
            case 202: case 203: case 204: case 205: case 206:
                this.operateStack('>'); break;
            // compare <
            case 207: case 208: case 209: case 210: case 211: case 212: case 213:
            case 214: case 215: case 216: case 217: case 218:
                this.operateStack('<'); break;
            // compare >=
            case 219: case 220: case 221: case 222: case 223: case 224: case 225:
            case 226: case 227: case 228: case 229: case 230:
                this.operateStack('>='); break;
            // compare <=
            case 231: case 232: case 233: case 234: case 235: case 236: case 237:
            case 238: case 239: case 240: case 241: case 242:
                this.operateStack('<='); break;
            // ++
            case 243: case 244: case 245: case 246: case 247: case 248: case 249:
                this.endAssign2('++'); break;
            // --
            case 250: case 251: case 252: case 253: case 254: case 255: case 256:
                this.endAssign2('--'); break;
            // +=
            case 257: case 258: case 259: case 260: case 261: case 262: case 263:
                this.endAssign('+'); break;
            // -=
            case 264: case 265: case 266: case 267: case 268: case 269: case 270:
                this.endAssign('-'); break;
            // *=
            case 271: case 272: case 273: case 274: case 275: case 276: case 277:
                this.endAssign('*'); break;
            case 278: this.resetAssign(BH.getUShort(p, 0)); break;
            case 282: this.beginAssignLocalVariable(BH.getUShort(p, 0)); break;
            case 283: this.beginAssignSharedVariable(BH.getUShort(p, 0)); break;
            case 284: this.beginAssignGlobalVariable(BH.getUShort(p, 0)); break;
            case 285: this.beginAssignLocalVariable(BH.getUShort(p, 0)); break;
            case 287: this.beginAssignInstanceVariable(); break;
            // cast 288-296
            case 288: case 289: case 290: case 291: case 292: case 293: case 294:
            case 295: case 296:
                this.cast(0); break;
            // cast 298-315
            case 298: case 299: case 300: case 301: case 302: case 303: case 304:
            case 305: case 306: case 307: case 308: case 309: case 310: case 311:
            case 312: case 313: case 314: case 315:
                this.cast(BH.getUShort(p, 0)); break;
            case 317: break;
            case 318: case 319: this.pushInstanceVariable(BH.getUShort(p, 0)); break;
            case 320: case 321: case 322: case 323: this.cast(0); break;
            case 330: case 331: this.callFunction(BH.getUInt(p, 0), BH.getUShort(p, 4), BH.getUShort(p, 6)); break;
            case 332: case 333: this.pushLocalVariable(BH.getUShort(p, 0)); break;
            case 334: case 335: this.pushSharedVariable(BH.getUShort(p, 0)); break;
            case 336: case 337: this.pushGlobalVariable(BH.getUShort(p, 0)); break;
            case 342: this.endAssign(); break;
            // cast 343-361
            case 343: case 344: case 345: case 346: case 347: case 348: case 349:
            case 350: case 351: case 352: case 353: case 354: case 355: case 356:
            case 357: case 358: case 359: case 360: case 361:
                this.cast(BH.getUShort(p, 0)); break;
            case 362: this.createObject(BH.getUInt(p, 0)); break;
            case 366: this.callFunction(BH.getUInt(p, 0), BH.getUShort(p, 4), BH.getUShort(p, 6)); break;
            case 367: this.pushLocalVariable(BH.getUShort(p, 0)); break;
            case 368: this.pushSharedVariable(BH.getUShort(p, 0)); break;
            case 369: this.pushGlobalVariable(BH.getUShort(p, 0)); break;
            case 372: this.operateStack('+'); break;
            case 373: this.operateStack('-'); break;
            case 374: this.operateStack('*'); break;
            case 375: this.operateStack('/'); break;
            case 376: this.operateStack('^'); break;
            case 377: this.operateStackSingle('-'); break;
            case 378: this.operateStack('='); break;
            case 379: this.operateStack('<>'); break;
            case 380: this.operateStack('>'); break;
            case 381: this.operateStack('<'); break;
            case 382: this.operateStack('>='); break;
            case 383: this.operateStack('<='); break;
            case 384: this.operateStack('and'); break;
            case 385: this.operateStack('or'); break;
            case 386: this.operateStackSingle('not'); break;
            case 387: this.pushInstanceVariable(BH.getUShort(p, 0)); break;
            case 388: case 389: this.cast(0); break;
            case 390: this.callBuiltinFunction('int'); break;
            case 391: this.callBuiltinFunction('abs'); break;
            case 392: this.callBuiltinFunction('abs'); break;
            case 393: this.callBuiltinFunction('asc'); break;
            case 394: this.callBuiltinFunction('blob'); break;
            case 395: this.callBuiltinFunction('ceiling'); break;
            case 396: this.callBuiltinFunction('cos'); break;
            case 397: this.callBuiltinFunction('exp'); break;
            case 398: this.callBuiltinFunction('fact'); break;
            case 399: this.callBuiltinFunction('inthigh'); break;
            case 400: this.callBuiltinFunction('intlow'); break;
            case 401: this.callBuiltinFunction('isdate'); break;
            case 402: this.callBuiltinFunction('isnull'); break;
            case 403: this.callBuiltinFunction('isnumber'); break;
            case 404: this.callBuiltinFunction('istime'); break;
            case 405: this.callBuiltinFunction('isvalid'); break;
            case 406: this.callBuiltinFunction('lefttrim'); break;
            case 407: this.callBuiltinFunction('len'); break;
            case 408: this.callBuiltinFunction('len'); break;
            case 409: this.callBuiltinFunction('log'); break;
            case 410: this.callBuiltinFunction('logten'); break;
            case 411: this.callBuiltinFunction('lower'); break;
            case 412: this.callBuiltinFunction('pi'); break;
            case 413: this.callBuiltinFunction('rand'); break;
            case 415: this.callBuiltinFunction('righttrim'); break;
            case 416: this.callBuiltinFunction('sin'); break;
            case 417: this.callBuiltinFunction('sqrt'); break;
            case 418: this.callBuiltinFunction('tan'); break;
            case 419: this.callBuiltinFunction('trim'); break;
            case 420: this.callBuiltinFunction('upper'); break;
            case 422: this.pushGlobalVariable(BH.getUShort(p, 0)); break;
            case 425: this.pushLocalVariable(BH.getUShort(p, 0)); break;
            case 426: this.pushSharedVariable(BH.getUShort(p, 0)); break;
            case 427: this.cast(0); break;
            case 430: this.cast(BH.getUShort(p, 0)); break;
            case 431: this.index(); break;
            case 432: this.index2(BH.getUShort(p, 0), BH.getUShort(p, 2)); break;
            case 433: this.index3(BH.getUShort(p, 0), BH.getUShort(p, 2), BH.getUShort(p, 4)); break;
            case 434: this.createArray(BH.getUShort(p, 4)); break;
            case 435: this.createArray(BH.getUShort(p, 4)); break;
            case 438: this.cast(BH.getUShort(p, 0)); break;
            case 440: this.callBuiltinFunction('lowerbound'); break;
            case 441: this.callBuiltinFunction('upperbound'); break;
            case 442: this.endAssign2('++'); break;
            case 443: this.endAssign2('--'); break;
            case 444: this.pushGlobalFunctionName(BH.getUShort(p, 2), BH.getUShort(p, 0)); break;
            case 445: case 446: case 447:
                this.callGlobalFunction(BH.getUShort(p, 2), BH.getUShort(p, 4)); break;
            case 448: this.callGlobalFunction(BH.getUShort(p, 2), BH.getUShort(p, 4)); break;
            case 449: break;
            case 451: this.sqlExecuteImmediate(); break;
            case 452: this.sqlExecuteDynamicDescriptor(BH.getUInt(p, 0)); break;
            case 453: this.sqlFetchDynamicDescriptor(); break;
            case 454: this.sqlOpenDynamicDescriptor(BH.getUInt(p, 0)); break;
            case 456: this.createObjectUsingName(BH.getUShort(p, 0)); break;
            case 457: this.cast(BH.getUShort(p, 0)); break;
            case 459: this.cast(BH.getUShort(p, 0)); break;
            case 461: case 462: this.cast(BH.getUShort(p, 0)); break;
            case 463: this.cast(0); break;
            case 464: this.pushInstanceVariable(0); break;
            case 466: this.pushInstanceVariableName(BH.getUShort(p, 0)); break;
            case 467: case 468: case 469: case 470: case 471:
                this.callBuiltinFunction('mod', 2); break;
            case 472: case 473: this.callBuiltinFunction('abs'); break;
            case 474: this.callBuiltinFunction('ceiling'); break;
            case 475: case 476: case 477: case 478: case 479:
                this.callBuiltinFunction('min', 2); break;
            case 480: case 481: case 482: case 483: case 484:
                this.callBuiltinFunction('max', 2); break;
            case 485: this.tryBlock(BH.getUShort(p, 0), BH.getUShort(p, 2)); break;
            case 486: this.endTry(); break;
            case 487: this.doCatch(); break;
            case 488: this.doThrow(); break;
            case 489: this.enterFinally(BH.getUShort(p, 0)); break;
            case 490: this.leaveFinally(); break;
            // cast 491-504
            case 491: case 492: case 493: case 494: case 495: case 496: case 497:
            case 498: case 499: case 500: case 501: case 502: case 503: case 504:
                this.cast(BH.getUShort(p, 0)); break;
            case 505: this.operateStack('+'); break;
            case 506: this.operateStack('-'); break;
            case 507: this.operateStack('*'); break;
            case 508: this.operateStack('/'); break;
            case 509: this.operateStack('^'); break;
            case 510: this.operateStackSingle('-'); break;
            case 511: this.pushConstant(BH.getLongLong(this.pbFunction.buffer, BH.getUInt(p, 0))); break;
            case 512: this.pushLocalVariable(BH.getUShort(p, 0)); break;
            case 513: this.pushGlobalVariable(BH.getUShort(p, 0)); break;
            case 515: this.pushSharedVariable(BH.getUShort(p, 0)); break;
            case 517: this.endAssign(); break;
            case 519: this.endAssign('+'); break;
            case 520: this.endAssign('-'); break;
            case 521: this.endAssign('*'); break;
            case 522: this.endAssign2('++'); break;
            case 523: this.endAssign2('--'); break;
            case 524: this.cast(BH.getUShort(p, 0)); break;
            case 525: this.callBuiltinFunction('abs'); break;
            case 527: this.operateStack('='); break;
            case 528: this.operateStack('<>'); break;
            case 529: this.operateStack('>'); break;
            case 530: this.operateStack('<'); break;
            case 531: this.operateStack('>='); break;
            case 532: this.operateStack('<='); break;
            case 533: this.callBuiltinFunction('mod', 2); break;
            case 534: this.callBuiltinFunction('min', 2); break;
            case 535: this.callBuiltinFunction('max', 2); break;
            case 539: this.callFunction(BH.getUInt(p, 0), BH.getUShort(p, 4), BH.getUShort(p, 6)); break;
            case 540: this.callGlobalFunction(BH.getUShort(p, 2), BH.getUShort(p, 4)); break;
            case 542: this.pushInstanceVariable(BH.getUShort(p, 0)); break;
            case 543: this.cast(0); break;
            case 544: this.cast(BH.getUShort(p, 0)); break;
            case 546: this.cast(0); break;
            default: return false;
        }
        return true;
    }
}
