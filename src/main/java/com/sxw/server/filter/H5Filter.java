package com.sxw.server.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sxw.server.enumeration.UserRootSpace;
import com.sxw.server.exception.BusinessException;
import com.sxw.server.pojo.ResponseBodyDTO;
import com.sxw.server.pojo.TokenInfo;
import com.sxw.server.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter({ "/h5Controller/*" })
@Order(5)
public class H5Filter implements Filter {

    private static final String TOKEN_HEADER_NAME = "token";

    private static final String defaultPassword = "admin";

    @Resource
    private SxwApiUtil sau;
    @Resource
    private LogUtil lu;
    @Resource
    private FolderUtil fu;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        final HttpSession session = request.getSession();
        if(session.getAttribute("ACCOUNT") != null){
            filterChain.doFilter(request, response);
            return;
        }
        String tokenString = request.getHeader(TOKEN_HEADER_NAME);
        // 结果返回对象
        ResponseBodyDTO responseBodyDTO = new ResponseBodyDTO();
        try{

            TokenInfo ti = TokenResolver.readTokenInfo(tokenString);

            // 校验token的合法性
            String checkedResult = sau.checkToken(tokenString);
            JSONObject checkedObject = JSON.parseObject(checkedResult);
            Integer checkCode = checkedObject.getInteger("code");
            if(checkCode != HttpStatus.OK.value()){
                responseBodyDTO.setCode(checkCode);
                responseBodyDTO.setMessage(checkedObject.getString("message"));
                responseBodyDTO.setData(checkedObject.getString("data"));
                response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
                response.getWriter().write(JSON.toJSONString(responseBodyDTO));
                return;
            }

            String account = ti.getUserId();
            String password = defaultPassword;

            String acountBaseInfo = sau.getAcountBaseInfo(ConfigureReader.instance().getAcountBaseInfoUrl(), tokenString);
            String userName = JSON.parseObject(acountBaseInfo).getJSONObject("data").getJSONObject("userDto").getString("userName");
            if (!ConfigureReader.instance().foundAccount(account)) {
                if (ConfigureReader.instance().createNewAccount(account, password)) {
                    // 初始化用户空间
                    fu.initUserFolder(UserRootSpace.ROOT.getVaue(),account);
                    fu.initUserFileSend(UserRootSpace.RECEIVE.getVaue(),account);
                    fu.initUserFolder(UserRootSpace.RECYCLE.getVaue(),account);
                    lu.writeSignUpEvent(request, account, password);
                }
            }
            session.setAttribute("ACCOUNT", (Object) ti.getUserId());
            session.setAttribute("ACCOUNTNAME", userName);
            session.setAttribute("TOKEN", tokenString);
            filterChain.doFilter(request, response);
        }catch (Exception e) {
            responseBodyDTO.setMessage(e.getMessage());
            responseBodyDTO.setData("");
            if(e instanceof BusinessException){
                BusinessException be = (BusinessException)e;
                responseBodyDTO.setCode(be.getCode());
            }else {
                responseBodyDTO.setCode(HttpStatus.BAD_REQUEST.value());
            }
            response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getWriter().write(JSON.toJSONString(responseBodyDTO));
        }
    }

    @Override
    public void destroy() {

    }
}
