package com.sxw.server.model;

public class FileSend
{
    private String id;
    private String pid;
    private String fileId;
    private String fileName;
    private String fileParent;
    private String fileSender;
    private String fileReceiver;
    private String fileSendDate;
    private String fileSendState;
    private String fileType;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileParent() {
        return fileParent;
    }

    public void setFileParent(String fileParent) {
        this.fileParent = fileParent;
    }

    public String getFileSender() {
        return fileSender;
    }

    public void setFileSender(String fileSender) {
        this.fileSender = fileSender;
    }

    public String getFileReceiver() {
        return fileReceiver;
    }

    public void setFileReceiver(String fileReceiver) {
        this.fileReceiver = fileReceiver;
    }

    public String getFileSendDate() {
        return fileSendDate;
    }

    public void setFileSendDate(String fileSendDate) {
        this.fileSendDate = fileSendDate;
    }

    public String getFileSendState() {
        return fileSendState;
    }

    public void setFileSendState(String fileSendState) {
        this.fileSendState = fileSendState;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
