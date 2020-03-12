package com.sxw.server.controller;

import com.sxw.server.service.AccountService;
import com.sxw.server.service.FolderViewService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 *
 * <h2>主控制器</h2>
 * <p>
 * 该控制器用于负责处理H5頁面的所有请求，具体过程请见各个方法注释。
 * </p>
 *
 * @author ggh@sxw.cn
 * @version 1.0
 */
@Controller
@RequestMapping({ "/h5Controller" })
public class H5RestController {
    private static final String CHARSET_BY_AJAX = "text/html; charset=utf-8";

    @Resource
    private FolderViewService fvs;

    @RequestMapping(value = { "/getFolderView.ajax" }, produces = { CHARSET_BY_AJAX })
    @ResponseBody
    public String getFolderView(final String fid, final HttpSession session, final HttpServletRequest request) {
        return fvs.getH5FolderViewToJson(fid, session, request);
    }

    @RequestMapping(value = { "/getRemainingFolderView.ajax" }, produces = { CHARSET_BY_AJAX })
    @ResponseBody
    public String getRemainingFolderView(final HttpServletRequest request) {
        return fvs.getRemainingFolderViewToJson(request);
    }

    @RequestMapping(value = { "/getReceiveBinView.ajax" }, produces = { CHARSET_BY_AJAX })
    @ResponseBody
    public String getReceiveBinView(final String fid, final HttpSession session, final HttpServletRequest request) {
        return fvs.getH5ReceiveViewToJson(fid, session, request);
    }


}
