package com.pbdviewer.utils.pbclass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PbObject {

    private boolean parsedInherit;

    private final PbEntry entry;
    private final PbProject project;
    private final int index;
    private final PbType type;
    private PbType inheritType;
    private PbObject inheritObject;
    private PbType parentType;
    private PbObject parentObject;
    private PbFunction[] functions;
    private PbVariable[] variables;
    private PbReferencedFunction[] referencedFunctions;
    private PbFunctionDefinition[] functionDefinitions;
    private PbVariable[] allVariables;
    private PbFunctionDefinition[] allFunctionDefinitions;
    private PbObject[] controls;

    public PbObject(PbEntry entry, int index, PbType type) {
        this.entry = entry;
        this.index = index;
        this.type = type;
        this.project = entry.getProject();
    }

    public void parseInherit() {
        if (parsedInherit) return;
        inheritObject = (inheritType != null) ? inheritType.getObject(entry) : null;
        parentObject = (parentType != null) ? parentType.getObject(entry) : null;

        if (inheritObject != null) {
            inheritObject.parseInherit();
            int num = Math.min(inheritObject.allVariables.length, allVariables.length);
            for (int i = 0; i < num; i++) allVariables[i] = inheritObject.allVariables[i];
            for (PbFunctionDefinition def : inheritObject.allFunctionDefinitions) {
                if (def != null) allFunctionDefinitions[def.getGlobalIndex()] = def;
            }
        }

        List<PbVariable> instanceVars = new ArrayList<>();
        for (PbVariable v : variables) {
            if (v.isInstance()) instanceVars.add(v);
        }
        Collections.reverse(instanceVars);
        for (int k = 0; k < instanceVars.size(); k++) {
            allVariables[allVariables.length - 1 - k] = instanceVars.get(k);
        }

        for (PbFunctionDefinition def : functionDefinitions) {
            allFunctionDefinitions[def.getGlobalIndex()] = def;
        }

        controls = entry.getObjects().values().stream()
                .filter(o -> o.getParentType() == type)
                .toArray(PbObject[]::new);

        for (int l = 0; l < allVariables.length; l++) {
            PbVariable variable = allVariables[l];
            if (variable != null) {
                PbObject matchingControl = null;
                for (PbObject ctrl : controls) {
                    if (ctrl.getType().getName().equals(variable.getName())) {
                        matchingControl = ctrl;
                        break;
                    }
                }
                if (matchingControl != null && matchingControl.getType() != variable.getType()) {
                    allVariables[l] = variable.inherit(matchingControl);
                }
            }
        }

        parsedInherit = true;
    }

    @Override
    public String toString() {
        return entry + "/" + type.getName();
    }

    public PbEntry getEntry() {
        return entry;
    }

    public PbProject getProject() {
        return project;
    }

    public int getIndex() {
        return index;
    }

    public PbType getType() {
        return type;
    }

    public PbType getInheritType() {
        return inheritType;
    }

    public void setInheritType(PbType inheritType) {
        this.inheritType = inheritType;
    }

    public PbObject getInheritObject() {
        return inheritObject;
    }

    public void setInheritObject(PbObject inheritObject) {
        this.inheritObject = inheritObject;
    }

    public PbType getParentType() {
        return parentType;
    }

    public void setParentType(PbType parentType) {
        this.parentType = parentType;
    }

    public PbObject getParentObject() {
        return parentObject;
    }

    public void setParentObject(PbObject parentObject) {
        this.parentObject = parentObject;
    }

    public PbFunction[] getFunctions() {
        return functions;
    }

    public void setFunctions(PbFunction[] functions) {
        this.functions = functions;
    }

    public PbVariable[] getVariables() {
        return variables;
    }

    public void setVariables(PbVariable[] variables) {
        this.variables = variables;
    }

    public PbReferencedFunction[] getReferencedFunctions() {
        return referencedFunctions;
    }

    public void setReferencedFunctions(PbReferencedFunction[] referencedFunctions) {
        this.referencedFunctions = referencedFunctions;
    }

    public PbFunctionDefinition[] getFunctionDefinitions() {
        return functionDefinitions;
    }

    public void setFunctionDefinitions(PbFunctionDefinition[] functionDefinitions) {
        this.functionDefinitions = functionDefinitions;
    }

    public PbVariable[] getAllVariables() {
        return allVariables;
    }

    public void setAllVariables(PbVariable[] allVariables) {
        this.allVariables = allVariables;
    }

    public PbFunctionDefinition[] getAllFunctionDefinitions() {
        return allFunctionDefinitions;
    }

    public void setAllFunctionDefinitions(PbFunctionDefinition[] allFunctionDefinitions) {
        this.allFunctionDefinitions = allFunctionDefinitions;
    }

    public PbObject[] getControls() {
        return controls;
    }

    public void setControls(PbObject[] controls) {
        this.controls = controls;
    }
}
