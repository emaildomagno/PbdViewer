package com.pbdviewer.model;

import com.pbdviewer.utils.pbclass.PbObject;
import com.pbdviewer.utils.pbclass.PbVariable;

import java.util.Arrays;
import java.util.stream.Collectors;

public class StructureNode extends TreeNode {

    public StructureNode(PbObject pbObject) {
        this.name = pbObject.getType().getName();
        setNodeType(NodeType.Structure);
        if (pbObject.getVariables() != null) {
            text = Arrays.stream(pbObject.getVariables())
                    .map(o -> o.toDisplayString(null, false))
                    .collect(Collectors.joining("\r\n"));
        }
    }
}
