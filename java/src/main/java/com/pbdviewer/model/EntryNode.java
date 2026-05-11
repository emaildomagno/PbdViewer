package com.pbdviewer.model;

import com.pbdviewer.utils.pbclass.*;

import java.util.Arrays;
import java.util.Comparator;

public class EntryNode extends TreeNode {

    private final PbEntry pbEntry;

    public EntryNode(PbEntry pbEntry) {
        this.pbEntry = pbEntry;
        setNodeType(NodeType.Node);
        this.name = pbEntry.getEntryName();
        this.text = pbEntry.getSource();

        switch (pbEntry.getSuffix()) {
            case "apl":
                setNodeType(NodeType.Application);
                parseApplication();
                break;
            case "men":
                setNodeType(NodeType.Menu);
                parseControl();
                break;
            case "win":
                setNodeType(NodeType.Window);
                parseControl();
                break;
            case "udo":
                setNodeType(NodeType.UserObject);
                parseControl();
                break;
            case "str":
                setNodeType(NodeType.Structure);
                parseStructure();
                break;
            case "fun":
                setNodeType(NodeType.Function);
                parseFunction();
                break;
            case "dwo":
                setNodeType(NodeType.DataWindow);
                break;
            default:
                break;
        }
    }

    private void parseStructure() {
        if (pbEntry.getEntryObject() != null && pbEntry.getEntryObject().getVariables() != null) {
            getChildren().add(new VariablesNode("properties", pbEntry.getEntryObject().getVariables(), null));
        }
    }

    private void parseFunction() {
        if (pbEntry.getEntryObject() == null) return;

        PbObject entryObj = pbEntry.getEntryObject();
        PbFunction[] functions = entryObj.getFunctions();

        if (functions != null && functions.length > 0) {
            // Set definitions
            for (PbFunction pbFunction : functions) {
                if (entryObj.getFunctionDefinitions() != null && pbFunction.getIndex() < entryObj.getFunctionDefinitions().length) {
                    pbFunction.setDefinition(entryObj.getFunctionDefinitions()[pbFunction.getIndex()]);
                }
            }

            DirectoryNode functionsDir = new DirectoryNode("functions");
            Arrays.stream(functions)
                    .filter(o -> o.getDefinition() != null)
                    .sorted(Comparator.comparing(o -> o.getDefinition().getName()))
                    .forEach(o -> functionsDir.getChildren().add(new FunctionNode(o)));
            getChildren().add(functionsDir);
        }

        // Structures
        PbObject[] structures = pbEntry.getObjects().values().stream()
                .filter(o -> o.getInheritType() != null && "structure".equals(o.getInheritType().getName()))
                .toArray(PbObject[]::new);

        if (structures.length > 0) {
            DirectoryNode structuresDir = new DirectoryNode("structures");
            Arrays.stream(structures)
                    .sorted(Comparator.comparing(o -> o.getType().getName()))
                    .forEach(o -> structuresDir.getChildren().add(new StructureNode(o)));
            getChildren().add(structuresDir);
        }
    }

    private void parseApplication() {
        String entryName = this.name;
        String baseName = entryName.contains(".") ? entryName.substring(0, entryName.indexOf('.')) : entryName;

        // Global variables (non-shared, type not matching entry name, with IsCustom flag)
        PbVariable[] globalVars = pbEntry.getVariables() != null
                ? Arrays.stream(pbEntry.getVariables())
                .filter(o -> !o.isShared() && !o.getType().getName().equals(baseName) &&
                        com.pbdviewer.utils.pbclass.PbVariableFlag.hasFlag(o.getFlagValue(), com.pbdviewer.utils.pbclass.PbVariableFlag.IsCustom))
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (globalVars.length > 0) {
            getChildren().add(new VariablesNode("global variables", globalVars, pbEntry.getVariableBuffer()));
        }

        PbObject entryObj = pbEntry.getEntryObject();
        if (entryObj == null) return;

        // Global external functions
        PbFunctionDefinition[] externalDefs = entryObj.getFunctionDefinitions() != null
                ? Arrays.stream(entryObj.getFunctionDefinitions())
                .filter(PbFunctionDefinition::isExternal)
                .toArray(PbFunctionDefinition[]::new)
                : new PbFunctionDefinition[0];

        if (externalDefs.length > 0) {
            getChildren().add(new ExternalFunctionsNode("global external functions", externalDefs));
        }

        // Shared variables
        PbVariable[] sharedVars = pbEntry.getVariables() != null
                ? Arrays.stream(pbEntry.getVariables())
                .filter(PbVariable::isShared)
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (sharedVars.length > 0) {
            getChildren().add(new VariablesNode("shared variables", sharedVars, pbEntry.getVariableBuffer()));
        }

        // Instance variables (shared+instance)
        PbVariable[] instanceVars = entryObj.getVariables() != null
                ? Arrays.stream(entryObj.getVariables())
                .filter(o -> o.isInstance() && o.isShared())
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (instanceVars.length > 0) {
            getChildren().add(new VariablesNode("instance variables", instanceVars, pbEntry.getVariableBuffer()));
        }

        // Properties (non-instance)
        PbVariable[] properties = entryObj.getVariables() != null
                ? Arrays.stream(entryObj.getVariables())
                .filter(o -> !o.isInstance())
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (properties.length > 0) {
            getChildren().add(new VariablesNode("properties", properties, pbEntry.getVariableBuffer()));
        }

        // All instance variables
        PbVariable[] allVariables = entryObj.getAllVariables();
        if (allVariables != null && allVariables.length > 0) {
            getChildren().add(new VariablesNode("all instance variables", allVariables, null, true));
        }

        // All functions and events
        PbFunctionDefinition[] allFunctionDefinitions = entryObj.getAllFunctionDefinitions();
        if (allFunctionDefinitions != null && allFunctionDefinitions.length > 0) {
            getChildren().add(new ExternalFunctionsNode("all functions and events", allFunctionDefinitions, true));
        }

        // Structures
        PbObject[] structures = pbEntry.getObjects().values().stream()
                .filter(o -> o.getInheritType() != null && "structure".equals(o.getInheritType().getName()))
                .toArray(PbObject[]::new);

        if (structures.length > 0) {
            DirectoryNode structuresDir = new DirectoryNode("structures");
            for (PbObject structObj : structures) {
                structuresDir.getChildren().add(new StructureNode(structObj));
            }
            getChildren().add(structuresDir);
        }

        // Set definitions on functions
        PbFunction[] functions = entryObj.getFunctions();
        if (functions != null) {
            for (PbFunction pbFunction : functions) {
                if (entryObj.getFunctionDefinitions() != null && pbFunction.getIndex() < entryObj.getFunctionDefinitions().length) {
                    pbFunction.setDefinition(entryObj.getFunctionDefinitions()[pbFunction.getIndex()]);
                }
            }

            // Events
            PbFunction[] events = Arrays.stream(functions)
                    .filter(o -> o.getDefinition() != null && o.getDefinition().isEvent())
                    .toArray(PbFunction[]::new);

            if (events.length > 0) {
                DirectoryNode eventsDir = new DirectoryNode("events");
                for (PbFunction pbFunction : events) {
                    eventsDir.getChildren().add(new FunctionNode(pbFunction));
                }
                getChildren().add(eventsDir);
            }

            // Non-event, non-external functions
            PbFunction[] nonEventFunctions = Arrays.stream(functions)
                    .filter(o -> o.getDefinition() != null && !o.getDefinition().isEvent() && !o.getDefinition().isExternal())
                    .toArray(PbFunction[]::new);

            if (nonEventFunctions.length > 0) {
                DirectoryNode functionsDir = new DirectoryNode("functions");
                Arrays.stream(nonEventFunctions)
                        .sorted(Comparator.comparing(o -> o.getDefinition().getName()))
                        .forEach(o -> functionsDir.getChildren().add(new FunctionNode(o)));
                getChildren().add(functionsDir);
            }
        }
    }

    private void parseControl() {
        // Structures
        PbObject[] structures = pbEntry.getObjects().values().stream()
                .filter(o -> o.getInheritType() != null && "structure".equals(o.getInheritType().getName()))
                .toArray(PbObject[]::new);

        PbObject entryObj = pbEntry.getEntryObject();
        if (entryObj == null) return;

        PbObject[] controls = pbEntry.getObjects().values().stream()
                .filter(o -> o.getParentObject() == entryObj)
                .toArray(PbObject[]::new);

        // Shared variables
        PbVariable[] sharedVars = pbEntry.getVariables() != null
                ? Arrays.stream(pbEntry.getVariables())
                .filter(PbVariable::isShared)
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (sharedVars.length > 0) {
            getChildren().add(new VariablesNode("shared variables", sharedVars, pbEntry.getVariableBuffer()));
        }

        // Instance variables (shared+instance)
        PbVariable[] instanceVars = entryObj.getVariables() != null
                ? Arrays.stream(entryObj.getVariables())
                .filter(o -> o.isInstance() && o.isShared())
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (instanceVars.length > 0) {
            getChildren().add(new VariablesNode("instance variables", instanceVars, pbEntry.getVariableBuffer()));
        }

        // Properties (non-instance, not a control type)
        PbObject[] finalControls = controls;
        PbVariable[] properties = entryObj.getVariables() != null
                ? Arrays.stream(entryObj.getVariables())
                .filter(o -> !o.isInstance() && Arrays.stream(finalControls)
                        .noneMatch(b -> b.getType().getName().equals(o.getType().getName())))
                .toArray(PbVariable[]::new)
                : new PbVariable[0];

        if (properties.length > 0) {
            getChildren().add(new VariablesNode("properties", properties, pbEntry.getVariableBuffer()));
        }

        // All instance variables
        PbVariable[] allVariables = entryObj.getAllVariables();
        if (allVariables != null && allVariables.length > 0) {
            getChildren().add(new VariablesNode("all instance variables", allVariables, null, true));
        }

        // All functions and events
        PbFunctionDefinition[] allFunctionDefinitions = entryObj.getAllFunctionDefinitions();
        if (allFunctionDefinitions != null && allFunctionDefinitions.length > 0) {
            getChildren().add(new ExternalFunctionsNode("all functions and events", allFunctionDefinitions, true));
        }

        // External function definitions
        PbFunctionDefinition[] externalDefs = entryObj.getFunctionDefinitions() != null
                ? Arrays.stream(entryObj.getFunctionDefinitions())
                .filter(PbFunctionDefinition::isExternal)
                .toArray(PbFunctionDefinition[]::new)
                : new PbFunctionDefinition[0];

        if (externalDefs.length > 0) {
            getChildren().add(new ExternalFunctionsNode("external functions", externalDefs));
        }

        // Structures directory
        if (structures.length > 0) {
            DirectoryNode structuresDir = new DirectoryNode("structures");
            Arrays.stream(structures)
                    .sorted(Comparator.comparing(o -> o.getType().getName()))
                    .forEach(o -> structuresDir.getChildren().add(new StructureNode(o)));
            getChildren().add(structuresDir);
        }

        // Controls directory
        DirectoryNode controlsDir = new DirectoryNode("controls");
        Arrays.stream(controls)
                .sorted(Comparator.comparing(o -> o.getType().getName()))
                .forEach(o -> controlsDir.getChildren().add(new ControlNode(o.getType().getName(), o)));
        if (!controlsDir.getChildren().isEmpty()) {
            getChildren().add(controlsDir);
        }

        // Set definitions on functions
        PbFunction[] functions = entryObj.getFunctions();
        if (functions != null) {
            for (PbFunction pbFunction : functions) {
                if (entryObj.getFunctionDefinitions() != null && pbFunction.getIndex() < entryObj.getFunctionDefinitions().length) {
                    pbFunction.setDefinition(entryObj.getFunctionDefinitions()[pbFunction.getIndex()]);
                }
            }

            // Events
            PbFunction[] events = Arrays.stream(functions)
                    .filter(o -> o.getDefinition() != null && o.getDefinition().isEvent())
                    .toArray(PbFunction[]::new);

            if (events.length > 0) {
                DirectoryNode eventsDir = new DirectoryNode("events");
                for (PbFunction pbFunction : events) {
                    eventsDir.getChildren().add(new FunctionNode(pbFunction));
                }
                getChildren().add(eventsDir);
            }

            // Non-event, non-external functions
            PbFunction[] nonEventFunctions = Arrays.stream(functions)
                    .filter(o -> o.getDefinition() != null && !o.getDefinition().isEvent() && !o.getDefinition().isExternal())
                    .toArray(PbFunction[]::new);

            if (nonEventFunctions.length > 0) {
                DirectoryNode functionsDir = new DirectoryNode("functions");
                Arrays.stream(nonEventFunctions)
                        .sorted(Comparator.comparing(o -> o.getDefinition().getName()))
                        .forEach(o -> functionsDir.getChildren().add(new FunctionNode(o)));
                getChildren().add(functionsDir);
            }
        }
    }
}
