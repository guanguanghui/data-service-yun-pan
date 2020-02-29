package com.sxw.server.pojo;

public class TokenInfo {
    private String accountId;
    private Integer accountType;
    private String userId;
    private String app;
    private String client;
    private String userType;
    private String platform;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Integer getAccountType() {
        return accountType;
    }

    public void setAccountType(Integer accountType) {
        this.accountType = accountType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    @Override
    public String toString() {
        return "TokenInfo{" +
                "accountId='" + accountId + '\'' +
                ", accountType=" + accountType +
                ", userId='" + userId + '\'' +
                ", app='" + app + '\'' +
                ", client='" + client + '\'' +
                ", userType='" + userType + '\'' +
                ", platform='" + platform + '\'' +
                '}';
    }
}
