package com.sxw.server.pojo;

import com.sxw.server.model.Node;

public class FileSendView {
    private String id;
    private String pid;
    private String fileId;
    private String fileName;
    private Long fileLength;
    private String fileSize;
    private String fileParentFolder;
    private String fileCreationDate;
    private String fileCreator;
    private String filePath;
    private String fileMd5;
    private String delFlag;

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

    public Long getFileLength() {
        return fileLength;
    }

    public void setFileLength(Long fileLength) {
        this.fileLength = fileLength;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileParentFolder() {
        return fileParentFolder;
    }

    public void setFileParentFolder(String fileParentFolder) {
        this.fileParentFolder = fileParentFolder;
    }

    public String getFileCreationDate() {
        return fileCreationDate;
    }

    public void setFileCreationDate(String fileCreationDate) {
        this.fileCreationDate = fileCreationDate;
    }

    public String getFileCreator() {
        return fileCreator;
    }

    public void setFileCreator(String fileCreator) {
        this.fileCreator = fileCreator;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public String getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
    }

    public FileSendView(Node node) {
        this.fileId = node.getFileId();
        this.fileName = node.getFileName();
        this.fileParentFolder = node.getFileParentFolder();
        this.fileCreationDate = node.getFileCreationDate();
        this.fileCreator = node.getFileCreator();
        this.fileLength = node.getFileLength();
        this.fileSize = node.getFileSize();
        this.fileMd5 = node.getFileMd5();
        this.filePath = node.getFilePath();
        this.delFlag = node.getDelFlag();
    }
}
