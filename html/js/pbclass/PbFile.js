// PbFile.js — port of Java PbFile.java (browser-compatible, no disk I/O)
// Receives file data as Uint8Array (pre-extracted from ZIP by the caller).
import * as BH from '../BufferHelper.js';
import { PbEntry } from './PbEntry.js';

const _ascii = new TextDecoder('ascii');
const _utf16 = new TextDecoder('utf-16le');

function _str(data, offset, len, unicode = false) {
    const slice = data.slice(offset, offset + len);
    return unicode ? _utf16.decode(slice) : _ascii.decode(slice);
}

export class PbFile {
    /**
     * Normal constructor: parse a PBD/PBL from in-memory Uint8Array.
     * @param {import('./PbProject.js').PbProject} project
     * @param {string} fileName — display name (no path needed)
     * @param {Uint8Array} data — full file content
     */
    constructor(project, fileName, data) {
        this.project  = project;
        this.fileName = fileName;
        this.filePath = fileName;
        /** @type {PbEntry[]} */
        this.entries  = [];

        const nodes = this._getNodeList(data);
        for (const node of nodes) {
            let current = node + 32;
            const buf32 = data.slice(node, node + 32);
            const uShort  = BH.getUShort(buf32, 20);
            const bytesPer = !project.isUnicode ? 1 : 2;
            const nameHdrSize = 4 + bytesPer * 4;
            const num3  = nameHdrSize + 16;
            const array = new Uint8Array(num3);

            for (let i = 0; i < uShort; i++) {
                array.set(data.slice(current, current + num3));
                const tag4 = _ascii.decode(array.slice(0, 4));
                const ver4 = project.getString(array, 4, bytesPer * 4);
                if (tag4 !== 'ENT*' || (ver4 !== '0600' && ver4 !== '0500')) {
                    throw new Error('格式错误');
                }
                const uInt  = BH.getUInt(array, nameHdrSize);
                const uInt2 = BH.getUInt(array, nameHdrSize + 4);
                const uShort2 = BH.getUShort(array, nameHdrSize + 14);
                current += num3;

                const buffer2 = data.slice(current, current + uShort2);
                current += uShort2;

                const entryName = project.getString(buffer2, 0, uShort2 - bytesPer);
                this.entries.push(new PbEntry(this, entryName, PbFile._readData(data, uInt, uInt2)));
            }
        }
    }

    /**
     * System-library constructor. In the browser we skip loading embedded
     * resources — just return an empty PbFile-like object.
     * @param {import('./PbProject.js').PbProject} project
     * @param {number} version
     * @returns {PbFile}
     */
    static createSystem(project, version) {
        const f = Object.create(PbFile.prototype);
        f.project  = project;
        f.fileName = 'system';
        f.filePath = null;
        f.entries  = [];
        // In the browser we cannot load embedded gzip resources — skip silently.
        return f;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * @param {Uint8Array} data
     * @param {number} start   — offset of first DAT* block
     * @param {number} size    — total expected bytes
     * @returns {Uint8Array}
     */
    static _readData(data, start, size) {
        const out  = new Uint8Array(size);
        let num    = 0;
        let offset = start;
        const hdr  = new Uint8Array(10);
        while (num < size) {
            hdr.set(data.slice(offset, offset + 10));
            if (_ascii.decode(hdr.slice(0, 4)) !== 'DAT*') break;
            const chunkSize = BH.getUShort(hdr, 8);
            out.set(data.slice(offset + 10, offset + 10 + chunkSize), num);
            num    += chunkSize;
            offset  = BH.getUInt(hdr, 4);
        }
        return out;
    }

    /**
     * Walk NOD* blocks and return their offsets.
     * @param {Uint8Array} data
     * @returns {number[]}
     */
    _getNodeList(data) {
        const list = [];
        const array = new Uint8Array(512);
        let foundAt = -1;
        let pos = 0;

        // Scan for HDR*PowerBuilder header
        while (pos + 512 <= data.length) {
            array.set(data.slice(pos, pos + 512));
            const tag4 = _ascii.decode(array.slice(0, 4));
            if (tag4 === 'HDR*') {
                const pb12 = _ascii.decode(array.slice(4, 16));
                if (pb12 === 'PowerBuilder') {
                    const ver4 = _ascii.decode(array.slice(18, 22));
                    if (ver4 === '0500') {
                        this.project.isPb5    = true;
                        foundAt = pos;
                        break;
                    }
                    if (ver4 === '0600') {
                        foundAt = pos;
                        break;
                    }
                }
                // try unicode header
                const pbU = _utf16.decode(array.slice(4, 28));
                const verU = _utf16.decode(array.slice(32, 40));
                if (pbU === 'PowerBuilder' && verU === '0600') {
                    this.project.isUnicode = true;
                    foundAt = pos;
                    break;
                }
            }
            pos += 512;
        }

        if (foundAt < 0) return list;

        // Skip past the HDR block
        let num = foundAt + (this.project.isUnicode ? 1536 : 1024);
        if (num + 512 > data.length) throw new Error('格式错误');
        array.set(data.slice(num, num + 512));
        if (_ascii.decode(array.slice(0, 4)) !== 'NOD*') throw new Error('格式错误');

        list.push(num);
        let uInt  = BH.getUInt(array, 4);
        let uInt2 = BH.getUInt(array, 12);

        // Forward chain
        while (uInt !== 0) {
            array.set(data.slice(uInt, uInt + 512));
            if (_ascii.decode(array.slice(0, 4)) !== 'NOD*') throw new Error('格式错误');
            list.push(uInt);
            uInt = BH.getUInt(array, 4);
        }
        // Backward chain
        while (uInt2 !== 0) {
            array.set(data.slice(uInt2, uInt2 + 512));
            if (_ascii.decode(array.slice(0, 4)) !== 'NOD*') throw new Error('格式错误');
            list.push(uInt2);
            uInt2 = BH.getUInt(array, 12);
        }

        return list;
    }
}
