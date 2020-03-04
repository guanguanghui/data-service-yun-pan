package com.sxw.server.service.impl;

import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.model.FileSend;
import com.sxw.server.pojo.*;
import com.sxw.server.service.FolderViewService;
import com.sxw.server.util.ConfigureReader;
import com.sxw.server.util.FolderUtil;
import com.sxw.server.util.SxwFFMPEGLocator;
import com.sxw.server.util.ServerTimeUtil;
import org.springframework.stereotype.*;
import javax.annotation.*;
import com.sxw.server.mapper.*;
import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import javax.servlet.http.*;
import java.util.*;
import java.util.stream.Collectors;
import com.sxw.server.enumeration.*;
import com.sxw.server.util.*;
import com.google.gson.*;

@Service
public class FolderViewServiceImpl implements FolderViewService {

    private static int SELECT_STEP = 256;// 每次查询的文件或文件夹的最大限额，即查询步进长度

    @Resource
    private FileBlockUtil fbu;
    @Resource
    private FolderUtil fu;
    @Resource
    private FolderMapper fm;
    @Resource
    private NodeMapper flm;
    @Resource
    private FileSenderMapper fsm;
    @Resource
    private Gson gson;
    @Resource
    private SxwFFMPEGLocator kfl;
    @Resource
    private AccessAuthUtil accessAuthUtil;

    @Override
    public String getFolderViewToJson(final String fid, final HttpSession session, final HttpServletRequest request) {
        final ConfigureReader cr = ConfigureReader.instance();
        if (fid == null || fid.length() == 0) {
            return "ERROR";
        }
        Folder vf = this.fm.queryById(fid);
        if (vf == null) {
            return "NOT_FOUND";// 如果用户请求一个不存在的文件夹，则返回“NOT_FOUND”，令页面回到ROOT视图
        }
        final String account = (String) session.getAttribute("ACCOUNT");
        // 检查访问文件夹视图请求是否合法
        if (!accessAuthUtil.accessFolder(vf, account)) {
            return "notAccess";// 如无访问权限则直接返回该字段，令页面回到ROOT视图。
        }
        final FolderView fv = new FolderView();
        fv.setSelectStep(SELECT_STEP);// 返回查询步长
        fv.setFolder(vf);
        fv.setParentList(this.fu.getParentList(fid));
        long foldersOffset = this.fm.countByParentId(fid);// 第一波文件夹数据按照最后的记录作为查询偏移量
        fv.setFoldersOffset(foldersOffset);
        Map<String, Object> keyMap1 = new HashMap<>();
        keyMap1.put("pid", fid);
        long fOffset = foldersOffset - SELECT_STEP;
        keyMap1.put("offset", fOffset > 0L ? fOffset : 0L);// 进行查询
        keyMap1.put("rows", SELECT_STEP);
        List<Folder> folders = this.fm.queryByParentIdSection(keyMap1);
        List<Folder> fs = folders.parallelStream().filter(f -> {
            return accessAuthUtil.accessFolder(f, account);
        }).collect(Collectors.toList());

        fv.setFolderList(fs);
        long filesOffset = this.flm.countByParentFolderId(fid);// 文件的查询逻辑与文件夹基本相同
        fv.setFilesOffset(filesOffset);
        Map<String, Object> keyMap2 = new HashMap<>();
        keyMap2.put("pfid", fid);
        long fiOffset = filesOffset - SELECT_STEP;
        keyMap2.put("offset", fiOffset > 0L ? fiOffset : 0L);
        keyMap2.put("rows", SELECT_STEP);
        List<Node> files = this.flm.queryByParentFolderIdSection(keyMap2).stream().filter(
                e -> e.getFileCreator().equals(account)
        ).collect(Collectors.toList());
        fv.setFileList(files);
        if (account != null) {
            fv.setAccount(account);
        }
        if (ConfigureReader.instance().isAllowChangePassword()) {
            fv.setAllowChangePassword("true");
        } else {
            fv.setAllowChangePassword("false");
        }
        if (ConfigureReader.instance().isAllowSignUp()) {
            fv.setAllowSignUp("true");
        } else {
            fv.setAllowSignUp("false");
        }
        final List<String> authList = new ArrayList<String>();
        if (cr.authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(fid))) {
            authList.add("U");
        }
        if (cr.authorized(account, AccountAuth.CREATE_NEW_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("C");
        }
        if (cr.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("D");
        }
        if (cr.authorized(account, AccountAuth.RENAME_FILE_OR_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("R");
        }
        if (cr.authorized(account, AccountAuth.DOWNLOAD_FILES, fu.getAllFoldersId(fid))) {
            authList.add("L");
            if (cr.isOpenFileChain()) {
                fv.setShowFileChain("true");// 显示永久资源链接
            } else {
                fv.setShowFileChain("false");
            }
        }
        if (cr.authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(fid))) {
            authList.add("M");
        }
        if (cr.authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(fid))) {
            authList.add("P");
        }
        if (cr.authorized(account, AccountAuth.SEND_FILES, fu.getAllFoldersId(fid))) {
            authList.add("S");
        }
        fv.setAuthList(authList);
        fv.setPublishTime(ServerTimeUtil.accurateToMinute());
        fv.setEnableFFMPEG(kfl.getFFMPEGExecutablePath() == null ? false : true);
        fv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());
        return gson.toJson(fv);
    }

    @Override
    public String getReceiveViewToJson(final String fid, final HttpSession session, final HttpServletRequest request) {
        final ConfigureReader cr = ConfigureReader.instance();
        if (fid == null || fid.length() == 0) {
            return "ERROR";
        }
        final String account = (String) session.getAttribute("ACCOUNT");
        // 初始化的收到文件根视图: receive
        FileSend fs = this.fsm.queryById(fid);
        Folder vf = this.fm.queryById(fs.getFileId());
        FolderSendView fsv = new FolderSendView(vf);
        fsv.setId(fs.getId());
        fsv.setPid(fs.getPid());

        if (vf == null) {
            return "NOT_FOUND";// 如果用户请求一个不存在的文件夹，则返回“NOT_FOUND”，令页面回到ROOT视图
        }

        final FolderReceiveView fv = new FolderReceiveView();
        fv.setSelectStep(SELECT_STEP);// 返回查询步长

        fv.setFolder(fsv);
        fv.setParentList(this.fu.getReceiveParentList(fid, account));

        long foldersOffset = this.fsm.countByPid(fid);// 第一波文件夹数据按照最后的记录作为查询偏移量
        fv.setFoldersOffset(foldersOffset);
        Map<String, Object> keyMap1 = new HashMap<>();
        keyMap1.put("pid", fid);
        long fOffset = foldersOffset - SELECT_STEP;
        keyMap1.put("offset", fOffset > 0L ? fOffset : 0L);// 进行查询
        keyMap1.put("rows", SELECT_STEP);
        List<FileSend> fsList = this.fsm.queryByPid(keyMap1).stream().filter(e -> {
            return e.getFileReceiver().equals(account);
        }).collect(Collectors.toList());
        List<FolderSendView> folders = fsList.parallelStream()
                .filter(e -> e.getFileType().equals(FileSendType.FOLDER.getName()))
                .map(e -> {
                    Folder folder = fm.queryById(e.getFileId());
                    // folder.setFolderParent(fid);
                    folder.setFolderName(e.getFileName());
                    folder.setFolderCreator(e.getFileSender());
                    folder.setFolderCreationDate(e.getFileSendDate());
                    FolderSendView fsv0 = new FolderSendView(folder);
                    fsv0.setId(e.getId());
                    fsv0.setPid(e.getPid());
                    return fsv0;
                }).collect(Collectors.toList());
        List<FileSendView> nodes = fsList.parallelStream()
                .filter(e -> e.getFileType().equals(FileSendType.FILE.getName()))
                .map(e -> {
                    Node node = flm.queryById(e.getFileId());
                    // node.setFileParentFolder(fid);
                    node.setFileName(e.getFileName());
                    node.setFileCreator(e.getFileSender());
                    node.setFileCreationDate(e.getFileSendDate());
                    FileSendView fsv1 = new FileSendView(node);
                    fsv1.setId(e.getId());
                    fsv1.setPid(e.getPid());
                    return fsv1;
                }).collect(Collectors.toList());

        fv.setFolderList(folders);
        fv.setFileList(nodes);
        if (account != null) {
            fv.setAccount(account);
        }
        if (ConfigureReader.instance().isAllowChangePassword()) {
            fv.setAllowChangePassword("true");
        } else {
            fv.setAllowChangePassword("false");
        }
        if (ConfigureReader.instance().isAllowSignUp()) {
            fv.setAllowSignUp("true");
        } else {
            fv.setAllowSignUp("false");
        }
        final List<String> authList = new ArrayList<String>();

        if (cr.authorized(account, AccountAuth.DOWNLOAD_FILES, fu.getAllFoldersId(fid))) {
            authList.add("L");
            if (cr.isOpenFileChain()) {
                fv.setShowFileChain("true");// 显示永久资源链接
            } else {
                fv.setShowFileChain("false");
            }
        }

        if (cr.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("D");
        }
        if (cr.authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(fid))) {
            authList.add("P");
        }
        if (cr.authorized(account, AccountAuth.SEND_FILES, fu.getAllFoldersId(fid))) {
            authList.add("S");
        }
        fv.setAuthList(authList);
        fv.setPublishTime(ServerTimeUtil.accurateToMinute());
        fv.setEnableFFMPEG(kfl.getFFMPEGExecutablePath() == null ? false : true);
        fv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());
        return gson.toJson(fv);
    }

    @Override
    public String getRecycleBinViewToJson(final String fid, final HttpSession session, final HttpServletRequest request) {
        final ConfigureReader cr = ConfigureReader.instance();
        if (fid == null || fid.length() == 0) {
            return "ERROR";
        }
        Folder vf = this.fm.queryById(fid);
        if (vf == null) {
            return "NOT_FOUND";// 如果用户请求一个不存在的文件夹，则返回“NOT_FOUND”，令页面回到ROOT视图
        }
        final String account = (String) session.getAttribute("ACCOUNT");
        // 检查访问文件夹视图请求是否合法
        if (!ConfigureReader.instance().accessFolder(vf, account)) {
            return "notAccess";// 如无访问权限则直接返回该字段，令页面回到ROOT视图。
        }
        // 刪除过期文件
        fbu.deleteExpiratedFiles(account);

        final FolderView fv = new FolderView();
        fv.setSelectStep(SELECT_STEP);// 返回查询步长
        fv.setFolder(vf);
        fv.setParentList(this.fu.getParentList(fid));
        long foldersOffset = this.fm.countByParentId(fid);// 第一波文件夹数据按照最后的记录作为查询偏移量
        fv.setFoldersOffset(foldersOffset);
        Map<String, Object> keyMap1 = new HashMap<>();
        keyMap1.put("pid", fid);
        long fOffset = foldersOffset - SELECT_STEP;
        keyMap1.put("offset", fOffset > 0L ? fOffset : 0L);// 进行查询
        keyMap1.put("rows", SELECT_STEP);
        List<Folder> folders = this.fm.queryByParentIdSection(keyMap1);

        // 设定过期日期
        long druation = ConfigureReader.instance().getExpirationDate(account);
        List<Folder> fs = new LinkedList<>();
        for (Folder f : folders) {
            if (ConfigureReader.instance().accessFolder(f, account)) {
                f.setFolderCreationDate(ExpirationDateUtil.getExpirationDate(f.getFolderCreationDate(), druation));
                fs.add(f);
            }
        }
        fv.setFolderList(fs);
        long filesOffset = this.flm.countByParentFolderId(fid);// 文件的查询逻辑与文件夹基本相同
        fv.setFilesOffset(filesOffset);
        Map<String, Object> keyMap2 = new HashMap<>();
        keyMap2.put("pfid", fid);
        long fiOffset = filesOffset - SELECT_STEP;
        keyMap2.put("offset", fiOffset > 0L ? fiOffset : 0L);
        keyMap2.put("rows", SELECT_STEP);
        fv.setFileList(this.flm.queryByParentFolderIdSection(keyMap2).stream().map(e -> {
            // 设定过期日期
            e.setFileCreationDate(ExpirationDateUtil.getExpirationDate(e.getFileCreationDate(), druation));
            return e;
        }).filter(e -> e.getFileCreator().equals(account)).collect(Collectors.toList()));
        if (account != null) {
            fv.setAccount(account);
        }
        if (ConfigureReader.instance().isAllowChangePassword()) {
            fv.setAllowChangePassword("true");
        } else {
            fv.setAllowChangePassword("false");
        }
        if (ConfigureReader.instance().isAllowSignUp()) {
            fv.setAllowSignUp("true");
        } else {
            fv.setAllowSignUp("false");
        }
        final List<String> authList = new ArrayList<String>();

        if (cr.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("D");
        }
        if (cr.authorized(account, AccountAuth.RENAME_FILE_OR_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("R");
        }
        if (cr.authorized(account, AccountAuth.DOWNLOAD_FILES, fu.getAllFoldersId(fid))) {
            authList.add("L");
            if (cr.isOpenFileChain()) {
                fv.setShowFileChain("true");// 显示永久资源链接
            } else {
                fv.setShowFileChain("false");
            }
        }
        if (cr.authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(fid))) {
            authList.add("M");
        }
        if (cr.authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(fid))) {
            authList.add("P");
        }
        fv.setAuthList(authList);
        fv.setPublishTime(ServerTimeUtil.accurateToMinute());
        fv.setEnableFFMPEG(kfl.getFFMPEGExecutablePath() == null ? false : true);
        fv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());
        return gson.toJson(fv);
    }

    @Override
    public String getSreachViewToJson(HttpServletRequest request) {
        final ConfigureReader cr = ConfigureReader.instance();
        String fid = request.getParameter("fid");
        String keyWorld = request.getParameter("keyworld");
        if (fid == null || fid.length() == 0 || keyWorld == null) {
            return "ERROR";
        }
        // 如果啥么也不查，那么直接返回指定文件夹标准视图
        if (keyWorld.length() == 0) {
            return getFolderViewToJson(fid, request.getSession(), request);
        }
        Folder vf = this.fm.queryById(fid);
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 检查访问文件夹视图请求是否合法
        if (!ConfigureReader.instance().accessFolder(vf, account)) {
            return "notAccess";// 如无访问权限则直接返回该字段，令页面回到ROOT视图。
        }
        final SreachView sv = new SreachView();
        // 先准备搜索视图的文件夹信息
        Folder sf = new Folder();
        sf.setFolderId(vf.getFolderId());// 搜索视图的主键设置与搜索路径一致
        sf.setFolderName("在“" + vf.getFolderName() + "”内搜索“" + keyWorld + "”的结果...");// 名称就是搜索的描述
        sf.setFolderParent(vf.getFolderId());// 搜索视图的父级也与搜索路径一致
        sf.setFolderCreator("--");// 搜索视图是虚拟的，没有这些
        sf.setFolderCreationDate("--");
        sf.setFolderConstraint(vf.getFolderConstraint());// 其访问等级也与搜索路径一致
        sv.setFolder(sf);// 搜索视图的文件夹信息已经准备就绪
        // 设置上级路径为搜索路径
        List<Folder> pl = this.fu.getParentList(fid);
        pl.add(vf);
        sv.setParentList(pl);
        // 设置所有搜索到的文件夹和文件，该方法迭查找：
        List<Node> ns = new LinkedList<>();
        List<Folder> fs = new LinkedList<>();
        sreachFilesAndFolders(fid, keyWorld, account, ns, fs);
        sv.setFileList(ns);
        sv.setFolderList(fs);
        // 搜索不支持分段加载，所以统计数据直接写入实际查询到的列表大小
        sv.setFoldersOffset(0L);
        sv.setFilesOffset(0L);
        sv.setSelectStep(SELECT_STEP);
        // 账户视图与文件夹相同
        if (account != null) {
            sv.setAccount(account);
        }
        if (ConfigureReader.instance().isAllowChangePassword()) {
            sv.setAllowChangePassword("true");
        } else {
            sv.setAllowChangePassword("false");
        }
        // 设置操作权限，对于搜索视图而言，只能进行下载操作（因为是虚拟的）
        final List<String> authList = new ArrayList<String>();
        // 搜索结果只接受“下载”操作
        if (cr.authorized(account, AccountAuth.DOWNLOAD_FILES, fu.getAllFoldersId(fid))) {
            authList.add("L");
            if (cr.isOpenFileChain()) {
                sv.setShowFileChain("true");// 显示永久资源链接
            } else {
                sv.setShowFileChain("false");
            }
        }
        if (cr.authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(fid))) {
            authList.add("M");
        }
        if (cr.authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(fid))) {
            authList.add("P");
        }
        if (cr.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER, fu.getAllFoldersId(fid))) {
            authList.add("D");
        }
        // 同时额外具备普通文件夹没有的“定位”功能。
        authList.add("O");
        sv.setAuthList(authList);
        // 写入实时系统时间
        sv.setPublishTime(ServerTimeUtil.accurateToMinute());
        // 设置查询字段
        sv.setKeyWorld(keyWorld);
        // 返回公告MD5
        sv.setEnableFFMPEG(kfl.getFFMPEGExecutablePath() == null ? false : true);
        sv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());
        return gson.toJson(sv);
    }

    // 迭代查找所有匹配项，参数分别是：从哪找、找啥、谁要找、添加的前缀是啥（便于分辨不同路径下的同名文件）、找到的文件放哪、找到的文件夹放哪
    private void sreachFilesAndFolders(String fid, String key, String account, List<Node> ns, List<Folder> fs) {
        long druation = ConfigureReader.instance().getExpirationDate(account);
        for (Folder f : this.fm.queryByParentId(fid)) {
            if (ConfigureReader.instance().accessFolder(f, account)) {
                if (f.getFolderName().indexOf(key) >= 0) {
                    f.setFolderName(f.getFolderName());
                    if (f.getDelFlag().equals(FileDelFlag.TRUE.getName())) {
                        f.setFolderCreationDate(ExpirationDateUtil.getExpirationDate(f.getFolderCreationDate(), druation));
                    }
                    fs.add(f);
                }
                sreachFilesAndFolders(f.getFolderId(), key, account, ns, fs);
            }
        }
        for (Node n : this.flm.queryByParentFolderId(fid)) {
            if (n.getFileName().indexOf(key) >= 0) {
                n.setFileName(n.getFileName());
                if (n.getDelFlag().equals(FileDelFlag.TRUE.getName())) {
                    n.setFileCreationDate(ExpirationDateUtil.getExpirationDate(n.getFileCreationDate(), druation));
                }
                ns.add(n);
            }
        }
    }

    @Override
    public String getRemainingFolderViewToJson(HttpServletRequest request) {
        final String fid = request.getParameter("fid");
        final String foldersOffset = request.getParameter("foldersOffset");
        final String filesOffset = request.getParameter("filesOffset");
        if (fid == null || fid.length() == 0) {
            return "ERROR";
        }
        Folder vf = this.fm.queryById(fid);
        if (vf == null) {
            return "NOT_FOUND";// 如果用户请求一个不存在的文件夹，则返回“NOT_FOUND”，令页面回到ROOT视图
        }
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 检查访问文件夹视图请求是否合法
        if (!ConfigureReader.instance().accessFolder(vf, account)) {
            return "notAccess";// 如无访问权限则直接返回该字段，令页面回到ROOT视图。
        }
        final RemainingFolderView fv = new RemainingFolderView();
        if (foldersOffset != null) {
            try {
                long newFoldersOffset = Long.parseLong(foldersOffset);
                if (newFoldersOffset > 0L) {
                    Map<String, Object> keyMap1 = new HashMap<>();
                    keyMap1.put("pid", fid);
                    long nfOffset = newFoldersOffset - SELECT_STEP;
                    keyMap1.put("offset", nfOffset > 0L ? nfOffset : 0L);
                    keyMap1.put("rows", nfOffset > 0L ? SELECT_STEP : newFoldersOffset);
                    List<Folder> folders = this.fm.queryByParentIdSection(keyMap1);
                    List<Folder> fs = new LinkedList<>();
                    for (Folder f : folders) {
                        if (ConfigureReader.instance().accessFolder(f, account)) {
                            fs.add(f);
                        }
                    }
                    fv.setFolderList(fs);
                }
            } catch (NumberFormatException e) {
                return "ERROR";
            }
        }
        if (filesOffset != null) {
            try {
                long newFilesOffset = Long.parseLong(filesOffset);
                if (newFilesOffset > 0L) {
                    Map<String, Object> keyMap2 = new HashMap<>();
                    keyMap2.put("pfid", fid);
                    long nfiOffset = newFilesOffset - SELECT_STEP;
                    keyMap2.put("offset", nfiOffset > 0L ? nfiOffset : 0L);
                    keyMap2.put("rows", nfiOffset > 0L ? SELECT_STEP : newFilesOffset);
                    fv.setFileList(this.flm.queryByParentFolderIdSection(keyMap2));
                }
            } catch (NumberFormatException e) {
                return "ERROR";
            }
        }
        return gson.toJson(fv);
    }
}
