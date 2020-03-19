package com.sxw.server.pojo;

import com.alibaba.fastjson.JSON;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

public class ParamSendFiles implements Serializable {
    @ApiModelProperty("文件列表")
    @NotNull
    private List<String> strIdList;
    @ApiModelProperty("文件夹列表")
    @NotNull
    private List<String> strFidList;
    @ApiModelProperty("接收者列表")
    @NotNull
    private List<String> fileReceivers;

    public List<String> getStrIdList() {
        return strIdList;
    }

    public void setStrIdList(List<String> strIdList) {
        this.strIdList = strIdList;
    }

    public List<String> getStrFidList() {
        return strFidList;
    }

    public void setStrFidList(List<String> strFidList) {
        this.strFidList = strFidList;
    }

    public List<String> getFileReceivers() {
        return fileReceivers;
    }

    public void setFileReceivers(List<String> fileReceivers) {
        this.fileReceivers = fileReceivers;
    }

    /**
     * 返回对象JSON字符串
     *
     * @return
     */
    public String toJSON() {
        return JSON.toJSONString(this);
    }

    /**
     * 返回对象格式化的JSON字符串
     *
     * @return
     */
    public String toJSONFormat() {
        return JSON.toJSONString(this, true);
    }
}
