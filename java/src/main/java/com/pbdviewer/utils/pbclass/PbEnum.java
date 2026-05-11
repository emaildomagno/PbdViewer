package com.pbdviewer.utils.pbclass;

import java.util.HashMap;
import java.util.Map;

public class PbEnum {

    private int index;
    private String name;
    private final Map<Integer, String> items = new HashMap<>();

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Integer, String> getItems() {
        return items;
    }
}
