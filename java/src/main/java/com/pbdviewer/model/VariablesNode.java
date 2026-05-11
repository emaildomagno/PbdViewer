package com.pbdviewer.model;

import com.pbdviewer.utils.pbclass.PbVariable;

import java.util.Arrays;
import java.util.stream.Collectors;

public class VariablesNode extends TreeNode {

    public VariablesNode(String name, PbVariable[] variables, byte[] valueBuffer) {
        this(name, variables, valueBuffer, false);
    }

    public VariablesNode(String name, PbVariable[] variables, byte[] valueBuffer, boolean isAll) {
        this.name = name;
        setNodeType(NodeType.Variables);
        if (variables != null) {
            text = Arrays.stream(variables)
                    .map(o -> showVariable(o, valueBuffer, isAll))
                    .collect(Collectors.joining("\r\n"));
        }
    }

    private String showVariable(PbVariable variable, byte[] valueBuffer, boolean isAll) {
        if (variable == null) return null;
        String displayText = variable.toDisplayString(valueBuffer, false);
        if (isAll && variable.getObject() != null) {
            displayText = String.format("%-40s// %s -> %s -> %s",
                    displayText,
                    variable.getObject().getType().getName(),
                    variable.getObject().getEntry().getEntryName(),
                    variable.getObject().getEntry().getFile().getFileName());
        }
        return displayText;
    }
}
