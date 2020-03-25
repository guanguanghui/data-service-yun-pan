package com.sxw.server.enumeration;

public enum FolderConstraint {
    PUBLIC("公开的",0),
    GROUP("小组",1),
    PRIVATE("私有的",2);

    // 成员变量
    private String name;
    private int index;
    // 构造方法
    private FolderConstraint(String name, int index) {
        this.name = name;
        this.index = index;
    }
    // 普通方法
    public static String getName(int index) {
        for (FolderConstraint c : FolderConstraint.values()) {
            if (c.getIndex() == index) {
                return c.name;
            }
        }
        return null;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getIndex() {
        return index;
    }
    public void setIndex(int index) {
        this.index = index;
    }
    }
