package com.pbdviewer.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class TreeNode {

    private NodeType nodeType;
    private boolean expanded;
    private boolean selected;
    protected String name;
    protected String text;
    private final ObservableList<TreeNode> children = FXCollections.observableArrayList();

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ObservableList<TreeNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
