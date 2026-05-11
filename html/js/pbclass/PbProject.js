// PbProject.js — port of Java PbProject.java (browser-compatible)
// Receives pre-extracted files as Map<string, Uint8Array> instead of reading disk.
import { PbFile } from './PbFile.js';
import { PbEnum } from './PbEnum.js';

const _latin1Dec = new TextDecoder('latin1');
const _utf16Dec  = new TextDecoder('utf-16le');

export class PbProject {
    constructor() {
        /** @type {PbFile[]} */
        this.files       = [];
        /** @type {Map<string, import('./PbObject.js').PbObject>} */
        this.objects     = new Map();
        /** @type {Map<number, import('./PbType.js').PbType>} */
        this.systemTypes = new Map();
        /** @type {Map<number, PbEnum>} */
        this.enums       = new Map();
        /** @type {import('./PbEntry.js').PbEntry|null} */
        this.systemEntry = null;
        /** @type {boolean} */
        this.isUnicode   = false;
        /** @type {boolean} */
        this.isPb5       = false;
        /** @type {number} */
        this.version     = 0;
        /** @type {boolean} */
        this.isDebug     = false;

        // Internal set to deduplicate library paths already processed
        this._loadedPaths = new Set();
        // Queue of {fileName, data} pairs added while iterating
        this._fileQueue   = [];
    }

    /**
     * Main async factory.
     * @param {string} entryFileName   — lowercased filename of the entry file
     * @param {Map<string, Uint8Array>} allFiles — Map<lowercased-name, Uint8Array>
     * @returns {Promise<PbProject>}
     */
    static async create(entryFileName, allFiles) {
        const project = new PbProject();
        project._allFiles = allFiles;

        // Find the entry file in allFiles
        const entryKey = entryFileName.toLowerCase();
        const entryData = allFiles.get(entryKey);
        if (!entryData) {
            throw new Error(`Entry file not found: ${entryFileName}`);
        }

        // Parse the entry PbFile (this may queue more files via onNewLibrary)
        const entryFile = new PbFile(project, entryFileName, entryData);
        project.files.push(entryFile);
        project._loadedPaths.add(entryKey);

        // Drain the queue — files added by onNewLibrary / onSystemLibrary
        let i = 0;
        while (i < project.files.length) {
            for (const entry of project.files[i].entries) {
                entry.parseObject();
            }
            i++;

            // Process any files added to the queue during parsing
            for (const { fileName, data } of project._fileQueue) {
                project.files.push(new PbFile(project, fileName, data));
            }
            project._fileQueue = [];
        }

        if (project.systemEntry !== null) project.systemEntry.parseInherit();

        for (const pbFile of project.files) {
            for (const entry of pbFile.entries) {
                entry.parseInherit();
            }
        }

        return project;
    }

    // ── Callbacks from PbEntry / PbFile ──────────────────────────────────────

    /**
     * Called when a library path is encountered.
     * @param {string} libpath — bare filename (not full path)
     * @param {boolean} isFullPath — ignored in browser (we use allFiles map)
     */
    onNewLibrary(libpath, isFullPath) {
        const key = libpath.toLowerCase();
        if (this._loadedPaths.has(key)) return;
        this._loadedPaths.add(key);

        const data = this._allFiles ? this._allFiles.get(key) : undefined;
        if (data) {
            // queue for after current iteration
            this._fileQueue.push({ fileName: libpath, data });
        }
    }

    /**
     * Called when a version code is seen in a pbd entry header.
     * @param {number} version
     * @returns {null}  (system library loading is skipped in browser)
     */
    onSystemLibrary(version) {
        if (this.version === 0) {
            this.version = version;
            // Attempt to create a (stub) system PbFile — it will be empty in browser
            const sysFile = PbFile.createSystem(this, version);
            // Only push if it has entries (currently never in browser)
            if (sysFile.entries.length > 0) this.files.push(sysFile);
        } else if (this.version !== version) {
            console.warn('two version libraries in one project??');
        }
        return null;
    }

    /** @param {import('./PbEntry.js').PbEntry} pbEntry */
    onSystemEntry(pbEntry) {
        this.systemEntry = pbEntry;
    }

    /** @param {import('./PbType.js').PbType} pbType */
    onNewSystemType(pbType) {
        this.systemTypes.set(pbType.index, pbType);
    }

    /**
     * @param {import('./PbObject.js').PbObject} pbObject
     * @param {string|null} [name]
     */
    onNewObject(pbObject, name = null) {
        this.objects.set(name !== null ? name : pbObject.type.name, pbObject);
    }

    /**
     * @param {import('./PbType.js').PbType} type
     * @param {number} index
     * @param {string} itemName
     */
    onNewEnumItem(type, index, itemName) {
        if (!this.enums.has(type.index)) {
            const e = new PbEnum();
            e.index = type.index;
            e.name  = type.name;
            this.enums.set(type.index, e);
        }
        this.enums.get(type.index).items.set(index, itemName + '!');
    }

    // ── String helpers ────────────────────────────────────────────────────────

    /**
     * @param {Uint8Array} buffer
     * @param {number} [offset]
     * @param {number} [size]
     * @returns {string}
     */
    getString(buffer, offset = 0, size = buffer.length - offset) {
        const slice = buffer.slice(offset, offset + size);
        return this.isUnicode ? _utf16Dec.decode(slice) : _latin1Dec.decode(slice);
    }
}
