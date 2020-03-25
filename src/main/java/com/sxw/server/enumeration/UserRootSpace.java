package com.sxw.server.enumeration;

public enum UserRootSpace {
    ROOT("root","用户空间根目录"),
    RECEIVE("receive","用户收到文件根目录"),
    RECYCLE("recycle","用户回收站根目录");

    private String value;
    private String description;

    private UserRootSpace(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getVaue() {
        return value;
    }
    public String getDescription() {
        return description;
    }


}
