package com.sxw.server.util;

import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.mapper.FileSenderMapper;
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

    public boolean accessFolder(Folder f, String account){
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("fileId",f.getFolderId());
        keyMap.put("fileReceiver",account);
        // 可以在分享发送界面 访问文件
        FileSend fs = fsm.queryByFileIdAndReceiver(keyMap);
        return ConfigureReader.instance().accessFolder(f,account) || fs != null;
    }

    public boolean accessSendFile(Node f, String account){
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("fileId",f.getFileId());
        keyMap.put("fileReceiver",account);
        // 可以在分享发送界面 访问文件
        FileSend fs = fsm.queryByFileIdAndReceiver(keyMap);
        return fs != null;
    }

    public boolean authorized(final String account, final AccountAuth auth, List<String> folders){
        return ConfigureReader.instance().authorized(account,auth,folders);
    }
}
