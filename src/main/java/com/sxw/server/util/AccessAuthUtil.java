package com.sxw.server.util;

import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.mapper.FileSenderMapper;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.model.FileSend;
import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AccessAuthUtil {
    @Resource
    private FileSenderMapper fsm;
    @Resource
    private FolderMapper fm;

    public boolean accessFolder(Folder f, String account){
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("fileId",f.getFolderId());
        keyMap.put("fileReceiver",account);
        // 可以在分享发送界面 访问文件
        FileSend fs = fsm.queryByFileIdAndReceiver(keyMap);
        return ConfigureReader.instance().accessFolder(f,account) || fs != null;
    }

    // 查看文件权限判断
    public boolean accessViewFolder(Folder f, String account){
        return ConfigureReader.instance().accessFolder(f,account);
    }

    public boolean accessViewFile(Node f, String account){
        // 验证自己空间和公共空间文件的可访问性
        Folder parentFolder = fm.queryById(f.getFileParentFolder());
        if(parentFolder.getFolderId().equals("root")){
            return f.getFileCreator().equals(account);
        }
        return true;
    }

    public boolean accessSendFile(Node f, String account){
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("fileId",f.getFileId());
        keyMap.put("fileReceiver",account);
        // 可以在分享发送界面 访问文件
        FileSend fs = fsm.queryByFileIdAndReceiver(keyMap);
        return fs != null || accessViewFile(f, account);
    }

    public boolean authorized(final String account, final AccountAuth auth, List<String> folders){
        return ConfigureReader.instance().authorized(account,auth,folders);
    }
}
