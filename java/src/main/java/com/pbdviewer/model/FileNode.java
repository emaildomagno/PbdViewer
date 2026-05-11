package com.pbdviewer.model;

import com.pbdviewer.utils.pbclass.PbEntry;
import com.pbdviewer.utils.pbclass.PbFile;

import java.util.Comparator;
import java.util.stream.Collectors;

public class FileNode extends TreeNode {

    public FileNode(PbFile pbFile) {
        setExpanded(true);
        this.name = pbFile.getFilePath() != null ? pbFile.getFilePath() : pbFile.getFileName();
        setNodeType(NodeType.File);

        // Group entries by suffix, create a DirectoryNode for each group
        pbFile.getEntries().stream()
                .collect(Collectors.groupingBy(PbEntry::getSuffix))
                .forEach((suffix, entries) -> {
                    DirectoryNode dirNode = new DirectoryNode(suffix);
                    dirNode.setExpanded(true);
                    entries.stream()
                            .sorted(Comparator.comparing(PbEntry::getName))
                            .forEach(entry -> dirNode.getChildren().add(new EntryNode(entry)));
                    getChildren().add(dirNode);
                });
    }
}
