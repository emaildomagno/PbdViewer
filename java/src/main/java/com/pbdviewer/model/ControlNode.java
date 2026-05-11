package com.pbdviewer.model;

import com.pbdviewer.utils.pbclass.*;

import java.util.Arrays;
import java.util.Comparator;

public class ControlNode extends TreeNode {

    public ControlNode(String name, PbObject object) {
        this.name = name.equals(object.getType().getName())
                ? name
                : String.format("%s(%s)", name, object.getType().getName());
        setNodeType(NodeType.Node);

        // Find child controls of this object
        PbObject[] controls = Arrays.stream(object.getEntry().getObjects().values().toArray(new PbObject[0]))
                .filter(o -> o.getParentObject() == object)
                .toArray(PbObject[]::new);

        // Properties: non-instance variables that aren't controls
        PbVariable[] properties = object.getVariables() != null
                ? Arrays.stream(object.getVariables())
                .filter(o -> !o.isInstance() && Arrays.stream(controls)
                        .noneMatch(b -> b.getType().getName().equals(o.getType().getName())))
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (properties.length > 0) {
            getChildren().add(new VariablesNode("properties", properties, object.getEntry().getVariableBuffer()));
        }

        PbVariable[] allVariables = object.getAllVariables();
        if (allVariables != null && allVariables.length > 0) {
            getChildren().add(new VariablesNode("all instance variables", allVariables, null, true));
        }

        PbFunctionDefinition[] allFunctionDefinitions = object.getAllFunctionDefinitions();
        if (allFunctionDefinitions != null && allFunctionDefinitions.length > 0) {
            getChildren().add(new ExternalFunctionsNode("all functions and events", allFunctionDefinitions, true));
        }

        // Child controls directory
        DirectoryNode controlsDir = new DirectoryNode("controls");
        for (PbObject ctrl : controls) {
            controlsDir.getChildren().add(new ControlNode(ctrl.getType().getName(), ctrl));
        }
        if (!controlsDir.getChildren().isEmpty()) {
            getChildren().add(controlsDir);
        }

        if (object.getFunctions() == null) {
            return;
        }

        // Set definitions on functions
        for (PbFunction pbFunction : object.getFunctions()) {
            if (object.getFunctionDefinitions() != null && pbFunction.getIndex() < object.getFunctionDefinitions().length) {
                pbFunction.setDefinition(object.getFunctionDefinitions()[pbFunction.getIndex()]);
            }
        }

        // Events directory
        PbFunction[] events = Arrays.stream(object.getFunctions())
                .filter(o -> o.getDefinition() != null && o.getDefinition().isEvent())
                .toArray(PbFunction[]::new);
        if (events.length > 0) {
            DirectoryNode eventsDir = new DirectoryNode("events");
            for (PbFunction pbFunction : events) {
                eventsDir.getChildren().add(new FunctionNode(pbFunction));
            }
            getChildren().add(eventsDir);
        }
    }
}
