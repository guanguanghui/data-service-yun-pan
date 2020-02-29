package com.sxw.server.enumeration;

public enum FileSendType {
    FILE("file", "文件"),
    FOLDER("folder", "文件夹");

    private String name;
    private String description;

    private FileSendType(String name, String description) {
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
