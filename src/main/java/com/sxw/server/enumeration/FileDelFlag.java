package com.sxw.server.enumeration;

public enum FileDelFlag {
    FALSE("false", "文件非处于回收站状态"),
    TRUE("true", "文件处于回收站状态");

    private String name;
    private String description;

    private FileDelFlag(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
}
