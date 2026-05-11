package com.pbdviewer.utils.pcode;

import com.pbdviewer.utils.pbclass.PbFunction;
import com.pbdviewer.utils.BufferHelper;
import com.pbdviewer.utils.CodeLine;
import com.pbdviewer.utils.JmpType;

public class PCodeParser90 extends PCodeParserBase {

    private static final int[] PCODE_LEN = {
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
    };

    @Override
    protected int[] getPCodeLenArray() {
        return PCODE_LEN;
    }

    public PCodeParser90(PbFunction pbFunction) {
        super(pbFunction);
    }

    @Override
    protected int onGetPCodeLen(int pcode) {
        if (pbFunction.getProject().getVersion() < 193 && pcode == 297) return 0;
        return super.onGetPCodeLen(pcode);
    }

    @Override
    protected boolean onParsePcode(int pCodeOp, CodeLine codeLine) {
        switch (pCodeOp) {
            case 0:
                doReturn(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 1:
                jump(BufferHelper.getUShort(codeLine.pCodeParam, 0L), JmpType.JmpIfTrue);
                break;
            case 2:
                jump(BufferHelper.getUShort(codeLine.pCodeParam, 0L), JmpType.JmpIfFalse);
                break;
            case 3:
                jump(BufferHelper.getUShort(codeLine.pCodeParam, 0L), JmpType.Jmp);
                break;
            case 4:
                sqlOperateTransaction("connect");
                break;
            case 5:
                sqlOperateTransaction("commit");
                break;
            case 6:
                sqlOperateTransaction("rollback");
                break;
            case 7:
                sqlOperateTransaction("disconnect");
                break;
            case 8:
                sqlClose();
                break;
            case 9:
                sqlOpen(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 10:
                sqlDirectInsertUpdateDelete(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 11:
                sqlDirectInsertUpdateDelete(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 12:
                sqlExecute(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 13:
                sqlFetch(BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 14:
                sqlDirectInsertUpdateDelete(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 15:
                sqlDirectSelect(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUShort(codeLine.pCodeParam, 6L));
                break;
            case 16:
                destroyObject();
                break;
            case 17:
                halt(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 18:
                callSuper(BufferHelper.getUShort(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUInt(codeLine.pCodeParam, 6L));
                break;
            case 19:
                popFunction();
                break;
            case 20:
                sqlExecuteSqlsa(BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 21:
                sqlPrepareSqlsa();
                break;
            case 22:
                sqlOpenDynamic(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 23:
                sqlExecuteDynamic(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 24:
                sqlDescribe();
                break;
            case 25:
                sqlDirectSelect(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUShort(codeLine.pCodeParam, 6L));
                break;
            case 26:
                sqlDirectInsertUpdateDelete(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L) + 1);
                break;
            case 27:
                pushLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 28:
                pushSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 29:
                pushInstanceVariableName(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 30:
                pushThis();
                break;
            case 31:
                pushParent();
                break;
            case 33:
                operateStack("and");
                break;
            case 34:
                operateStack("or");
                break;
            case 35:
                operateStackSingle("not");
                break;
            case 36:
                pushInstanceVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 37:
                break;
            case 41:
                callFunction(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUShort(codeLine.pCodeParam, 6L));
                break;
            case 42:
                createObject(BufferHelper.getUInt(codeLine.pCodeParam, 0L));
                break;
            case 44:
                pushGlobalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 45:
                pushLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 46:
                pushGlobalSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 47:
                pushConstant(String.valueOf((short) BufferHelper.getUShort(codeLine.pCodeParam, 0L)));
                break;
            case 48:
                pushConstant(String.valueOf(BufferHelper.getUShort(codeLine.pCodeParam, 0L)));
                break;
            case 49:
                pushConstant(String.valueOf((int) BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 50:
                pushConstant(String.valueOf(BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 51:
                pushConstant(BufferHelper.getDecimal(pbFunction.getBuffer(), BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 52:
                pushConstant(BufferHelper.getReal(BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 53:
                pushConstant(BufferHelper.getDouble(pbFunction.getBuffer(), BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 54:
                pushConstant(BufferHelper.getTime(pbFunction.getBuffer(), BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 55:
                pushConstant(BufferHelper.getDate(pbFunction.getBuffer(), BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 56:
                pushConstant(BufferHelper.getEscapeString(pbFunction.getProject().isUnicode(), pbFunction.getBuffer(), BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 57:
                pushConstant(String.valueOf(BufferHelper.getUShort(codeLine.pCodeParam, 0L) == 1).toLowerCase());
                break;
            case 58:
                pushEnum(BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 59:
            case 60:
            case 61:
            case 62:
            case 63:
            case 64:
            case 65:
            case 66:
            case 67:
            case 68:
            case 69:
            case 70:
            case 71:
            case 72:
            case 73:
            case 74:
            case 75:
            case 76:
            case 77:
            case 78:
            case 79:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 80:
            case 81:
            case 82:
            case 83:
            case 84:
            case 85:
            case 86:
                operateStack("+");
                break;
            case 87:
            case 88:
            case 89:
            case 90:
            case 91:
            case 92:
            case 93:
                operateStack("-");
                break;
            case 94:
            case 95:
            case 96:
            case 97:
            case 98:
            case 99:
            case 100:
                operateStack("*");
                break;
            case 101:
            case 102:
            case 103:
            case 104:
            case 105:
            case 106:
            case 107:
                operateStack("/");
                break;
            case 108:
            case 109:
            case 110:
            case 111:
            case 112:
            case 113:
            case 114:
                operateStack("^");
                break;
            case 115:
            case 116:
            case 117:
            case 118:
            case 119:
            case 120:
            case 121:
                operateStackSingle("-");
                break;
            case 122:
            case 123:
                operateStack("+");
                break;
            case 124:
                endAssign(true);
                break;
            case 125:
            case 126:
            case 127:
            case 128:
            case 129:
            case 130:
            case 131:
            case 132:
            case 133:
            case 134:
            case 135:
            case 136:
            case 137:
                endAssign();
                break;
            case 138:
            case 139:
            case 140:
            case 141:
            case 142:
            case 143:
            case 144:
            case 145:
            case 146:
            case 147:
            case 148:
            case 149:
            case 150:
            case 151:
            case 152:
            case 153:
            case 154:
            case 155:
            case 156:
            case 157:
            case 158:
            case 159:
            case 160:
            case 161:
            case 162:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 163:
            case 164:
            case 165:
            case 166:
            case 167:
            case 168:
            case 169:
            case 170:
            case 171:
            case 172:
            case 173:
            case 174:
            case 175:
            case 176:
            case 177:
            case 178:
                operateStack("=");
                break;
            case 179:
            case 180:
            case 181:
            case 182:
            case 183:
            case 184:
            case 185:
            case 186:
            case 187:
            case 188:
            case 189:
            case 190:
            case 191:
            case 192:
            case 193:
            case 194:
                operateStack("<>");
                break;
            case 195:
            case 196:
            case 197:
            case 198:
            case 199:
            case 200:
            case 201:
            case 202:
            case 203:
            case 204:
            case 205:
            case 206:
                operateStack(">");
                break;
            case 207:
            case 208:
            case 209:
            case 210:
            case 211:
            case 212:
            case 213:
            case 214:
            case 215:
            case 216:
            case 217:
            case 218:
                operateStack("<");
                break;
            case 219:
            case 220:
            case 221:
            case 222:
            case 223:
            case 224:
            case 225:
            case 226:
            case 227:
            case 228:
            case 229:
            case 230:
                operateStack(">=");
                break;
            case 231:
            case 232:
            case 233:
            case 234:
            case 235:
            case 236:
            case 237:
            case 238:
            case 239:
            case 240:
            case 241:
            case 242:
                operateStack("<=");
                break;
            case 243:
            case 244:
            case 245:
            case 246:
            case 247:
            case 248:
            case 249:
                endAssign2("++");
                break;
            case 250:
            case 251:
            case 252:
            case 253:
            case 254:
            case 255:
            case 256:
                endAssign2("--");
                break;
            case 257:
            case 258:
            case 259:
            case 260:
            case 261:
            case 262:
            case 263:
                endAssign("+");
                break;
            case 264:
            case 265:
            case 266:
            case 267:
            case 268:
            case 269:
            case 270:
                endAssign("-");
                break;
            case 271:
            case 272:
            case 273:
            case 274:
            case 275:
            case 276:
            case 277:
                endAssign("*");
                break;
            case 278:
                resetAssign(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 282:
                beginAssignLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 283:
                beginAssignSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 284:
                beginAssignGlobalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 285:
                beginAssignLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 287:
                beginAssignInstanceVariable();
                break;
            case 288:
            case 289:
            case 290:
            case 291:
            case 292:
            case 293:
            case 294:
            case 295:
            case 296:
                cast(0);
                break;
            case 298:
            case 299:
            case 300:
            case 301:
            case 302:
            case 303:
            case 304:
            case 305:
            case 306:
            case 307:
            case 308:
            case 309:
            case 310:
            case 311:
            case 312:
            case 313:
            case 314:
            case 315:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 317:
                break;
            case 318:
            case 319:
                pushInstanceVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 320:
            case 321:
            case 322:
            case 323:
                cast(0);
                break;
            case 330:
            case 331:
                callFunction(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUShort(codeLine.pCodeParam, 6L));
                break;
            case 332:
            case 333:
                pushLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 334:
            case 335:
                pushSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 336:
            case 337:
                pushGlobalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 342:
                endAssign();
                break;
            case 343:
            case 344:
            case 345:
            case 346:
            case 347:
            case 348:
            case 349:
            case 350:
            case 351:
            case 352:
            case 353:
            case 354:
            case 355:
            case 356:
            case 357:
            case 358:
            case 359:
            case 360:
            case 361:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 362:
                createObject(BufferHelper.getUInt(codeLine.pCodeParam, 0L));
                break;
            case 366:
                callFunction(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUShort(codeLine.pCodeParam, 6L));
                break;
            case 367:
                pushLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 368:
                pushSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 369:
                pushGlobalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 372:
                operateStack("+");
                break;
            case 373:
                operateStack("-");
                break;
            case 374:
                operateStack("*");
                break;
            case 375:
                operateStack("/");
                break;
            case 376:
                operateStack("^");
                break;
            case 377:
                operateStackSingle("-");
                break;
            case 378:
                operateStack("=");
                break;
            case 379:
                operateStack("<>");
                break;
            case 380:
                operateStack(">");
                break;
            case 381:
                operateStack("<");
                break;
            case 382:
                operateStack(">=");
                break;
            case 383:
                operateStack("<=");
                break;
            case 384:
                operateStack("and");
                break;
            case 385:
                operateStack("or");
                break;
            case 386:
                operateStackSingle("not");
                break;
            case 387:
                pushInstanceVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 388:
            case 389:
                cast(0);
                break;
            case 390:
                callBuiltinFunction("int");
                break;
            case 391:
                callBuiltinFunction("abs");
                break;
            case 392:
                callBuiltinFunction("abs");
                break;
            case 393:
                callBuiltinFunction("asc");
                break;
            case 394:
                callBuiltinFunction("blob");
                break;
            case 395:
                callBuiltinFunction("ceiling");
                break;
            case 396:
                callBuiltinFunction("cos");
                break;
            case 397:
                callBuiltinFunction("exp");
                break;
            case 398:
                callBuiltinFunction("fact");
                break;
            case 399:
                callBuiltinFunction("inthigh");
                break;
            case 400:
                callBuiltinFunction("intlow");
                break;
            case 401:
                callBuiltinFunction("isdate");
                break;
            case 402:
                callBuiltinFunction("isnull");
                break;
            case 403:
                callBuiltinFunction("isnumber");
                break;
            case 404:
                callBuiltinFunction("istime");
                break;
            case 405:
                callBuiltinFunction("isvalid");
                break;
            case 406:
                callBuiltinFunction("lefttrim");
                break;
            case 407:
                callBuiltinFunction("len");
                break;
            case 408:
                callBuiltinFunction("len");
                break;
            case 409:
                callBuiltinFunction("log");
                break;
            case 410:
                callBuiltinFunction("logten");
                break;
            case 411:
                callBuiltinFunction("lower");
                break;
            case 412:
                callBuiltinFunction("pi");
                break;
            case 413:
                callBuiltinFunction("rand");
                break;
            case 415:
                callBuiltinFunction("righttrim");
                break;
            case 416:
                callBuiltinFunction("sin");
                break;
            case 417:
                callBuiltinFunction("sqrt");
                break;
            case 418:
                callBuiltinFunction("tan");
                break;
            case 419:
                callBuiltinFunction("trim");
                break;
            case 420:
                callBuiltinFunction("upper");
                break;
            case 422:
                pushGlobalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 425:
                pushLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 426:
                pushSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 427:
                cast(0);
                break;
            case 430:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 431:
                index();
                break;
            case 432:
                index2(BufferHelper.getUShort(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 2L));
                break;
            case 433:
                index3(BufferHelper.getUShort(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 434:
                createArray(BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 435:
                createArray(BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 438:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 440:
                callBuiltinFunction("lowerbound");
                break;
            case 441:
                callBuiltinFunction("upperbound");
                break;
            case 442:
                endAssign2("++");
                break;
            case 443:
                endAssign2("--");
                break;
            case 444:
                pushGlobalFunctionName(BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 445:
            case 446:
            case 447:
                callGlobalFunction(BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 448:
                callGlobalFunction(BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 449:
                break;
            case 451:
                sqlExecuteImmediate();
                break;
            case 452:
                sqlExecuteDynamicDescriptor(BufferHelper.getUInt(codeLine.pCodeParam, 0L));
                break;
            case 453:
                sqlFetchDynamicDescriptor();
                break;
            case 454:
                sqlOpenDynamicDescriptor(BufferHelper.getUInt(codeLine.pCodeParam, 0L));
                break;
            case 456:
                createObjectUsingName(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 457:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 459:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 461:
            case 462:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 463:
                cast(0);
                break;
            case 464:
                pushInstanceVariable(0);
                break;
            case 466:
                pushInstanceVariableName(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 467:
            case 468:
            case 469:
            case 470:
            case 471:
                callBuiltinFunction("mod", 2);
                break;
            case 472:
            case 473:
                callBuiltinFunction("abs");
                break;
            case 474:
                callBuiltinFunction("ceiling");
                break;
            case 475:
            case 476:
            case 477:
            case 478:
            case 479:
                callBuiltinFunction("min", 2);
                break;
            case 480:
            case 481:
            case 482:
            case 483:
            case 484:
                callBuiltinFunction("max", 2);
                break;
            case 485:
                tryBlock(BufferHelper.getUShort(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 2L));
                break;
            case 486:
                endTry();
                break;
            case 487:
                doCatch();
                break;
            case 488:
                doThrow();
                break;
            case 489:
                enterFinally(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 490:
                leaveFinally();
                break;
            case 491:
            case 492:
            case 493:
            case 494:
            case 495:
            case 496:
            case 497:
            case 498:
            case 499:
            case 500:
            case 501:
            case 502:
            case 503:
            case 504:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 505:
                operateStack("+");
                break;
            case 506:
                operateStack("-");
                break;
            case 507:
                operateStack("*");
                break;
            case 508:
                operateStack("/");
                break;
            case 509:
                operateStack("^");
                break;
            case 510:
                operateStackSingle("-");
                break;
            case 511:
                pushConstant(BufferHelper.getLongLong(pbFunction.getBuffer(), BufferHelper.getUInt(codeLine.pCodeParam, 0L)));
                break;
            case 512:
                pushLocalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 513:
                pushGlobalVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 515:
                pushSharedVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 517:
                endAssign();
                break;
            case 519:
                endAssign("+");
                break;
            case 520:
                endAssign("-");
                break;
            case 521:
                endAssign("*");
                break;
            case 522:
                endAssign2("++");
                break;
            case 523:
                endAssign2("--");
                break;
            case 524:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 525:
                callBuiltinFunction("abs");
                break;
            case 527:
                operateStack("=");
                break;
            case 528:
                operateStack("<>");
                break;
            case 529:
                operateStack(">");
                break;
            case 530:
                operateStack("<");
                break;
            case 531:
                operateStack(">=");
                break;
            case 532:
                operateStack("<=");
                break;
            case 533:
                callBuiltinFunction("mod", 2);
                break;
            case 534:
                callBuiltinFunction("min", 2);
                break;
            case 535:
                callBuiltinFunction("max", 2);
                break;
            case 539:
                callFunction(BufferHelper.getUInt(codeLine.pCodeParam, 0L), BufferHelper.getUShort(codeLine.pCodeParam, 4L), BufferHelper.getUShort(codeLine.pCodeParam, 6L));
                break;
            case 540:
                callGlobalFunction(BufferHelper.getUShort(codeLine.pCodeParam, 2L), BufferHelper.getUShort(codeLine.pCodeParam, 4L));
                break;
            case 542:
                pushInstanceVariable(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 543:
                cast(0);
                break;
            case 544:
                cast(BufferHelper.getUShort(codeLine.pCodeParam, 0L));
                break;
            case 546:
                cast(0);
                break;
            default:
                return false;
        }
        return true;
    }
}
