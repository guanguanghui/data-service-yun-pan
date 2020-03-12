package com.sxw.server.pojo;

import com.alibaba.fastjson.JSON;

public class ResponseBodyDTO {
    private Integer code;

    private Object data;

    private String message;

    public ResponseBodyDTO(){}

    public ResponseBodyDTO(Integer code, Object body){
        this.code = code;
        this.data = body;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
