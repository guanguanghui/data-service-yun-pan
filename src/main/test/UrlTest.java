import com.alibaba.fastjson.JSON;
import com.sxw.server.util.ConfigureReader;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UrlTest {
    public static void main(String[] args) {
        String docViewUrl = "http://docview.sxw.cn:8000/view/url.json?url=";
        String resourceUrl = "http://116.62.135.75:2181/externalLinksController/downloadFileByKey/teacher.xlsx?dkey=1101ec93-b34a-4186-b8de-0b12c40dce3a";
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity entity = new HttpEntity(null, headers);
        ResponseEntity<String> result = restTemplate.exchange(docViewUrl + resourceUrl, HttpMethod.GET, entity, String.class);
        String viewUrl = JSON.parseObject(result.getBody()).getJSONObject("data").getString("viewUrl");
        String host = docViewUrl.substring(0,docViewUrl.lastIndexOf("/view"));
        System.out.println(host + viewUrl);
    }
}
