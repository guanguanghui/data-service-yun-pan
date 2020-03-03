package com.sxw.server.pojo;

import java.util.List;

public class FolderReceiveView {
    private FolderSendView folder;
    private List<FolderSendView> parentList;
    private List<FolderSendView> folderList;
    private List<FileSendView> fileList;
    private String account;
    private List<String> authList;
    private String publishTime;
    private String allowChangePassword;
    private String showFileChain;
    private String allowSignUp;
    private boolean enableDownloadZip;
    private boolean enableFFMPEG;

    private long foldersOffset;// 文件夹列表查询偏移量
    private long filesOffset;// 文件列表查询偏移量
    private int selectStep;// 查询步长

    public FolderSendView getFolder() {
        return this.folder;
    }

    public void setFolder(final FolderSendView folder) {
        this.folder = folder;
    }

    public List<FolderSendView> getParentList() {
        return this.parentList;
    }

    public void setParentList(final List<FolderSendView> parentList) {
        this.parentList = parentList;
    }

    public List<FolderSendView> getFolderList() {
        return this.folderList;
    }

    public void setFolderList(final List<FolderSendView> folderList) {
        this.folderList = folderList;
    }

    public List<FileSendView> getFileList() {
        return this.fileList;
    }

    public void setFileList(final List<FileSendView> fileList) {
        this.fileList = fileList;
    }

    public List<String> getAuthList() {
        return this.authList;
    }

    public void setAuthList(final List<String> authList) {
        this.authList = authList;
    }

    public String getAccount() {
        return this.account;
    }

    public void setAccount(final String account) {
        this.account = account;
    }

    public String getPublishTime() {
        return this.publishTime;
    }

    public void setPublishTime(final String publishTime) {
        this.publishTime = publishTime;
    }

    public String getAllowChangePassword() {
        return allowChangePassword;
    }

    public void setAllowChangePassword(String allowChangePassword) {
        this.allowChangePassword = allowChangePassword;
    }

    public String getShowFileChain() {
        return showFileChain;
    }

    public void setShowFileChain(String showFileChain) {
        this.showFileChain = showFileChain;
    }

    public String getAllowSignUp() {
        return allowSignUp;
    }

    public void setAllowSignUp(String allowSignUp) {
        this.allowSignUp = allowSignUp;
    }

    public long getFoldersOffset() {
        return foldersOffset;
    }

    public void setFoldersOffset(long foldersOffset) {
        this.foldersOffset = foldersOffset;
    }

    public long getFilesOffset() {
        return filesOffset;
    }

    public void setFilesOffset(long filesOffset) {
        this.filesOffset = filesOffset;
    }

    public int getSelectStep() {
        return selectStep;
    }

    public void setSelectStep(int selectStep) {
        this.selectStep = selectStep;
    }

    public boolean isEnableDownloadZip() {
        return enableDownloadZip;
    }

    public void setEnableDownloadZip(boolean enableDownloadZip) {
        this.enableDownloadZip = enableDownloadZip;
    }

    public boolean isEnableFFMPEG() {
        return enableFFMPEG;
    }

    public void setEnableFFMPEG(boolean enableFFMPEG) {
        this.enableFFMPEG = enableFFMPEG;
    }

}
