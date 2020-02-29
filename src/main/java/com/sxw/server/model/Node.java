package com.sxw.server.model;

public class Node
{
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

    public String getFileId() {
        return this.fileId;
    }
    
    public void setFileId(final String fileId) {
        this.fileId = fileId;
    }
    
    public String getFileName() {
        return this.fileName;
    }
    
    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public Long getFileLength() {
        return fileLength;
    }

    public void setFileLength(Long fileLength) {
        this.fileLength = fileLength;
    }
    
    public String getFileSize() {
        return this.fileSize;
    }
    
    public void setFileSize(final String fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getFileParentFolder() {
        return this.fileParentFolder;
    }
    
    public void setFileParentFolder(final String fileParentFolder) {
        this.fileParentFolder = fileParentFolder;
    }
    
    public String getFileCreationDate() {
        return this.fileCreationDate;
    }
    
    public void setFileCreationDate(final String fileCreationDate) {
        this.fileCreationDate = fileCreationDate;
    }
    
    public String getFileCreator() {
        return this.fileCreator;
    }
    
    public void setFileCreator(final String fileCreator) {
        this.fileCreator = fileCreator;
    }
    
    public String getFilePath() {
        return this.filePath;
    }
    
    public void setFilePath(final String filePath) {
        this.filePath = filePath;
    }
}
