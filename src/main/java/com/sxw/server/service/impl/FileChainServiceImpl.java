package com.sxw.server.service.impl;

import java.io.File;
import java.io.IOException;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.mapper.PropertiesMapper;
import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import com.sxw.server.model.Propertie;
import com.sxw.server.service.FileChainService;
import com.sxw.server.util.AESCipher;
import com.sxw.server.util.ConfigureReader;
import com.sxw.server.util.ContentTypeMap;
import com.sxw.server.util.FileBlockUtil;
import com.sxw.server.util.FolderUtil;
import com.sxw.server.util.LogUtil;
import com.sxw.server.util.RangeFileStreamWriter;

@Service
public class FileChainServiceImpl extends RangeFileStreamWriter implements FileChainService {

    @Resource
    private NodeMapper nm;
    @Resource
    private FolderMapper flm;
    @Resource
    private FileBlockUtil fbu;
    @Resource
    private ContentTypeMap ctm;
    @Resource
    private LogUtil lu;
    @Resource
    private AESCipher cipher;
    @Resource
    private PropertiesMapper pm;
    @Resource
    private FolderUtil fu;

    @Override
    public void getResourceByChainKey(HttpServletRequest request, HttpServletResponse response) {
        int statusCode = 403;
        final String ckey = request.getParameter("ckey");
        // 权限凭证有效性并确认其对应的资源
        if (ckey != null) {
            Propertie keyProp = pm.selectByKey("chain_aes_key");
            if (keyProp != null) {
                try {
                    String fid = cipher.decrypt(keyProp.getPropertieValue(), ckey);
                    Node f = this.nm.queryById(fid);
                    if (f != null) {
                        File target = this.fbu.getFileFromBlocks(f);
                        if (target != null && target.isFile()) {
                            String fileName = f.getFileName();
                            String suffix = "";
                            if (fileName.indexOf(".") >= 0) {
                                suffix = fileName.substring(fileName.indexOf("."));
                            }
                            writeRangeFileStream(request, response, target, f.getFileName(),
                                    ctm.getContentType(suffix),
                                    ConfigureReader.instance().getDownloadMaxRate(null));
                            if (request.getHeader("Range") == null) {
                                this.lu.writeChainEvent(request, f);
                            }
                            return;
                        }
                    }
                    statusCode = 404;
                } catch (Exception e) {
                    lu.writeException(e);
                    statusCode = 500;
                }
            } else {
                statusCode = 404;
            }
        }

        try {
            //  处理无法下载的资源
            response.sendError(statusCode);
        } catch (IOException e) {

        }
    }

    @Override
    public String getChainKeyByFid(HttpServletRequest request) {
        if (ConfigureReader.instance().isOpenFileChain()) {
            String fid = request.getParameter("fid");
            String account = (String) request.getSession().getAttribute("ACCOUNT");
            if (fid != null) {
                final Node f = this.nm.queryById(fid);
                if (f != null) {

                    Folder folder = flm.queryById(f.getFileParentFolder());

                    // 将指定的fid加密为ckey并返回。
                    try {
                        Propertie keyProp = pm.selectByKey("chain_aes_key");
                        if (keyProp == null) {// 如果没有生成过永久性AES密钥，则先生成再加密
                            String aesKey = cipher.generateRandomKey();
                            Propertie chainAESKey = new Propertie();
                            chainAESKey.setPropertieKey("chain_aes_key");
                            chainAESKey.setPropertieValue(aesKey);
                            if (pm.insert(chainAESKey) > 0) {
                                return cipher.encrypt(aesKey, fid);
                            }
                        } else {// 如果已经有了，则直接用其加密
                            return cipher.encrypt(keyProp.getPropertieValue(), fid);
                        }
                    } catch (Exception e) {
                        lu.writeException(e);
                    }


                }
            }
        }
        return "ERROR";
    }

}
