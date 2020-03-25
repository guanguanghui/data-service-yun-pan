package com.sxw.server.filter;

import javax.annotation.Resource;
import javax.servlet.annotation.*;
import javax.servlet.*;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.sxw.printer.Printer;
import com.sxw.server.enumeration.FolderConstraint;
import com.sxw.server.enumeration.UserRootSpace;
import com.sxw.server.exception.BusinessException;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.model.Folder;
import com.sxw.server.pojo.TokenInfo;
import com.sxw.server.util.*;

import javax.servlet.http.*;

import org.springframework.core.annotation.Order;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

@WebFilter
@Order(2)
public class MastLoginFilter implements Filter {

    private static final String defaultPassword = "admin";

    @Resource
    private LogUtil lu;
    @Resource
    private SxwApiUtil sau;
    @Resource
    private FolderUtil fu;

    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final ConfigureReader cr = ConfigureReader.instance();
        final boolean s = cr.mustLogin();
        final HttpServletRequest hsq = (HttpServletRequest) request;
        final HttpServletResponse hsr = (HttpServletResponse) response;
        final String url = hsq.getServletPath();
        final HttpSession session = hsq.getSession();

        if (url.startsWith("/externalLinksController/") || url.startsWith("//externalLinksController/")
                || url.startsWith("/homeController/getNewVerCode.do")
                || url.startsWith("//homeController/getNewVerCode.do")) {
            chain.doFilter(request, response);// 对于外部链接控制器和验证码的请求直接放行。
            return;
        }
        // 如果是无需登录的请求，那么直接放行（如果访问者已经登录，那么会被后面的过滤器重定向至主页，此处无需处理）
        switch (url) {
            case "/prv/login.html":
            case "//prv/login.html":
            case "/homeController/askForAllowSignUpOrNot.ajax":
            case "//homeController/askForAllowSignUpOrNot.ajax":
            case "/prv/signup.html":
            case "//prv/signup.html":
            case "//swagger-ui.html":
            case "/swagger-ui.html":
            case "/getFolderView":
            case "/getReceiveBinView":
                chain.doFilter(request, response);
                return;
            case "//":
            case "/":
                // 智慧校园跳转网盘的入口对接
                try {
                    final String token = hsq.getParameter((String) hsq.getParameterMap().keySet().toArray()[0]);
                    TokenInfo ti = TokenResolver.readTokenInfo(token);
                    String account = ti.getUserId();
                    String password = defaultPassword;

                    String acountBaseInfo = sau.getAcountBaseInfo(ConfigureReader.instance().getAcountBaseInfoUrl(), token);
                    String userName = JSON.parseObject(acountBaseInfo).getJSONObject("data").getJSONObject("userDto").getString("userName");

                    // 新账户和密码的合法性检查
                    CharsetEncoder ios8859_1Encoder = Charset.forName("ISO-8859-1").newEncoder();
                    if (account != null && account.length() >= 3 && ios8859_1Encoder.canEncode(account)) {
                        if (account.indexOf("=") < 0 && account.indexOf(":") < 0) {
                            if (!ConfigureReader.instance().foundAccount(account)) {
                                if (ConfigureReader.instance().createNewAccount(account, password)) {
                                    // 初始化用户空间
                                    fu.initUserFolder(UserRootSpace.ROOT.getVaue(),account);
                                    fu.initUserFolder(UserRootSpace.RECEIVE.getVaue(),account);
                                    fu.initUserFolder(UserRootSpace.RECYCLE.getVaue(),account);
                                    lu.writeSignUpEvent(hsq, account, password);
                                }
                            }
                            session.setAttribute("ACCOUNT", (Object) ti.getUserId());
                            session.setAttribute("ACCOUNTNAME", userName);
                            session.setAttribute("TOKEN", token);
                            hsr.sendRedirect("/home.html");
                        }
                    }else {
                        hsr.sendRedirect(ConfigureReader.instance().getLoginUrl());
                    }
                } catch (BusinessException e) {
                    // 认证异常，返回登陆入口
                    hsr.sendRedirect(ConfigureReader.instance().getLoginUrl());
                } catch (Exception e) {
                    // 其他异常，也返回登陆入口
                    Printer.instance.print(e.getMessage());
                    hsr.sendRedirect(ConfigureReader.instance().getLoginUrl());
                }
                return;
            default:
                break;
        }
        if (s) {
            if (url.endsWith(".html") || url.endsWith(".do")) {
                if (session.getAttribute("ACCOUNT") != null) {
                    final String account = (String) session.getAttribute("ACCOUNT");
                    if (cr.foundAccount(account)) {
                        chain.doFilter(request, response);
                    } else {
                        hsr.sendRedirect(ConfigureReader.instance().getLoginUrl());
                    }
                } else {
                    hsr.sendRedirect(ConfigureReader.instance().getLoginUrl());
                }
            } else if (url.endsWith(".ajax")) {
                if (url.equals("/homeController/doLogin.ajax") || url.equals("/homeController/getPublicKey.ajax")
                        || url.equals("/homeController/doSigUp.ajax")) {
                    chain.doFilter(request, response);
                } else if (session.getAttribute("ACCOUNT") != null) {
                    final String account = (String) session.getAttribute("ACCOUNT");
                    if (cr.foundAccount(account)) {
                        chain.doFilter(request, response);
                    } else {
                        hsr.setCharacterEncoding("UTF-8");
                        final PrintWriter pw = hsr.getWriter();
                        pw.print("mustLogin");
                        pw.flush();
                    }
                } else {
                    hsr.setCharacterEncoding("UTF-8");
                    final PrintWriter pw2 = hsr.getWriter();
                    pw2.print("mustLogin");
                    pw2.flush();
                }
            } else {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public void destroy() {
    }
}
