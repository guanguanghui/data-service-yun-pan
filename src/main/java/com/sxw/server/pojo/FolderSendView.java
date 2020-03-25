package com.sxw.server.pojo;

import com.sxw.server.model.Folder;

public class FolderSendView {
    private String id;
    private String pid;
    private String folderId;
    private String folderName;
    private String folderCreationDate;
    private String folderCreator;
    private String folderParent;
    private int folderConstraint;
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

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderCreationDate() {
        return folderCreationDate;
    }

    public void setFolderCreationDate(String folderCreationDate) {
        this.folderCreationDate = folderCreationDate;
    }

    public String getFolderCreator() {
        return folderCreator;
    }

    public void setFolderCreator(String folderCreator) {
        this.folderCreator = folderCreator;
    }

    public String getFolderParent() {
        return folderParent;
    }

    public void setFolderParent(String folderParent) {
        this.folderParent = folderParent;
    }

    public int getFolderConstraint() {
        return folderConstraint;
    }

    public void setFolderConstraint(int folderConstraint) {
        this.folderConstraint = folderConstraint;
    }

    public String getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
    }

    public FolderSendView(Folder folder){
        this.folderId = folder.getFolderId();
        this.folderName = folder.getFolderName();
        this.folderParent = folder.getFolderParent();
        this.folderCreationDate = folder.getFolderCreationDate();
        this.folderCreator = folder.getFolderCreator();
        this.folderConstraint = folder.getFolderConstraint();
        this.delFlag = folder.getDelFlag();
    }

    public FolderSendView(){

    }
}
