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
        String resourceUrl = "http://116.62.135.75:2181/externalLinksController/chain/teacher%20(1).xlsx?ckey=Hb66pBL%2B5LJMrvwBEavaOOA47Kw2ZkhiV4Yitm%2Fu%2F9QiuIibud7Lie58FuC6BdT6";
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity entity = new HttpEntity(null, headers);
        ResponseEntity<String> result = restTemplate.exchange(docViewUrl + resourceUrl, HttpMethod.GET, entity, String.class);
        String viewUrl = JSON.parseObject(result.getBody()).getJSONObject("data").getString("viewUrl");
        String host = docViewUrl.substring(0,docViewUrl.lastIndexOf("/view"));
        System.out.println(host + viewUrl);
    }
}
