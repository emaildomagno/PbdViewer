// BufferHelper.js — port of Java BufferHelper.java
// All functions operate on Uint8Array buffers with little-endian byte order.

const _utf16leDecoder = new TextDecoder('utf-16le');
const _asciiDecoder   = new TextDecoder('latin1');

/**
 * Mask offset to 31 bits (mirrors Java's `offset &= 0x7FFFFFFF`).
 * All public functions accept offset as a regular JS number.
 */
function _off(offset) {
    return (offset | 0) & 0x7FFFFFFF;
}

// ─── Integer reads ────────────────────────────────────────────────────────────

export function getUShort(buffer, offset) {
    offset = _off(offset);
    return (buffer[offset] | (buffer[offset + 1] << 8)) >>> 0;
}

export function getUInt(buffer, offset) {
    offset = _off(offset);
    return (
        (buffer[offset]     |
        (buffer[offset + 1] << 8) |
        (buffer[offset + 2] << 16) |
        (buffer[offset + 3] << 24)) >>> 0
    );
}

// ─── Buffer slice ─────────────────────────────────────────────────────────────

export function getBuffer(buffer, offset, size) {
    offset = _off(offset);
    size = Math.min(size, buffer.length - offset);
    if (size <= 0) return new Uint8Array(0);
    return buffer.slice(offset, offset + size);
}

// ─── Hex string ───────────────────────────────────────────────────────────────

export function getHexString(buffer, offset, size) {
    if (offset === undefined) offset = 0;
    if (size   === undefined) size   = buffer.length - offset;
    const sub = getBuffer(buffer, offset, size);
    const parts = [];
    for (let i = 0; i < sub.length; i++) {
        parts.push(sub[i].toString(16).padStart(2, '0').toUpperCase());
    }
    return parts.join(' ');
}

// ─── String reads ─────────────────────────────────────────────────────────────

export function getString(isUnicode, buffer, offset) {
    offset = _off(offset);
    let end = offset;
    if (isUnicode) {
        while (end + 1 < buffer.length && (buffer[end] !== 0 || buffer[end + 1] !== 0)) {
            end += 2;
        }
    } else {
        while (end < buffer.length && buffer[end] !== 0) {
            end++;
        }
    }
    if (end === offset) return '';
    const slice = buffer.subarray(offset, end);
    return isUnicode ? _utf16leDecoder.decode(slice) : _asciiDecoder.decode(slice);
}

export function getEscapeString(isUnicode, buffer, offset) {
    const s = getString(isUnicode, buffer, offset)
        .replace(/~/g,  '~~')
        .replace(/\r/g, '~r')
        .replace(/\n/g, '~n')
        .replace(/\t/g, '~t')
        .replace(/"/g,  '~"');
    return `"${s}"`;
}

// ─── Numeric value reads ──────────────────────────────────────────────────────

export function getDecimal(buffer, offset) {
    offset = _off(offset);
    const sign  = getUShort(buffer, offset);       // offset+0
    const scale = buffer[offset + 2] & 0xFF;       // offset+2

    // lo=4 bytes @ offset+4, mid=4 bytes @ offset+8, hi=2 bytes @ offset+12
    const lo  = BigInt(getUInt(buffer, offset + 4));
    const mid = BigInt(getUInt(buffer, offset + 8));
    const hi  = BigInt(getUShort(buffer, offset + 12));

    let value = lo + (mid << 32n) + (hi << 64n);

    let text = value.toString();

    if (scale > 0) {
        if (text.length <= scale) {
            text = text.padStart(scale + 1, '0');
        }
        text = text.slice(0, text.length - scale) + '.' + text.slice(text.length - scale);
        // trimTrailingZeros
        let i = text.length;
        while (i > 0 && text[i - 1] === '0') i--;
        text = text.slice(0, i);
        if (text.endsWith('.')) text += '0';
    }

    if (sign > 0) text = '-' + text;
    return text;
}

export function getReal(code) {
    // Reinterpret the 32-bit uint as an IEEE-754 float32
    const buf = new Uint8Array(4);
    buf[0] =  code        & 0xFF;
    buf[1] = (code >>  8) & 0xFF;
    buf[2] = (code >> 16) & 0xFF;
    buf[3] = (code >> 24) & 0xFF;
    return new Float32Array(buf.buffer)[0].toString();
}

export function getDouble(buffer, offset) {
    offset = _off(offset);
    // Copy 8 bytes into aligned buffer
    const aligned = new Uint8Array(8);
    aligned.set(buffer.subarray(offset, offset + 8));
    return new Float64Array(aligned.buffer)[0].toString();
}

export function getLongLong(buffer, offset) {
    offset = _off(offset);
    const aligned = new Uint8Array(8);
    aligned.set(buffer.subarray(offset, offset + 8));
    return new BigInt64Array(aligned.buffer)[0].toString();
}

// ─── Date / Time reads ────────────────────────────────────────────────────────

export function getDate(buffer, offset) {
    offset = _off(offset);
    const year  = getUShort(buffer, offset + 4) + 1900;
    const month = (buffer[offset + 6] & 0xFF) + 1;
    const day   = buffer[offset + 7] & 0xFF;
    return `${year}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}`;
}

export function getTime(buffer, offset) {
    offset = _off(offset);
    const hh = buffer[offset + 8]  & 0xFF;
    const mm = buffer[offset + 9]  & 0xFF;
    const ss = buffer[offset + 10] & 0xFF;
    let text = `${String(hh).padStart(2,'0')}:${String(mm).padStart(2,'0')}:${String(ss).padStart(2,'0')}`;
    const ms = Math.floor(getUInt(buffer, offset) / 1000);
    if (ms !== 0) {
        text += `.${String(ms).padStart(3,'0')}`;
    }
    return text;
}

export function getDateTime(buffer, offset) {
    offset = _off(offset);
    return `datetime(${getDate(buffer, offset)},${getTime(buffer, offset)})`;
}

// ─── Cursor ───────────────────────────────────────────────────────────────────

export function getCursor(isUnicode, bytes, offset, paramList) {
    offset = _off(offset);
    // If the value at +8 is not 0xFFFFFFFF, recurse to that offset
    const next = getUInt(bytes, offset + 8);
    if (next !== 0xFFFFFFFF) {
        return getCursor(isUnicode, bytes, next, paramList);
    }
    const strOffset = getUInt(bytes, offset + 24);
    const str = getString(isUnicode, bytes, strOffset);
    let text = '';
    if (paramList != null) {
        let paramPtr = getUInt(bytes, offset + 16);
        let pos = 0;
        for (const param of paramList) {
            const uShort  = getUShort(bytes, paramPtr);
            const uShort2 = getUShort(bytes, paramPtr + 2);
            paramPtr += 4;
            if (uShort === 0 && uShort2 === 0) break;
            text += str.slice(pos, uShort) + `:${param}`;
            pos = uShort2;
        }
        text += str.slice(pos);
    }
    return text;
}
