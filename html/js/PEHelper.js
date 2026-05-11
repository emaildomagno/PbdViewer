// PEHelper.js — port of Java PEHelper.java (browser-compatible, takes Uint8Array)
// Primary useful method: getOffset(rva) -> file offset (number)

export class PEHelper {
    /**
     * @param {Uint8Array} data — full PE file bytes
     */
    constructor(data) {
        this._data        = data;
        this._pos         = 0;
        this.openFile     = false;

        this._dosHeader       = null;
        this._dosStub         = null;
        this._peHeader        = null;
        this._optionalHeader  = null;
        this._optDirAttrib    = null;
        this._sectionTable    = null;
        this._exportDir       = null;
        this._importDir       = null;
        this._resourceDir     = null;

        try {
            this._loadFile();
            this.openFile = true;
        } catch (e) {
            // malformed PE — openFile stays false
        }
    }

    /**
     * Convert a virtual address (RVA) to a file offset.
     * @param {number} offset — the RVA
     * @returns {number} file offset, or Number.MAX_SAFE_INTEGER if not found
     */
    getOffset(offset) {
        if (!this._sectionTable) return Number.MAX_SAFE_INTEGER;
        const imageFileCode = this._getLong(this._optionalHeader.imageFileCode);
        for (const sec of this._sectionTable.sections) {
            const rva  = this._getLong(sec.sizeOfRawDataRVA);
            const size = this._getLong(sec.virtualAddress);
            const adjustedRva = rva + imageFileCode;
            if (offset >= adjustedRva && offset <= adjustedRva + size) {
                return this._getLong(sec.pointerToRawData) + (offset - adjustedRva);
            }
        }
        return Number.MAX_SAFE_INTEGER;
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    _loadFile() {
        this._loadDosHeader();
        this._loadDosStub();
        this._loadPEHeader();
        this._loadOptionalHeader();
        this._loadOptionalDirAttrib();
        this._loadSectionTable();
        this._loadExportDirectory();
        this._loadImportDirectory();
        this._loadResourceDirectory();
    }

    _loadDosHeader() {
        const h = {};
        h.e_magic    = this._readBytes(2);
        h.e_cblp     = this._readBytes(2);
        h.e_cp       = this._readBytes(2);
        h.e_crlc     = this._readBytes(2);
        h.e_cparhdr  = this._readBytes(2);
        h.e_minalloc = this._readBytes(2);
        h.e_maxalloc = this._readBytes(2);
        h.e_ss       = this._readBytes(2);
        h.e_sp       = this._readBytes(2);
        h.e_csum     = this._readBytes(2);
        h.e_ip       = this._readBytes(2);
        h.e_cs       = this._readBytes(2);
        h.e_rva      = this._readBytes(2);
        h.e_fg       = this._readBytes(2);
        h.e_bl1      = this._readBytes(8);
        h.e_oemid    = this._readBytes(2);
        h.e_oeminfo  = this._readBytes(2);
        h.e_bl2      = this._readBytes(20);
        h.e_PESTAR   = this._readBytes(2);
        this._dosHeader = h;
    }

    _loadDosStub() {
        const size = this._getLong(this._dosHeader.e_PESTAR) - this._pos;
        this._dosStub = { data: this._readBytes(size > 0 ? size : 0) };
    }

    _loadPEHeader() {
        const h = {};
        h.header                = this._readBytes(4);
        h.machine               = this._readBytes(2);
        h.numberOfSections      = this._readBytes(2);
        h.timeDateStamp         = this._readBytes(4);
        h.pointerToSymbolTable  = this._readBytes(4);
        h.numberOfSymbols       = this._readBytes(4);
        h.sizeOfOptionalHeader  = this._readBytes(2);
        h.characteristics       = this._readBytes(2);
        this._peHeader = h;
    }

    _loadOptionalHeader() {
        const h = {};
        h.magic                    = this._readBytes(2);
        h.majorLinkerVersion       = this._readBytes(1);
        h.minorLinkerVersion       = this._readBytes(1);
        h.sizeOfCode               = this._readBytes(4);
        h.sizeOfInitializedData    = this._readBytes(4);
        h.sizeOfUninitializedData  = this._readBytes(4);
        h.addressOfEntryPoint      = this._readBytes(4);
        h.baseOfCode               = this._readBytes(4);
        h.imageBase                = this._readBytes(4);
        h.imageFileCode            = this._readBytes(4);
        h.sectionAlign             = this._readBytes(4);
        h.fileAlign                = this._readBytes(4);
        h.majorOSV                 = this._readBytes(2);
        h.minorOSV                 = this._readBytes(2);
        h.majorImageVer            = this._readBytes(2);
        h.minorImageVer            = this._readBytes(2);
        h.majorSV                  = this._readBytes(2);
        h.minorSV                  = this._readBytes(2);
        h.unknown                  = this._readBytes(4);
        h.sizeOfImage              = this._readBytes(4);
        h.sizeOfHeards             = this._readBytes(4);
        h.checkSum                 = this._readBytes(4);
        h.subsystem                = this._readBytes(2);
        h.dllCharacteristics       = this._readBytes(2);
        h.bsize                    = this._readBytes(4);
        h.timeBsize                = this._readBytes(4);
        h.aucBsize                 = this._readBytes(4);
        h.sizeOfBsize              = this._readBytes(4);
        h.fuckBsize                = this._readBytes(4);
        h.directCount              = this._readBytes(4);
        this._optionalHeader = h;
    }

    _loadOptionalDirAttrib() {
        const count = this._getLong(this._optionalHeader.directCount);
        const dirs  = [];
        for (let i = 0; i < count; i++) {
            dirs.push({ dirRva: this._readBytes(4), dirSize: this._readBytes(4) });
        }
        this._optDirAttrib = { dirs };
    }

    _loadSectionTable() {
        const count    = this._getLong(this._peHeader.numberOfSections);
        const sections = [];
        for (let n = 0; n < count; n++) {
            const s = {};
            s.sectName             = this._readBytes(8);
            s.virtualAddress       = this._readBytes(4);
            s.sizeOfRawDataRVA     = this._readBytes(4);
            s.sizeOfRawDataSize    = this._readBytes(4);
            s.pointerToRawData     = this._readBytes(4);
            s.pointerToRelocations = this._readBytes(4);
            s.pointerToLinenumbers = this._readBytes(4);
            s.numberOfRelocations  = this._readBytes(2);
            s.numberOfLinenumbers  = this._readBytes(2);
            s.characteristics      = this._readBytes(4);
            sections.push(s);
        }
        this._sectionTable = { sections };
    }

    _loadExportDirectory() {
        if (!this._optDirAttrib.dirs.length) return;
        const dir0   = this._optDirAttrib.dirs[0];
        const rvaVal = this._getLong(dir0.dirRva);
        if (rvaVal === 0) return;
        for (const sec of this._sectionTable.sections) {
            const rvaBase = this._getLong(sec.sizeOfRawDataRVA);
            const rvaSize = this._getLong(sec.sizeOfRawDataSize);
            if (rvaVal < rvaBase || rvaVal >= rvaBase + rvaSize) continue;
            this._pos = rvaVal - rvaBase + this._getLong(sec.pointerToRawData);
            const ed = {};
            ed.characteristics         = this._readBytes(4);
            ed.timeDateStamp           = this._readBytes(4);
            ed.majorVersion            = this._readBytes(2);
            ed.minorVersion            = this._readBytes(2);
            ed.name                    = this._readBytes(4);
            ed.base                    = this._readBytes(4);
            ed.numberOfFunctions       = this._readBytes(4);
            ed.numberOfNames           = this._readBytes(4);
            ed.addressOfFunctions      = this._readBytes(4);
            ed.addressOfNames          = this._readBytes(4);
            ed.addressOfNameOrdinals   = this._readBytes(4);
            this._exportDir = ed;
            break;
        }
    }

    _loadImportDirectory() {
        if (this._optDirAttrib.dirs.length < 2) return;
        const dir1   = this._optDirAttrib.dirs[1];
        const rvaVal = this._getLong(dir1.dirRva);
        if (rvaVal === 0) return;
        // (import directory parsing not needed for getOffset — skip for brevity)
        this._importDir = {};
    }

    _loadResourceDirectory() {
        if (this._optDirAttrib.dirs.length < 3) return;
        const dir2   = this._optDirAttrib.dirs[2];
        const rvaVal = this._getLong(dir2.dirRva);
        if (rvaVal === 0) return;
        // (resource directory not needed for getOffset — skip for brevity)
        this._resourceDir = {};
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** @returns {Uint8Array} */
    _readBytes(n) {
        const slice = this._data.slice(this._pos, this._pos + n);
        this._pos += n;
        return slice;
    }

    /**
     * Interpret up to 4 bytes in LE as an unsigned number.
     * @param {Uint8Array} data
     * @returns {number}
     */
    _getLong(data) {
        if (!data || data.length > 4) return 0;
        let result = 0;
        for (let i = data.length - 1; i >= 0; i--) {
            result = (result * 256 + (data[i] & 0xFF));
        }
        return result >>> 0;
    }
}
