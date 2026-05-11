package com.pbdviewer.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PE (Portable Executable) file parser.
 * Converts C# PEHelper.cs to Java. The getPETable() method returns null since
 * Java has no DataSet/DataTable equivalent; the primary use is getOffset().
 */
public class PEHelper {

    // -------------------------------------------------------------------------
    // Inner data structure classes
    // -------------------------------------------------------------------------

    private static class DosHeader {
        byte[] e_magic = new byte[2];
        byte[] e_cblp = new byte[2];
        byte[] e_cp = new byte[2];
        byte[] e_crlc = new byte[2];
        byte[] e_cparhdr = new byte[2];
        byte[] e_minalloc = new byte[2];
        byte[] e_maxalloc = new byte[2];
        byte[] e_ss = new byte[2];
        byte[] e_sp = new byte[2];
        byte[] e_csum = new byte[2];
        byte[] e_ip = new byte[2];
        byte[] e_cs = new byte[2];
        byte[] e_rva = new byte[2];
        byte[] e_fg = new byte[2];
        byte[] e_bl1 = new byte[8];
        byte[] e_oemid = new byte[2];
        byte[] e_oeminfo = new byte[2];
        byte[] e_bl2 = new byte[20];
        byte[] e_PESTAR = new byte[2];
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class DosStub {
        byte[] dosStubData;
        long fileStartIndex;
        long fileEndIndex;

        DosStub(long size) {
            dosStubData = new byte[(int) size];
        }
    }

    private static class PEHeader {
        byte[] header = new byte[4];
        byte[] machine = new byte[2];
        byte[] numberOfSections = new byte[2];
        byte[] timeDateStamp = new byte[4];
        byte[] pointerToSymbolTable = new byte[4];
        byte[] numberOfSymbols = new byte[4];
        byte[] sizeOfOptionalHeader = new byte[2];
        byte[] characteristics = new byte[2];
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class OptionalHeader {
        byte[] magic = new byte[2];
        byte[] majorLinkerVersion = new byte[1];
        byte[] minorLinkerVersion = new byte[1];
        byte[] sizeOfCode = new byte[4];
        byte[] sizeOfInitializedData = new byte[4];
        byte[] sizeOfUninitializedData = new byte[4];
        byte[] addressOfEntryPoint = new byte[4];
        byte[] baseOfCode = new byte[4];
        byte[] imageBase = new byte[4];
        byte[] imageFileCode = new byte[4];
        byte[] sectionAlign = new byte[4];
        byte[] fileAlign = new byte[4];
        byte[] majorOSV = new byte[2];
        byte[] minorOSV = new byte[2];
        byte[] majorImageVer = new byte[2];
        byte[] minorImageVer = new byte[2];
        byte[] majorSV = new byte[2];
        byte[] minorSV = new byte[2];
        byte[] unknown = new byte[4];
        byte[] sizeOfImage = new byte[4];
        byte[] sizeOfHeards = new byte[4];
        byte[] checkSum = new byte[4];
        byte[] subsystem = new byte[2];
        byte[] dllCharacteristics = new byte[2];
        byte[] bsize = new byte[4];
        byte[] timeBsize = new byte[4];
        byte[] aucBsize = new byte[4];
        byte[] sizeOfBsize = new byte[4];
        byte[] fuckBsize = new byte[4];
        byte[] directCount = new byte[4];
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class OptionalDirAttrib {
        static class DirAttrib {
            byte[] dirRva = new byte[4];
            byte[] dirSize = new byte[4];
        }

        List<DirAttrib> dirByte = new ArrayList<>();
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class SectionTable {
        static class SectionData {
            byte[] sectName = new byte[8];
            byte[] virtualAddress = new byte[4];
            byte[] sizeOfRawDataRVA = new byte[4];
            byte[] sizeOfRawDataSize = new byte[4];
            byte[] pointerToRawData = new byte[4];
            byte[] pointerToRelocations = new byte[4];
            byte[] pointerToLinenumbers = new byte[4];
            byte[] numberOfRelocations = new byte[2];
            byte[] numberOfLinenumbers = new byte[2];
            byte[] characteristics = new byte[4];
        }

        List<SectionData> section = new ArrayList<>();
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class ExportDirectory {
        byte[] characteristics = new byte[4];
        byte[] timeDateStamp = new byte[4];
        byte[] majorVersion = new byte[2];
        byte[] minorVersion = new byte[2];
        byte[] name = new byte[4];
        byte[] base = new byte[4];
        byte[] numberOfFunctions = new byte[4];
        byte[] numberOfNames = new byte[4];
        byte[] addressOfFunctions = new byte[4];
        byte[] addressOfNames = new byte[4];
        byte[] addressOfNameOrdinals = new byte[4];
        List<byte[]> addressOfFunctionsList = new ArrayList<>();
        List<byte[]> addressOfNamesList = new ArrayList<>();
        List<byte[]> addressOfNameOrdinalsList = new ArrayList<>();
        List<byte[]> nameList = new ArrayList<>();
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class ImportDirectory {
        static class ImportDate {
            static class FunctionList {
                byte[] originalFirst = new byte[4];
                byte[] functionName;
                byte[] functionHead = new byte[2];
            }

            byte[] originalFirstThunk = new byte[4];
            byte[] timeDateStamp = new byte[4];
            byte[] forwarderChain = new byte[4];
            byte[] name = new byte[4];
            byte[] firstThunk = new byte[4];
            byte[] dllName;
            List<FunctionList> dllFunctionList = new ArrayList<>();
        }

        List<ImportDate> importList = new ArrayList<>();
        long fileStartIndex;
        long fileEndIndex;
    }

    private static class ResourceDirectory {
        static class DirectoryEntry {
            static class DataEntry {
                byte[] resourRVA = new byte[4];
                byte[] resourSize = new byte[4];
                byte[] resourTest = new byte[4];
                byte[] resourWen = new byte[4];
                long fileStartIndex;
                long fileEndIndex;
            }

            byte[] name = new byte[4];
            byte[] id = new byte[4];
            List<DataEntry> dataEntryList = new ArrayList<>();
            List<ResourceDirectory> nodeDirectoryList = new ArrayList<>();
        }

        byte[] characteristics = new byte[4];
        byte[] timeDateStamp = new byte[4];
        byte[] majorVersion = new byte[2];
        byte[] minorVersion = new byte[2];
        byte[] numberOfNamedEntries = new byte[2];
        byte[] numberOfIdEntries = new byte[2];
        byte[] nodeName;
        List<DirectoryEntry> entryList = new ArrayList<>();
        long fileStartIndex;
        long fileEndIndex;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private byte[] peFileByte;
    private boolean openFile;
    private long peFileIndex;

    private DosHeader dosHeader;
    private DosStub dosStub;
    private PEHeader peHeader;
    private OptionalHeader optionalHeader;
    private OptionalDirAttrib optionalDirAttrib;
    private SectionTable sectionTable;
    private ExportDirectory exportDirectory;
    private ImportDirectory importDirectory;
    private ResourceDirectory resourceDirectory;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public PEHelper(InputStream stream) throws IOException {
        openFile = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = stream.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        peFileByte = baos.toByteArray();
        loadFile();
        openFile = true;
    }

    public PEHelper(String filename) throws IOException {
        openFile = false;
        try (FileInputStream fis = new FileInputStream(filename)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            peFileByte = baos.toByteArray();
        }
        loadFile();
        openFile = true;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isOpenFile() {
        return openFile;
    }

    /**
     * Converts a virtual address (RVA offset from a section) to a file offset.
     */
    public long getOffset(long offset) {
        if (sectionTable == null) return Long.MAX_VALUE;
        for (SectionTable.SectionData item : sectionTable.section) {
            long rva = getLong(item.sizeOfRawDataRVA);
            long size = getLong(item.sizeOfRawDataSize);
            long imageFileCodeVal = getLong(optionalHeader.imageFileCode);
            long adjustedRva = rva + imageFileCodeVal;
            if (offset >= adjustedRva && offset <= adjustedRva + getLong(item.virtualAddress)) {
                return getLong(item.pointerToRawData) + (offset - adjustedRva);
            }
        }
        return Long.MAX_VALUE;
    }

    /**
     * Returns null - Java has no DataSet/DataTable equivalent.
     * The PE structure data is used internally via getOffset().
     */
    public Object getPETable() {
        return null;
    }

    // -------------------------------------------------------------------------
    // Private loading methods
    // -------------------------------------------------------------------------

    private void loadFile() {
        loadDosHeader();
        loadDosStub();
        loadPEHeader();
        loadOptionalHeader();
        loadOptionalDirAttrib();
        loadSectionTable();
        loadExportDirectory();
        loadImportDirectory();
        loadResourceDirectory();
    }

    private void loadDosHeader() {
        dosHeader = new DosHeader();
        dosHeader.fileStartIndex = peFileIndex;
        loadBytes(dosHeader.e_magic);
        loadBytes(dosHeader.e_cblp);
        loadBytes(dosHeader.e_cp);
        loadBytes(dosHeader.e_crlc);
        loadBytes(dosHeader.e_cparhdr);
        loadBytes(dosHeader.e_minalloc);
        loadBytes(dosHeader.e_maxalloc);
        loadBytes(dosHeader.e_ss);
        loadBytes(dosHeader.e_sp);
        loadBytes(dosHeader.e_csum);
        loadBytes(dosHeader.e_ip);
        loadBytes(dosHeader.e_cs);
        loadBytes(dosHeader.e_rva);
        loadBytes(dosHeader.e_fg);
        loadBytes(dosHeader.e_bl1);
        loadBytes(dosHeader.e_oemid);
        loadBytes(dosHeader.e_oeminfo);
        loadBytes(dosHeader.e_bl2);
        loadBytes(dosHeader.e_PESTAR);
        dosHeader.fileEndIndex = peFileIndex;
    }

    private void loadDosStub() {
        long size = getLong(dosHeader.e_PESTAR) - peFileIndex;
        dosStub = new DosStub(size);
        dosStub.fileStartIndex = peFileIndex;
        loadBytes(dosStub.dosStubData);
        dosStub.fileEndIndex = peFileIndex;
    }

    private void loadPEHeader() {
        peHeader = new PEHeader();
        peHeader.fileStartIndex = peFileIndex;
        loadBytes(peHeader.header);
        loadBytes(peHeader.machine);
        loadBytes(peHeader.numberOfSections);
        loadBytes(peHeader.timeDateStamp);
        loadBytes(peHeader.pointerToSymbolTable);
        loadBytes(peHeader.numberOfSymbols);
        loadBytes(peHeader.sizeOfOptionalHeader);
        loadBytes(peHeader.characteristics);
        peHeader.fileEndIndex = peFileIndex;
    }

    private void loadOptionalHeader() {
        optionalHeader = new OptionalHeader();
        optionalHeader.fileStartIndex = peFileIndex;
        loadBytes(optionalHeader.magic);
        loadBytes(optionalHeader.majorLinkerVersion);
        loadBytes(optionalHeader.minorLinkerVersion);
        loadBytes(optionalHeader.sizeOfCode);
        loadBytes(optionalHeader.sizeOfInitializedData);
        loadBytes(optionalHeader.sizeOfUninitializedData);
        loadBytes(optionalHeader.addressOfEntryPoint);
        loadBytes(optionalHeader.baseOfCode);
        loadBytes(optionalHeader.imageBase);
        loadBytes(optionalHeader.imageFileCode);
        loadBytes(optionalHeader.sectionAlign);
        loadBytes(optionalHeader.fileAlign);
        loadBytes(optionalHeader.majorOSV);
        loadBytes(optionalHeader.minorOSV);
        loadBytes(optionalHeader.majorImageVer);
        loadBytes(optionalHeader.minorImageVer);
        loadBytes(optionalHeader.majorSV);
        loadBytes(optionalHeader.minorSV);
        loadBytes(optionalHeader.unknown);
        loadBytes(optionalHeader.sizeOfImage);
        loadBytes(optionalHeader.sizeOfHeards);
        loadBytes(optionalHeader.checkSum);
        loadBytes(optionalHeader.subsystem);
        loadBytes(optionalHeader.dllCharacteristics);
        loadBytes(optionalHeader.bsize);
        loadBytes(optionalHeader.timeBsize);
        loadBytes(optionalHeader.aucBsize);
        loadBytes(optionalHeader.sizeOfBsize);
        loadBytes(optionalHeader.fuckBsize);
        loadBytes(optionalHeader.directCount);
        optionalHeader.fileEndIndex = peFileIndex;
    }

    private void loadOptionalDirAttrib() {
        optionalDirAttrib = new OptionalDirAttrib();
        optionalDirAttrib.fileStartIndex = peFileIndex;
        long count = getLong(optionalHeader.directCount);
        for (int i = 0; i < count; i++) {
            OptionalDirAttrib.DirAttrib dirAttrib = new OptionalDirAttrib.DirAttrib();
            loadBytes(dirAttrib.dirRva);
            loadBytes(dirAttrib.dirSize);
            optionalDirAttrib.dirByte.add(dirAttrib);
        }
        optionalDirAttrib.fileEndIndex = peFileIndex;
    }

    private void loadSectionTable() {
        sectionTable = new SectionTable();
        long count = getLong(peHeader.numberOfSections);
        sectionTable.fileStartIndex = peFileIndex;
        for (long n = 0; n < count; n++) {
            SectionTable.SectionData sectionData = new SectionTable.SectionData();
            loadBytes(sectionData.sectName);
            loadBytes(sectionData.virtualAddress);
            loadBytes(sectionData.sizeOfRawDataRVA);
            loadBytes(sectionData.sizeOfRawDataSize);
            loadBytes(sectionData.pointerToRawData);
            loadBytes(sectionData.pointerToRelocations);
            loadBytes(sectionData.pointerToLinenumbers);
            loadBytes(sectionData.numberOfRelocations);
            loadBytes(sectionData.numberOfLinenumbers);
            loadBytes(sectionData.characteristics);
            sectionTable.section.add(sectionData);
        }
        sectionTable.fileEndIndex = peFileIndex;
    }

    private void loadExportDirectory() {
        if (optionalDirAttrib.dirByte.isEmpty()) return;
        OptionalDirAttrib.DirAttrib dirAttrib = optionalDirAttrib.dirByte.get(0);
        if (getLong(dirAttrib.dirRva) == 0L) return;
        long rvaVal = getLong(dirAttrib.dirRva);
        exportDirectory = new ExportDirectory();
        for (int i = 0; i < sectionTable.section.size(); i++) {
            SectionTable.SectionData sectionData = sectionTable.section.get(i);
            long rvaBase = getLong(sectionData.sizeOfRawDataRVA);
            long rvaSize = getLong(sectionData.sizeOfRawDataSize);
            if (rvaVal < rvaBase || rvaVal >= rvaBase + rvaSize) continue;
            peFileIndex = rvaVal - rvaBase + getLong(sectionData.pointerToRawData);
            exportDirectory.fileStartIndex = peFileIndex;
            exportDirectory.fileEndIndex = peFileIndex + getLong(dirAttrib.dirSize);
            loadBytes(exportDirectory.characteristics);
            loadBytes(exportDirectory.timeDateStamp);
            loadBytes(exportDirectory.majorVersion);
            loadBytes(exportDirectory.minorVersion);
            loadBytes(exportDirectory.name);
            loadBytes(exportDirectory.base);
            loadBytes(exportDirectory.numberOfFunctions);
            loadBytes(exportDirectory.numberOfNames);
            loadBytes(exportDirectory.addressOfFunctions);
            loadBytes(exportDirectory.addressOfNames);
            loadBytes(exportDirectory.addressOfNameOrdinals);
            peFileIndex = getLong(exportDirectory.addressOfFunctions) - rvaBase + getLong(sectionData.pointerToRawData);
            long num = getLong(exportDirectory.addressOfNames) - rvaBase + getLong(sectionData.pointerToRawData);
            long num2 = (num - peFileIndex) / 4;
            for (long n3 = 0; n3 < num2; n3++) {
                byte[] data = new byte[4];
                loadBytes(data);
                exportDirectory.addressOfFunctionsList.add(data);
            }
            peFileIndex = num;
            num = getLong(exportDirectory.addressOfNameOrdinals) - rvaBase + getLong(sectionData.pointerToRawData);
            num2 = (num - peFileIndex) / 4;
            for (long n4 = 0; n4 < num2; n4++) {
                byte[] data = new byte[4];
                loadBytes(data);
                exportDirectory.addressOfNamesList.add(data);
            }
            peFileIndex = num;
            num = getLong(exportDirectory.name) - rvaBase + getLong(sectionData.pointerToRawData);
            num2 = (num - peFileIndex) / 2;
            for (long n5 = 0; n5 < num2; n5++) {
                byte[] data = new byte[2];
                loadBytes(data);
                exportDirectory.addressOfNameOrdinalsList.add(data);
            }
            peFileIndex = num;
            long num6 = 0;
            while (true) {
                if (peFileByte[(int)(peFileIndex + num6)] == 0) {
                    if (peFileByte[(int)(peFileIndex + num6 + 1)] == 0) break;
                    byte[] data = new byte[(int) num6];
                    loadBytes(data);
                    exportDirectory.nameList.add(data);
                    peFileIndex++;
                    num6 = 0;
                }
                num6++;
            }
            break;
        }
    }

    private void loadImportDirectory() {
        if (optionalDirAttrib.dirByte.size() < 2) return;
        OptionalDirAttrib.DirAttrib dirAttrib = optionalDirAttrib.dirByte.get(1);
        long rvaVal = getLong(dirAttrib.dirRva);
        if (rvaVal == 0L) return;
        long dirSize = getLong(dirAttrib.dirSize);
        importDirectory = new ImportDirectory();
        long num = 0, num2 = 0, num3 = 0, num4 = 0;
        for (int i = 0; i < sectionTable.section.size(); i++) {
            SectionTable.SectionData sectionData = sectionTable.section.get(i);
            num3 = getLong(sectionData.sizeOfRawDataRVA);
            num4 = getLong(sectionData.sizeOfRawDataSize);
            if (rvaVal >= num3 && rvaVal < num3 + num4) {
                num = getLong(sectionData.sizeOfRawDataRVA);
                num2 = getLong(sectionData.pointerToRawData);
                peFileIndex = rvaVal - num + num2;
                importDirectory.fileStartIndex = peFileIndex;
                importDirectory.fileEndIndex = peFileIndex + dirSize;
                break;
            }
        }
        if (num == 0 && num2 == 0) return;
        while (true) {
            ImportDirectory.ImportDate importDate = new ImportDirectory.ImportDate();
            loadBytes(importDate.originalFirstThunk);
            loadBytes(importDate.timeDateStamp);
            loadBytes(importDate.forwarderChain);
            loadBytes(importDate.name);
            loadBytes(importDate.firstThunk);
            if (getLong(importDate.name) == 0L) break;
            importDirectory.importList.add(importDate);
        }
        for (int j = 0; j < importDirectory.importList.size(); j++) {
            ImportDirectory.ImportDate importDate2 = importDirectory.importList.get(j);
            peFileIndex = getLong(importDate2.name) - num + num2;
            long nameLen = 0;
            while (peFileByte[(int)(peFileIndex + nameLen)] != 0) nameLen++;
            importDate2.dllName = new byte[(int) nameLen];
            loadBytes(importDate2.dllName);
        }
        for (int k = 0; k < importDirectory.importList.size(); k++) {
            ImportDirectory.ImportDate importDate3 = importDirectory.importList.get(k);
            peFileIndex = getLong(importDate3.originalFirstThunk) - num + num2;
            while (true) {
                ImportDirectory.ImportDate.FunctionList fl = new ImportDirectory.ImportDate.FunctionList();
                loadBytes(fl.originalFirst);
                long val = getLong(fl.originalFirst);
                if (val == 0L) break;
                long savedIndex = peFileIndex;
                peFileIndex = val - num + num2;
                if (val >= num3 && val < num3 + num4) {
                    int nb = 0;
                    loadBytes(fl.functionHead);
                    while (peFileByte[(int)(peFileIndex + nb)] != 0) nb++;
                    byte[] fnData = new byte[nb];
                    loadBytes(fnData);
                    fl.functionName = fnData;
                } else {
                    fl.functionName = new byte[1];
                }
                peFileIndex = savedIndex;
                importDate3.dllFunctionList.add(fl);
            }
        }
    }

    private void loadResourceDirectory() {
        if (optionalDirAttrib.dirByte.size() < 3) return;
        OptionalDirAttrib.DirAttrib dirAttrib = optionalDirAttrib.dirByte.get(2);
        long rvaVal = getLong(dirAttrib.dirRva);
        if (rvaVal == 0L) return;
        long dirSize = getLong(dirAttrib.dirSize);
        resourceDirectory = new ResourceDirectory();
        long num = 0, num2 = 0, num3 = 0, num4 = 0, pEIndex = 0;
        for (int i = 0; i < sectionTable.section.size(); i++) {
            SectionTable.SectionData sectionData = sectionTable.section.get(i);
            num3 = getLong(sectionData.sizeOfRawDataRVA);
            num4 = getLong(sectionData.sizeOfRawDataSize);
            if (rvaVal >= num3 && rvaVal < num3 + num4) {
                num = getLong(sectionData.sizeOfRawDataRVA);
                num2 = getLong(sectionData.pointerToRawData);
                peFileIndex = rvaVal - num + num2;
                pEIndex = peFileIndex;
                resourceDirectory.fileStartIndex = peFileIndex;
                resourceDirectory.fileEndIndex = peFileIndex + dirSize;
                break;
            }
        }
        if (num != 0L || num2 != 0L) {
            addResourceNode(resourceDirectory, pEIndex, 0L, num3);
        }
    }

    private void addResourceNode(ResourceDirectory node, long pEIndex, long rva, long resourSectRva) {
        peFileIndex = pEIndex + rva;
        loadBytes(node.characteristics);
        loadBytes(node.timeDateStamp);
        loadBytes(node.majorVersion);
        loadBytes(node.minorVersion);
        loadBytes(node.numberOfNamedEntries);
        loadBytes(node.numberOfIdEntries);
        long namedCount = getLong(node.numberOfNamedEntries);
        for (int i = 0; i < namedCount; i++) {
            ResourceDirectory.DirectoryEntry entry = new ResourceDirectory.DirectoryEntry();
            loadBytes(entry.name);
            loadBytes(entry.id);
            byte[] arr = new byte[]{entry.name[0], entry.name[1]};
            long nameOffset = getLong(arr) + pEIndex;
            arr[0] = peFileByte[(int) nameOffset];
            arr[1] = peFileByte[(int)(nameOffset + 1)];
            long nameLen = getLong(arr);
            node.nodeName = new byte[(int)(nameLen * 2)];
            for (int j = 0; j < node.nodeName.length; j++) {
                node.nodeName[j] = peFileByte[(int)(nameOffset + 2 + j)];
            }
            arr[0] = entry.id[2];
            arr[1] = entry.id[3];
            long savedIndex = peFileIndex;
            if (getLong(arr) == 0L) {
                arr[0] = entry.id[0];
                arr[1] = entry.id[1];
                peFileIndex = getLong(arr) + pEIndex;
                ResourceDirectory.DirectoryEntry.DataEntry dataEntry = new ResourceDirectory.DirectoryEntry.DataEntry();
                loadBytes(dataEntry.resourRVA);
                loadBytes(dataEntry.resourSize);
                loadBytes(dataEntry.resourTest);
                loadBytes(dataEntry.resourWen);
                peFileIndex = savedIndex;
                entry.dataEntryList.add(dataEntry);
            } else {
                arr[0] = entry.id[0];
                arr[1] = entry.id[1];
                ResourceDirectory childDir = new ResourceDirectory();
                entry.nodeDirectoryList.add(childDir);
                addResourceNode(childDir, pEIndex, getLong(arr), resourSectRva);
            }
            peFileIndex = savedIndex;
            node.entryList.add(entry);
        }
        long idCount = getLong(node.numberOfIdEntries);
        for (int k = 0; k < idCount; k++) {
            ResourceDirectory.DirectoryEntry entry2 = new ResourceDirectory.DirectoryEntry();
            loadBytes(entry2.name);
            loadBytes(entry2.id);
            byte[] arr2 = new byte[]{entry2.id[2], entry2.id[3]};
            long savedIndex2 = peFileIndex;
            if (getLong(arr2) == 0L) {
                arr2[0] = entry2.id[0];
                arr2[1] = entry2.id[1];
                peFileIndex = getLong(arr2) + pEIndex;
                ResourceDirectory.DirectoryEntry.DataEntry dataEntry2 = new ResourceDirectory.DirectoryEntry.DataEntry();
                loadBytes(dataEntry2.resourRVA);
                loadBytes(dataEntry2.resourSize);
                loadBytes(dataEntry2.resourTest);
                loadBytes(dataEntry2.resourWen);
                dataEntry2.fileStartIndex = getLong(dataEntry2.resourRVA) - resourSectRva + pEIndex;
                dataEntry2.fileEndIndex = dataEntry2.fileStartIndex + getLong(dataEntry2.resourSize);
                peFileIndex = savedIndex2;
                entry2.dataEntryList.add(dataEntry2);
            } else {
                arr2[0] = entry2.id[0];
                arr2[1] = entry2.id[1];
                ResourceDirectory childDir2 = new ResourceDirectory();
                entry2.nodeDirectoryList.add(childDir2);
                addResourceNode(childDir2, pEIndex, getLong(arr2), resourSectRva);
            }
            peFileIndex = savedIndex2;
            node.entryList.add(entry2);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void loadBytes(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = peFileByte[(int) peFileIndex];
            peFileIndex++;
        }
    }

    /**
     * Interprets up to 4 bytes in little-endian order as an unsigned long.
     */
    private long getLong(byte[] data) {
        if (data.length > 4) return 0L;
        long result = 0;
        for (int i = data.length - 1; i >= 0; i--) {
            result = (result << 8) | (data[i] & 0xFF);
        }
        return result;
    }
}
