package com.pbdviewer.model;

import com.pbdviewer.utils.pbclass.PbFunctionDefinition;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ExternalFunctionsNode extends TreeNode {

    public ExternalFunctionsNode(String name, PbFunctionDefinition[] defs) {
        this(name, defs, false);
    }

    public ExternalFunctionsNode(String name, PbFunctionDefinition[] defs, boolean isAll) {
        this.name = name;
        setNodeType(NodeType.ExternalFunctions);
        if (defs != null) {
            text = Arrays.stream(defs)
                    .map(o -> showFunction(o, isAll))
                    .collect(Collectors.joining("\r\n"));
        }
    }

    private String showFunction(PbFunctionDefinition def, boolean isAll) {
        if (def == null) return null;
        String displayText = def.toString();
        if (isAll && def.getObject() != null) {
            displayText = String.format("%s  // %s -> %s -> %s",
                    displayText,
                    def.getObject().getType().getName(),
                    def.getObject().getEntry().getEntryName(),
                    def.getObject().getEntry().getFile().getFileName());
        }
        return displayText;
    }
}
