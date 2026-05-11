package com.pbdviewer.model;

public class DirectoryNode extends TreeNode {

    public DirectoryNode(String name) {
        this.name = name;
        setNodeType(NodeType.Directory);
    }
}
