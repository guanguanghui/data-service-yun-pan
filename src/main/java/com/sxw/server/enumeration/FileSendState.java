package com.sxw.server.enumeration;

public enum FileSendState {

    ON_SENDER_AND_RECEIVER("1", "文件发送信息在发送端和接收端都显示"),
    ON_SENDER("2", "文件发送信息只在发送端显示"),
    ON_RECEIVER("3", "文件发送信息只在接收端显示"),
    NO_SENDER_AND_RECEIVER("4", "文件发送信息在发送端和接收端都不显示");

    private String name;
    private String description;

    private FileSendState(String name, String description) {
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
