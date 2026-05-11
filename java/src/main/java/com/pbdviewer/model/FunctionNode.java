package com.pbdviewer.model;

import com.pbdviewer.utils.BufferHelper;
import com.pbdviewer.utils.PCodeHelper;
import com.pbdviewer.utils.pbclass.PbFunction;
import com.pbdviewer.utils.pbclass.PbFunctionParam;
import com.pbdviewer.utils.pbclass.PbVariable;

public class FunctionNode extends TreeNode {

    public FunctionNode(PbFunction pbFunction) {
        setNodeType(NodeType.Function);
        this.name = pbFunction.getDefinition().getName();
        this.text = "";
        this.text += "//" + pbFunction.getDefinition() + "\r\n\r\n";

        Iterable<String> values = PCodeHelper.parsePCode(pbFunction, true);

        // Show variables
        if (pbFunction.getVariables() != null) {
            for (int i = 0; i < pbFunction.getVariables().length; i++) {
                PbVariable variable = pbFunction.getVariables()[i];
                if (pbFunction.getProject().isDebug) {
                    this.text += String.format("%04X:  %s\r\n", i, variable.toDisplayString(pbFunction.getBuffer(), true));
                } else {
                    // Skip if it's a param
                    boolean isParam = false;
                    if (pbFunction.getDefinition().getParams() != null) {
                        for (PbFunctionParam param : pbFunction.getDefinition().getParams()) {
                            if (param.getName().equals(variable.getName())) {
                                isParam = true;
                                break;
                            }
                        }
                    }
                    // Skip referenced globals and names starting with control char 
                    if (!isParam && !variable.isReferencedGlobal() &&
                            (variable.getName() == null || !variable.getName().startsWith(""))) {
                        this.text += String.format("%s\r\n", variable.toDisplayString(pbFunction.getBuffer(), false));
                    }
                }
            }
        }

        if (pbFunction.getProject().isDebug && pbFunction.getBuffer() != null) {
            this.text += "\r\n\r\n";
            int rows = (pbFunction.getBuffer().length + 15) / 16;
            for (int j = 0; j < rows; j++) {
                this.text += String.format("%04X:  %s\r\n", j,
                        BufferHelper.getHexString(pbFunction.getBuffer(), j * 16, 16));
            }
        }

        this.text += "\r\n\r\n";
        this.text += String.join("\r\n", values);
    }
}
