package com.pbdviewer.utils.pbclass;

public class PbFunctionParam {

    private boolean isReadOnly;
    private boolean isReference;
    private PbType type;
    private String name;
    private String arrayString;

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }

    public boolean isReference() {
        return isReference;
    }

    public void setReference(boolean reference) {
        isReference = reference;
    }

    public PbType getType() {
        return type;
    }

    public void setType(PbType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArrayString() {
        return arrayString;
    }

    public void setArrayString(String arrayString) {
        this.arrayString = arrayString;
    }

    @Override
    public String toString() {
        String text = "";
        if (isReference) text += "ref ";
        if (isReadOnly) text += "readonly ";
        return text + String.format("%s %s%s", type.getName(), name, arrayString);
    }
}
