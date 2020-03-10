package com.sxw.server.util;

import com.alibaba.fastjson.JSON;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class SxwApiUtil {

    @Resource
    private RestTemplate restTemplate;

    public String getAcountBaseInfo(String url,String token){
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
        headers.add("TOKEN",token);
        HttpEntity<String> entity = new HttpEntity<String>(null, headers);
        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return result.getBody();
    }

    public String getAllDepartment(String url,String token){

        String accountInfo = getAcountBaseInfo(ConfigureReader.instance().getAcountBaseInfoUrl(),token);
        String schoolId = JSON.parseObject(accountInfo).getJSONObject("data").getJSONObject("schoolInfo").getString("schoolId");

        Map<String, Object> requestParam = new HashMap<>();
        requestParam.put("schoolId", schoolId);
        requestParam.put("type", 1);

        HttpHeaders headers = new HttpHeaders();
        headers.add("TOKEN",token);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
        HttpEntity entity = new HttpEntity(requestParam, headers);
        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return result.getBody();
    }
}
