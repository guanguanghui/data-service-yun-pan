package com.sxw.server.controller;

import com.sxw.server.pojo.ParamSendFiles;
import com.sxw.server.service.ExternalDownloadService;
import com.sxw.server.service.FileService;
import com.sxw.server.service.FolderViewService;
import com.sxw.server.service.ResourceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

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
@Api(value = "YunPanH5ViewApi", tags = "YunPanH5ViewApi", description = "智慧校园云盘H5", protocols = "application/json")
@Controller
@RequestMapping({ "/h5Controller" })
public class H5RestController {
    private static final String CHARSET_BY_AJAX = "application/json; charset=utf-8";

    @Resource
    private FolderViewService fvs;
    @Resource
    private FileService fis;
    @Resource
    private ResourceService rs;//文件预览链接的相关处理

    @ApiOperation(value = "查询文件夹空间视图#管光辉/20200312#", notes = "查询文件夹空间视图", nickname = "YunPanH5ViewApi-getFolderView")
    @RequestMapping(value = { "/getFolderView" }, produces = { CHARSET_BY_AJAX }, method = RequestMethod.GET)
    @ResponseBody
    public String getFolderView(final String fid, final HttpSession session, final HttpServletRequest request) {
        return fvs.getH5FolderViewToJson(fid, session, request);
    }
    @ApiOperation(value = "查询收到文件夹空间视图#管光辉/20200312#", notes = "查询收到文件夹空间视图", nickname = "YunPanH5ViewApi-getReceiveBinView")
    @RequestMapping(value = { "/getReceiveBinView" }, produces = { CHARSET_BY_AJAX }, method = RequestMethod.GET)
    @ResponseBody
    public String getReceiveBinView(final String fid, final HttpSession session, final HttpServletRequest request) {
        return fvs.getH5ReceiveViewToJson(fid, session, request);
    }

    @ApiOperation(value = "发送文件文件夹#管光辉/20200312#", notes = "发送文件文件夹", nickname = "YunPanH5ViewApi-sendCheckedFiles")
    @RequestMapping(value = { "/sendFiles" }, produces = { CHARSET_BY_AJAX }, method = RequestMethod.POST)
    @ResponseBody
    public String sendCheckedFiles(@RequestBody ParamSendFiles paramSendFiles,final HttpServletRequest request) {
        return fis.doH5SendFiles(paramSendFiles,request);
    }

    @ApiOperation(value = "文件预览#管光辉/20200312#", notes = "文件预览", nickname = "YunPanH5ViewApi-getPreViewFileUrl")
    @RequestMapping(value = { "/getPreViewFileUrl" }, produces = { CHARSET_BY_AJAX }, method = RequestMethod.GET)
    @ResponseBody
    public String getPreViewFileUrl(@RequestParam(value = "fileId") String fileId, final HttpServletRequest request) {
        return rs.getH5FilePreViewUrl(fileId,request);
    }

}
