package com.sxw.server.service.impl;

import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.exception.FoldersTotalOutOfLimitException;
import com.sxw.server.listener.ServerInitListener;
import com.sxw.server.mapper.FileSenderMapper;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.model.FileSend;
import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import com.sxw.server.service.FileService;
import com.sxw.server.util.*;
import javafx.util.Pair;
import org.apache.commons.codec.digest.DigestUtils;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.stereotype.*;
import javax.annotation.*;
import com.sxw.server.enumeration.*;
import com.sxw.server.pojo.CheckImportFolderRespons;
import com.sxw.server.pojo.CheckUploadFilesRespons;
import org.springframework.web.multipart.*;
import javax.servlet.http.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

/**
 * <h2>文件服务功能实现类</h2>
 * <p>
 * 该类负责对文件相关的服务进行实现操作，例如下载和上传等，各方法功能详见接口定义。
 * </p>
 *
 * @author ggh@sxw.cn
 * @version 1.0
 * @see FileService
 */
@Service
public class FileServiceImpl extends RangeFileStreamWriter implements FileService {
    private static final String FOLDERS_TOTAL_OUT_OF_LIMIT = "foldersTotalOutOfLimit";// 文件夹数量超限标识
    private static final String FILES_TOTAL_OUT_OF_LIMIT = "filesTotalOutOfLimit";// 文件数量超限标识
    private static final String ERROR_PARAMETER = "errorParameter";// 参数错误标识
    private static final String NO_AUTHORIZED = "noAuthorized";// 权限错误标识
    private static final String UPLOADSUCCESS = "uploadsuccess";// 上传成功标识
    private static final String UPLOADERROR = "uploaderror";// 上传失败标识

    @Resource
    private NodeMapper fm;
    @Resource
    private FolderMapper flm;
    @Resource
    private FileSenderMapper fsm;
    @Resource
    private LogUtil lu;
    @Resource
    private Gson gson;
    @Resource
    private FileBlockUtil fbu;
    @Resource
    private FolderUtil fu;
    @Resource
    private AccessAuthUtil accessAuthUtil;

    private static final String CONTENT_TYPE = "application/octet-stream";

    // 检查上传文件列表的实现（上传文件的前置操作）
    public String checkUploadFile(final HttpServletRequest request, final HttpServletResponse response) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        final String folderId = request.getParameter("folderId");
        final String nameList = request.getParameter("namelist");
        final String maxUploadFileSize = request.getParameter("maxSize");
        final String maxUploadFileIndex = request.getParameter("maxFileIndex");
        // 目标文件夹合法性检查
        if (folderId == null || folderId.length() == 0) {
            return ERROR_PARAMETER;
        }
        // 获取上传目标文件夹，如果没有直接返回错误
        Folder folder = flm.queryById(folderId);
        if (folder == null) {
            return ERROR_PARAMETER;
        }
        // 权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            return NO_AUTHORIZED;
        }
        // 获得上传文件名列表
        final List<String> namelistObj = gson.fromJson(nameList, new TypeToken<List<String>>() {
        }.getType());
        // 准备一个检查结果对象
        CheckUploadFilesRespons cufr = new CheckUploadFilesRespons();
        // 开始文件上传体积限制检查
        try {
            // 获取最大文件体积（以Byte为单位）
            long mufs = Long.parseLong(maxUploadFileSize);
            // 获取最大文件的名称
            String mfname = namelistObj.get(Integer.parseInt(maxUploadFileIndex));
            long pMaxUploadSize = ConfigureReader.instance().getUploadFileSize(account);
            if (pMaxUploadSize >= 0) {
                if (mufs > pMaxUploadSize) {
                    cufr.setCheckResult("fileTooLarge");
                    cufr.setMaxUploadFileSize(FormatFileSizeUtil.formatSize(pMaxUploadSize));
                    cufr.setOverSizeFile(mfname);
                    return gson.toJson(cufr);
                }
            }
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
        // 开始文件命名冲突检查
        final List<String> pereFileNameList = new ArrayList<>();
        // 查找目标目录下是否存在与待上传文件同名的文件（或文件夹），如果有，记录在上方的列表中
        for (final String fileName : namelistObj) {
            if (folderId == null || folderId.length() <= 0 || fileName == null || fileName.length() <= 0) {
                return ERROR_PARAMETER;
            }
            final List<Node> files = this.fm.queryByParentFolderId(folderId);
            if (files.stream().parallel()
                    .filter(e -> e.getFileCreator().equals(account))
                    .anyMatch((n) -> n.getFileName()
                            .equals(new String(fileName.getBytes(Charset.forName("UTF-8")), Charset.forName("UTF-8"))))) {
                pereFileNameList.add(fileName);
            }
        }
        // 判断如果上传了这一批文件的话，会不会引起文件数量超限
        long estimatedTotal = fm.countByParentFolderId(folderId) - pereFileNameList.size() + namelistObj.size();
        if (estimatedTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimatedTotal < 0) {
            return "filesTotalOutOfLimit";
        }
        // 如果存在同名文件，则写入同名文件的列表；否则，直接允许上传
        if (pereFileNameList.size() > 0) {
            cufr.setCheckResult("hasExistsNames");
            cufr.setPereFileNameList(pereFileNameList);
        } else {
            cufr.setCheckResult("permitUpload");
            cufr.setPereFileNameList(new ArrayList<String>());
        }
        return gson.toJson(cufr);// 以JSON格式写回该结果
    }

    // 伪装上传操作，接收文件并存入文件节点
    public String pretendUploadFile(final HttpServletRequest request, final HttpServletResponse response) {
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        final String folderId = request.getParameter("folderId");
        final String originalFileName = request.getParameter("originalFileName");
        final String fileMd5 = request.getParameter("fileMd5");
        final Long fileSize = Long.valueOf(request.getParameter("fileSize"));
        String fileName = originalFileName;
        final String repeType = request.getParameter("repeType");
        // 再次检查上传文件名与目标目录ID
        if (folderId == null || folderId.length() <= 0 || originalFileName == null || originalFileName.length() <= 0) {
            return UPLOADERROR;
        }
        Folder folder = flm.queryById(folderId);
        if (folder == null) {
            return UPLOADERROR;
        }
        // 检查上传权限
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            return UPLOADERROR;
        }
        // 检查上传文件体积是否超限
        long mufs = ConfigureReader.instance().getUploadFileSize(account);
        if (mufs >= 0 && fileSize > mufs) {
            return UPLOADERROR;
        }
        // 检查是否存在同名文件。不存在：直接存入新节点；存在：检查repeType代表的上传类型：覆盖、跳过、保留两者。
        final List<Node> files = this.fm.queryByParentFolderId(folderId);
        if (files.parallelStream()
                .filter(e -> e.getFileCreator().equals(account))
                .anyMatch((e) -> e.getFileName().equals(originalFileName))) {
            // 针对存在同名文件的操作
            if (repeType != null) {
                switch (repeType) {
                    // 跳过则忽略上传请求并直接返回上传成功（跳过不应上传）
                    case "skip":
                        return UPLOADSUCCESS;
                    // 覆盖则找到已存在文件节点的File并将新内容写入其中，同时更新原节点信息（除了文件名、父目录和ID之外的全部信息）
                    case "cover":
                        return UPLOADSUCCESS;
                    // 保留两者，使用型如“xxxxx (n).xx”的形式命名新文件。其中n为计数，例如已经存在2个文件，则新文件的n记为2
                    case "both":
                        // 设置新文件名为标号形式
                        fileName = FileNodeUtil.getNewNodeName(originalFileName, files);
                        break;
                    default:
                        // 其他声明，容错，暂无效果
                        return UPLOADERROR;
                }
            } else {
                // 如果既有重复文件、同时又没声明如何操作，则直接上传失败。
                return UPLOADERROR;
            }
        }
        // 判断该文件夹内的文件数量是否超限
        if (fm.countByParentFolderId(folderId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
            return FILES_TOTAL_OUT_OF_LIMIT;
        }
        // 将文件存入节点

        final Node f2 = new Node();
        f2.setFileId(UUID.randomUUID().toString());
        if (account != null) {
            f2.setFileCreator(account);
        } else {
            f2.setFileCreator("\u533f\u540d\u7528\u6237");
        }
        f2.setFileCreationDate(ServerTimeUtil.accurateToSecond());
        f2.setFileName(fileName);
        f2.setFileParentFolder(folderId);
        Node node = fm.queryByFileMd5(fileMd5).get(0);
        f2.setFilePath(node.getFilePath());
        f2.setFileLength(node.getFileLength());
        f2.setFileSize(node.getFileSize());
        f2.setFileMd5(node.getFileMd5());
        f2.setDelFlag(FileDelFlag.FALSE.getName());
        int i = 0;
        // 尽可能避免UUID重复的情况发生，重试10次
        while (true) {
            try {
                if (this.fm.insert(f2) > 0) {
                    if (hasRepeatNode(f2)) {
                        return UPLOADERROR;
                    } else {
                        this.lu.writeUploadFileEvent(request, f2, account);
                        return UPLOADSUCCESS;
                    }
                }
                break;
            } catch (Exception e) {
                f2.setFileId(UUID.randomUUID().toString());
                i++;
            }
            if (i >= 10) {
                break;
            }
        }
        return UPLOADERROR;
    }

    // 执行上传操作，接收文件并存入文件节点
    public String doUploadFile(final HttpServletRequest request, final HttpServletResponse response,
                               final MultipartFile file) {
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        final String folderId = request.getParameter("folderId");
        final String originalFileName = new String(file.getOriginalFilename().getBytes(Charset.forName("UTF-8")),
                Charset.forName("UTF-8"));
        String fileName = originalFileName;
        final String repeType = request.getParameter("repeType");
        // 再次检查上传文件名与目标目录ID
        if (folderId == null || folderId.length() <= 0 || originalFileName == null || originalFileName.length() <= 0) {
            return UPLOADERROR;
        }
        Folder folder = flm.queryById(folderId);
        if (folder == null) {
            return UPLOADERROR;
        }
        // 检查上传权限
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            return UPLOADERROR;
        }
        // 检查上传文件体积是否超限
        long mufs = ConfigureReader.instance().getUploadFileSize(account);
        if (mufs >= 0 && file.getSize() > mufs) {
            return UPLOADERROR;
        }
        // 检查是否存在同名文件。不存在：直接存入新节点；存在：检查repeType代表的上传类型：覆盖、跳过、保留两者。
        final List<Node> files = this.fm.queryByParentFolderId(folderId);
        if (files.parallelStream()
                .filter(e -> e.getFileCreator().equals(account))
                .anyMatch((e) -> e.getFileName().equals(originalFileName))) {
            // 针对存在同名文件的操作
            if (repeType != null) {
                switch (repeType) {
                    // 跳过则忽略上传请求并直接返回上传成功（跳过不应上传）
                    case "skip":
                        return UPLOADSUCCESS;
                    // 覆盖则找到已存在文件节点的File并将新内容写入其中，同时更新原节点信息（除了文件名、父目录和ID之外的全部信息）
                    case "cover":
                        // 其中覆盖操作同时要求用户必须具备删除权限
                        if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                fu.getAllFoldersId(folderId))) {
                            return UPLOADERROR;
                        }
                        for (Node f : files) {
                            if (f.getFileName().equals(originalFileName)) {
                                File file2 = fbu.getFileFromBlocks(f);
                                try {
                                    String md5 = DigestUtils.md5Hex(new FileInputStream(file2));
                                    List<Node> sameBlockFiles = fm.queryByFileMd5(md5);
                                    if (sameBlockFiles == null || sameBlockFiles.size() == 0) {
                                        file.transferTo(file2);
                                    } else {
                                        f.setFilePath(sameBlockFiles.get(0).getFilePath());
                                    }

                                    f.setFileLength(file.getSize());
                                    f.setFileSize(FormatFileSizeUtil.formatSize(file.getSize()));
                                    f.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                                    f.setFileMd5(md5);
                                    f.setDelFlag(FileDelFlag.FALSE.getName());
                                    if (account != null) {
                                        f.setFileCreator(account);
                                    } else {
                                        f.setFileCreator("\u533f\u540d\u7528\u6237");
                                    }
                                    if (fm.update(f) > 0) {
                                        this.lu.writeUploadFileEvent(request, f, account);
                                        return UPLOADSUCCESS;
                                    } else {
                                        return UPLOADERROR;
                                    }
                                } catch (Exception e) {
                                    return UPLOADERROR;
                                }
                            }
                        }
                        return UPLOADERROR;
                    // 保留两者，使用型如“xxxxx (n).xx”的形式命名新文件。其中n为计数，例如已经存在2个文件，则新文件的n记为2
                    case "both":
                        // 设置新文件名为标号形式
                        fileName = FileNodeUtil.getNewNodeName(originalFileName, files);
                        break;
                    default:
                        // 其他声明，容错，暂无效果
                        return UPLOADERROR;
                }
            } else {
                // 如果既有重复文件、同时又没声明如何操作，则直接上传失败。
                return UPLOADERROR;
            }
        }
        // 判断该文件夹内的文件数量是否超限
        if (fm.countByParentFolderId(folderId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
            return FILES_TOTAL_OUT_OF_LIMIT;
        }
        // 将文件存入节点并获取其存入生成路径，型如“UUID.block”形式。
        String md5, path;
        try {
            md5 = DigestUtils.md5Hex(file.getInputStream());
        } catch (IOException e) {
            return UPLOADERROR;
        }
        List<Node> sameBlockFiles = fm.queryByFileMd5(md5);
        if (sameBlockFiles == null || sameBlockFiles.size() == 0) {
            path = this.fbu.saveToFileBlocks(file);
            if (path.equals("ERROR")) {
                return UPLOADERROR;
            }
        } else {
            path = sameBlockFiles.get(0).getFilePath();
        }
        final long flength = file.getSize();
        final String fsize = FormatFileSizeUtil.formatSize(flength);
        final Node f2 = new Node();
        f2.setFileId(UUID.randomUUID().toString());
        if (account != null) {
            f2.setFileCreator(account);
        } else {
            f2.setFileCreator("\u533f\u540d\u7528\u6237");
        }
        f2.setFileCreationDate(ServerTimeUtil.accurateToSecond());
        f2.setFileName(fileName);
        f2.setFileParentFolder(folderId);
        f2.setFilePath(path);
        f2.setFileLength(flength);
        f2.setFileSize(fsize);
        f2.setFileMd5(md5);

        f2.setDelFlag(FileDelFlag.FALSE.getName());
        int i = 0;
        // 尽可能避免UUID重复的情况发生，重试10次
        while (true) {
            try {
                if (this.fm.insert(f2) > 0) {
                    if (hasRepeatNode(f2)) {
                        return UPLOADERROR;
                    } else {
                        this.lu.writeUploadFileEvent(request, f2, account);
                        return UPLOADSUCCESS;
                    }
                }
                break;
            } catch (Exception e) {
                f2.setFileId(UUID.randomUUID().toString());
                i++;
            }
            if (i >= 10) {
                break;
            }
        }
        return UPLOADERROR;
    }

    // 删除单个文件，该功能与删除多个文件重复，计划合并二者
    public String reallyDeleteFile(final HttpServletRequest request) {
        // 接收参数并接续要删除的文件
        final String fileId = request.getParameter("fileId");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (fileId == null || fileId.length() <= 0) {
            return ERROR_PARAMETER;
        }
        // 确认要删除的文件存在
        final Node file = this.fm.queryById(fileId);
        if (file == null) {
            return "deleteFileSuccess";
        }
        final Folder f = this.flm.queryById(file.getFileParentFolder());
        // 权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                fu.getAllFoldersId(file.getFileParentFolder()))
                || !ConfigureReader.instance().accessFolder(f, account)) {
            return NO_AUTHORIZED;
        }
        // 从文件块删除
        if (!this.fbu.deleteFromFileBlocks(file)) {
            return "cannotDeleteFile";
        }
        // 从节点删除
        if (this.fm.deleteById(fileId) > 0) {
            this.lu.writeDeleteFileEvent(request, file);
            return "deleteFileSuccess";
        }
        return "cannotDeleteFile";
    }


    // 删除单个文件，该功能与删除多个文件重复，计划合并二者
    public String deleteFile(final HttpServletRequest request) {
        // 接收参数并接续要删除的文件
        final String fileId = request.getParameter("fileId");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (fileId == null || fileId.length() <= 0) {
            return ERROR_PARAMETER;
        }
        // 确认要删除的文件存在
        final Node file = this.fm.queryById(fileId);
        if (file == null) {
            return "deleteFileSuccess";
        }
        final Folder f = this.flm.queryById(file.getFileParentFolder());
        // 权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                fu.getAllFoldersId(file.getFileParentFolder()))
                || !ConfigureReader.instance().accessFolder(f, account)) {
            return NO_AUTHORIZED;
        }

        // 从节点删除
        file.setDelFlag(FileDelFlag.TRUE.getName());
        file.setFileCreationDate(String.valueOf(System.currentTimeMillis()));
        file.setFileParentFolder("recycle");
        if (this.fm.update(file) > 0) {
            this.lu.writeDeleteFileEvent(request, file);
            return "deleteFileSuccess";
        }
        return "cannotDeleteFile";
    }

    // 普通下载：下载单个文件
    public void doDownloadFile(final HttpServletRequest request, final HttpServletResponse response) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 权限检查
        // 找到要下载的文件节点
        final String fileId = request.getParameter("fileId");
        if (fileId != null) {
            final Node f = this.fm.queryById(fileId);
            if (f != null) {
                if (accessAuthUtil.authorized(account, AccountAuth.DOWNLOAD_FILES,
                        fu.getAllFoldersId(f.getFileParentFolder()))) {
                    Folder folder = flm.queryById(f.getFileParentFolder());
                    if (accessAuthUtil.accessFolder(folder, account) || accessAuthUtil.accessSendFile(f, account)) {
                        // 执行写出
                        final File fo = this.fbu.getFileFromBlocks(f);
                        if (fo != null) {
                            writeRangeFileStream(request, response, fo, f.getFileName(), CONTENT_TYPE,
                                    ConfigureReader.instance().getDownloadMaxRate(account));
                            // 日志记录（仅针对一次下载）
                            if (request.getHeader("Range") == null) {
                                this.lu.writeDownloadFileEvent(request, f);
                            }
                            return;
                        }
                    }
                }
            }
        }
        try {
            //  处理无法下载的资源
            response.sendError(404);
        } catch (IOException e) {
        }
    }

    // 重命名文件
    public String doRenameFile(final HttpServletRequest request) {
        final String fileId = request.getParameter("fileId");
        final String newFileName = request.getParameter("newFileName");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 参数检查
        if (fileId == null || fileId.length() <= 0 || newFileName == null || newFileName.length() <= 0) {
            return ERROR_PARAMETER;
        }
        if (!TextFormateUtil.instance().matcherFileName(newFileName) || newFileName.indexOf(".") == 0) {
            return ERROR_PARAMETER;
        }
        final Node file = this.fm.queryById(fileId);
        if (file == null) {
            return ERROR_PARAMETER;
        }
        final Folder folder = flm.queryById(file.getFileParentFolder());
        if (!ConfigureReader.instance().accessFolder(folder, account)) {
            return NO_AUTHORIZED;
        }
        // 权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.RENAME_FILE_OR_FOLDER,
                fu.getAllFoldersId(file.getFileParentFolder()))) {
            return NO_AUTHORIZED;
        }
        if (!file.getFileName().equals(newFileName)) {
            // 不允许重名
            if (fm.queryBySomeFolder(fileId).parallelStream()
                    .filter(e -> e.getFileCreator().equals(account))
                    .anyMatch((e) -> e.getFileName().equals(newFileName))) {
                return "nameOccupied";
            }
            // 更新文件名
            final Map<String, String> map = new HashMap<String, String>();
            map.put("fileId", fileId);
            map.put("newFileName", newFileName);
            if (this.fm.updateFileNameById(map) == 0) {
                // 并写入日志
                return "cannotRenameFile";
            }
        }
        this.lu.writeRenameFileEvent(request, file, newFileName);
        return "renameFileSuccess";
    }

    // 删除所有选中文件和文件夹
    public String deleteCheckedFiles(final HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        try {
            // 得到要删除的文件ID列表
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            // 对每个要删除的文件节点进行确认并删除
            for (final String fileId : idList) {
                if (fileId == null || fileId.length() == 0) {
                    return ERROR_PARAMETER;
                }
                final Node file = this.fm.queryById(fileId);
                if (file == null) {
                    continue;
                }
                final Folder folder = flm.queryById(file.getFileParentFolder());
                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                        fu.getAllFoldersId(file.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }
                // 删除文件块
                if (!this.fbu.deleteFromFileBlocks(file)) {
                    return "cannotDeleteFile";
                }
                // 删除文件节点
                if (this.fm.deleteById(fileId) <= 0) {
                    return "cannotDeleteFile";
                }
                // 日志记录
                this.lu.writeDeleteFileEvent(request, file);
            }
            // 删完选中的文件，再去删文件夹
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (String fid : fidList) {
                Folder folder = flm.queryById(fid);
                if (folder == null) {
                    continue;
                }
                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                final List<Folder> l = this.fu.getParentList(fid);
                if (fu.deleteAllChildFolder(fid) <= 0) {
                    return "cannotDeleteFile";
                } else {
                    this.lu.writeDeleteFolderEvent(request, folder, l);
                }
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "deleteFileSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }

    // 伪删除所有选中文件和文件夹
    public String fakerDeleteCheckedFiles(final HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        try {
            // 得到要删除的文件ID列表
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            // 对每个要删除的文件节点进行确认并删除
            for (final String fileId : idList) {
                if (fileId == null || fileId.length() == 0) {
                    return ERROR_PARAMETER;
                }
                final Node file = this.fm.queryById(fileId);
                if (file == null) {
                    continue;
                }
                final Folder folder = flm.queryById(file.getFileParentFolder());
                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                        fu.getAllFoldersId(file.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }

                // 从节点删除
                file.setDelFlag(FileDelFlag.TRUE.getName());
                file.setFileCreator(account);
                file.setFileCreationDate(String.valueOf(System.currentTimeMillis()));
                file.setFileParentFolder("recycle");
                if (this.fm.update(file) > 0) {
                    // 日志记录
                    this.lu.writeDeleteFileEvent(request, file);
                    return "deleteFileSuccess";
                } else {
                    return "cannotDeleteFile";
                }
            }
            // 删完选中的文件，再去删文件夹
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (String fid : fidList) {
                Folder folder = flm.queryById(fid);
                if (folder == null) {
                    continue;
                }
                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                final List<Folder> l = this.fu.getParentList(fid);
                if (fu.fakeDeleteAllChildFolder(fid) <= 0) {
                    return "cannotDeleteFile";
                } else {
                    this.lu.writeDeleteFolderEvent(request, folder, l);
                }
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "deleteFileSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }


    // 打包下载功能：前置——压缩要打包下载的文件
    public String downloadCheckedFiles(final HttpServletRequest request) {
        if (ConfigureReader.instance().isEnableDownloadByZip()) {
            final String account = (String) request.getSession().getAttribute("ACCOUNT");
            final String strIdList = request.getParameter("strIdList");
            final String strFidList = request.getParameter("strFidList");
            try {
                // 获得要打包下载的文件ID
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                // 创建ZIP压缩包并将全部文件压缩
                if (idList.size() > 0 || fidList.size() > 0) {
                    final String zipname = this.fbu.createZip(idList, fidList, account);
                    this.lu.writeDownloadCheckedFileEvent(request, idList);
                    // 返回生成的压缩包路径
                    return zipname;
                }
            } catch (Exception ex) {
                lu.writeException(ex);
            }
        }
        return "ERROR";
    }

    // 打包下载功能：执行——下载压缩好的文件
    public void downloadCheckedFilesZip(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final String zipname = request.getParameter("zipId");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (zipname != null && !zipname.equals("ERROR")) {
            final String tfPath = ConfigureReader.instance().getTemporaryfilePath();
            final File zip = new File(tfPath, zipname);
            String fname = "sxwpan_" + ServerTimeUtil.accurateToDay() + "_\u6253\u5305\u4e0b\u8f7d.zip";
            if (zip.exists()) {
                writeRangeFileStream(request, response, zip, fname, CONTENT_TYPE,
                        ConfigureReader.instance().getDownloadMaxRate(account));
                zip.delete();
            }
        }
    }

    public String getPackTime(final HttpServletRequest request) {
        if (ConfigureReader.instance().isEnableDownloadByZip()) {
            final String account = (String) request.getSession().getAttribute("ACCOUNT");
            final String strIdList = request.getParameter("strIdList");
            final String strFidList = request.getParameter("strFidList");
            try {
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                for (String fid : fidList) {
                    countFolderFilesId(account, fid, fidList);
                }
                long packTime = 0L;
                for (final String fid : idList) {
                    final Node n = this.fm.queryById(fid);
                    if (ConfigureReader.instance().authorized(account, AccountAuth.DOWNLOAD_FILES,
                            fu.getAllFoldersId(n.getFileParentFolder()))
                            && ConfigureReader.instance().accessFolder(flm.queryById(n.getFileParentFolder()),
                            account)) {
                        final File f = fbu.getFileFromBlocks(n);
                        if (f != null && f.exists()) {
                            packTime += f.length() / 25000000L;
                        }
                    }
                }
                if (packTime < 4L) {
                    return "\u9a6c\u4e0a\u5b8c\u6210";
                }
                if (packTime >= 4L && packTime < 10L) {
                    return "\u5927\u7ea610\u79d2";
                }
                if (packTime >= 10L && packTime < 35L) {
                    return "\u4e0d\u5230\u534a\u5206\u949f";
                }
                if (packTime >= 35L && packTime < 65L) {
                    return "\u5927\u7ea61\u5206\u949f";
                }
                if (packTime >= 65L) {
                    return "\u8d85\u8fc7" + packTime / 60L
                            + "\u5206\u949f\uff0c\u8017\u65f6\u8f83\u957f\uff0c\u5efa\u8bae\u76f4\u63a5\u4e0b\u8f7d";
                }
            } catch (Exception ex) {
                lu.writeException(ex);
            }
        }
        return "0";
    }

    @Override
    public String getFileOccupancySpace(final HttpServletRequest request) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        return FormatFileSizeUtil.formatSize(fm.queryFileOccupancySpace(account));
    }

    @Override
    public String getFileIsUpLoaded(final HttpServletRequest request) {
        final String fileMd5 = request.getParameter("fileMd5");
        List<Node> files = fm.queryByFileMd5(fileMd5);
        if (files == null || files.size() == 0) {
            return "false";
        } else {
            return "true";
        }
    }

    // 用于迭代获得全部文件夹内的文件ID（方便预测耗时）
    private void countFolderFilesId(String account, String fid, List<String> idList) {
        Folder f = flm.queryById(fid);
        if (ConfigureReader.instance().accessFolder(f, account)) {
            idList.addAll(Arrays.asList(
                    fm.queryByParentFolderId(fid).parallelStream().map((e) -> e.getFileId()).toArray(String[]::new)));
            List<Folder> cFolders = flm.queryByParentId(fid);
            for (Folder cFolder : cFolders) {
                countFolderFilesId(account, cFolder.getFolderId(), idList);
            }
        }
    }

    // 执行移动文件操作
    @Override
    public String doMoveFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String strOptMap = request.getParameter("strOptMap");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        if (targetFolder == null) {
            return ERROR_PARAMETER;
        }
        if (!ConfigureReader.instance().accessFolder(targetFolder, account)) {
            return NO_AUTHORIZED;
        }
        if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(locationpath))) {
            return NO_AUTHORIZED;
        }
        try {
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            final Map<String, String> optMap = gson.fromJson(strOptMap, new TypeToken<Map<String, String>>() {
            }.getType());
            for (final String id : idList) {
                if (id == null || id.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final Node node = this.fm.queryById(id);
                if (node == null) {
                    return ERROR_PARAMETER;
                }
                if (node.getFileParentFolder().equals(locationpath)) {
                    continue;
                }
                if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                        fu.getAllFoldersId(node.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }
                if (fm.queryByParentFolderId(locationpath).parallelStream()
                        .filter(e -> e.getFileCreator().equals(account))
                        .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                    if (optMap.get(id) == null) {
                        return ERROR_PARAMETER;
                    }
                    switch (optMap.get(id)) {
                        case "cover":
                            if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            Node n = fm.queryByParentFolderId(locationpath).parallelStream()
                                    .filter((e) -> e.getFileName().equals(node.getFileName()) && e.getFileCreator().equals(account))
                                    .findFirst().get();
                            if (fm.deleteById(n.getFileId()) > 0) {
                                node.setFileCreator(account);
                                node.setFileParentFolder(locationpath);
                                if (this.fm.update(node) <= 0) {
                                    return "cannotMoveFiles";
                                }
                            } else {
                                return "cannotMoveFiles";
                            }
                            this.lu.writeMoveFileEvent(request, node);
                            break;
                        case "both":
                            if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FILES_TOTAL_OUT_OF_LIMIT;
                            }
                            node.setFileCreator(account);
                            node.setFileName(FileNodeUtil.getNewNodeName(node.getFileName(),
                                    fm.queryByParentFolderId(locationpath)));
                            node.setFileParentFolder(locationpath);
                            if (fm.update(node) <= 0) {
                                return "cannotMoveFiles";
                            }
                            this.lu.writeMoveFileEvent(request, node);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FILES_TOTAL_OUT_OF_LIMIT;
                    }
                    node.setFileCreator(account);
                    node.setFileParentFolder(locationpath);
                    if (this.fm.update(node) <= 0) {
                        return "cannotMoveFiles";
                    }
                    this.lu.writeMoveFileEvent(request, node);
                }
            }
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (final String fid : fidList) {
                if (fid == null || fid.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final Folder folder = this.flm.queryById(fid);
                if (folder == null) {
                    return ERROR_PARAMETER;
                }
                if (folder.getFolderParent().equals(locationpath)) {
                    continue;
                }
                Folder parentFolder = this.flm.queryById(locationpath);
                int pc = locationpath.equals("root") ? folder.getFolderConstraint() : parentFolder.getFolderConstraint();

                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                if (fid.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                        .anyMatch((e) -> e.getFolderId().equals(folder.getFolderId()))) {
                    return ERROR_PARAMETER;
                }
                if (flm.queryByParentId(locationpath).parallelStream()
                        .filter(e -> e.getFolderCreator().equals(account))
                        .anyMatch((e) -> e.getFolderName().equals(folder.getFolderName()))) {
                    if (optMap.get(fid) == null) {
                        return ERROR_PARAMETER;
                    }
                    switch (optMap.get(fid)) {
                        case "cover":
                            if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            Folder f = flm.queryByParentId(locationpath).parallelStream()
                                    .filter((e) -> e.getFolderName().equals(folder.getFolderName()) && e.getFolderCreator().equals(account)).findFirst().get();
                            folder.setFolderCreator(account);
                            folder.setFolderParent(locationpath);
                            if (this.flm.update(folder) > 0) {
                                fu.changeChildFolderConstraint(folder.getFolderId(), pc);
                                if (fu.fakeDeleteAllChildFolder(f.getFolderId()) > 0) {
                                    this.lu.writeMoveFileEvent(request, folder);
                                    break;
                                }
                            }
                            return "cannotMoveFiles";
                        case "both":
                            if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FOLDERS_TOTAL_OUT_OF_LIMIT;
                            }
                            folder.setFolderCreator(account);
                            folder.setFolderParent(locationpath);
                            folder.setFolderName(FileNodeUtil.getNewFolderName(folder.getFolderName(),
                                    flm.queryByParentId(locationpath)));
                            if (this.flm.update(folder) > 0) {
                                fu.changeChildFolderConstraint(folder.getFolderId(), pc);
                                this.lu.writeMoveFileEvent(request, folder);
                                break;
                            } else {
                                return "cannotMoveFiles";
                            }
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FOLDERS_TOTAL_OUT_OF_LIMIT;
                    }
                    folder.setFolderCreator(account);
                    folder.setFolderParent(locationpath);
                    if (this.fu.updateAllChildFolder(folder) > 0) {
                        fu.changeChildFolderConstraint(folder.getFolderId(), pc);
                        this.lu.writeMoveFileEvent(request, folder);
                    } else {
                        return "cannotMoveFiles";
                    }
                }
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "moveFilesSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }

    // 执行还原文件操作
    @Override
    public String doRestoreFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String strOptMap = request.getParameter("strOptMap");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        if (targetFolder == null) {
            return ERROR_PARAMETER;
        }
        if (!ConfigureReader.instance().accessFolder(targetFolder, account)) {
            return NO_AUTHORIZED;
        }
        if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(locationpath))) {
            return NO_AUTHORIZED;
        }
        try {
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            final Map<String, String> optMap = gson.fromJson(strOptMap, new TypeToken<Map<String, String>>() {
            }.getType());
            for (final String id : idList) {
                if (id == null || id.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final Node node = this.fm.queryById(id);
                if (node == null) {
                    return ERROR_PARAMETER;
                }
                if (node.getFileParentFolder().equals(locationpath)) {
                    continue;
                }
                if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                        fu.getAllFoldersId(node.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }
                if (fm.queryByParentFolderId(locationpath).parallelStream()
                        .filter(e -> e.getFileCreator().equals(account))
                        .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                    if (optMap.get(id) == null) {
                        return ERROR_PARAMETER;
                    }
                    switch (optMap.get(id)) {
                        case "cover":
                            if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            Node n = fm.queryByParentFolderId(locationpath).parallelStream()
                                    .filter((e) -> e.getFileName().equals(node.getFileName()) && e.getFileCreator().equals(account)).findFirst().get();
                            if (fm.deleteById(n.getFileId()) > 0) {
                                Map<String, String> map = new HashMap<>();

                                node.setDelFlag(FileDelFlag.FALSE.getName());
                                node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                                node.setFileParentFolder(locationpath);

                                if (this.fm.update(node) <= 0) {
                                    return "cannotRestoreFiles";
                                }
                            } else {
                                return "cannotRestoreFiles";
                            }
                            this.lu.writeRestoreFileEvent(request, node);
                            break;
                        case "both":
                            if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FILES_TOTAL_OUT_OF_LIMIT;
                            }
                            node.setFileName(FileNodeUtil.getNewNodeName(node.getFileName(),
                                    fm.queryByParentFolderId(locationpath)));
                            node.setDelFlag(FileDelFlag.FALSE.getName());
                            node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                            node.setFileParentFolder(locationpath);
                            if (fm.update(node) <= 0) {
                                return "cannotRestoreFiles";
                            }
                            this.lu.writeRestoreFileEvent(request, node);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FILES_TOTAL_OUT_OF_LIMIT;
                    }
                    node.setDelFlag(FileDelFlag.FALSE.getName());
                    node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                    node.setFileParentFolder(locationpath);
                    if (this.fm.update(node) <= 0) {
                        return "cannotRestoreFiles";
                    }
                    this.lu.writeRestoreFileEvent(request, node);
                }
            }
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (final String fid : fidList) {
                if (fid == null || fid.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final Folder folder = this.flm.queryById(fid);
                if (folder == null) {
                    return ERROR_PARAMETER;
                }
                if (folder.getFolderParent().equals(locationpath)) {
                    continue;
                }

                Folder parentFolder = this.flm.queryById(locationpath);
                int pc = parentFolder.getFolderId().equals("root") ? folder.getFolderConstraint() : parentFolder.getFolderConstraint();

                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                if (fid.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                        .anyMatch((e) -> e.getFolderId().equals(folder.getFolderId()))) {
                    return ERROR_PARAMETER;
                }
                if (flm.queryByParentId(locationpath).parallelStream()
                        .filter(e -> e.getFolderCreator().equals(account))
                        .anyMatch((e) -> e.getFolderName().equals(folder.getFolderName()))) {
                    if (optMap.get(fid) == null) {
                        return ERROR_PARAMETER;
                    }
                    switch (optMap.get(fid)) {
                        case "cover":
                            if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            Folder f = flm.queryByParentId(locationpath).parallelStream()
                                    .filter((e) -> e.getFolderName().equals(folder.getFolderName()) && e.getFolderCreator().equals(account)).findFirst().get();
                            fu.changeChildFolderConstraint(folder.getFolderId(), pc);
                            if (this.fu.restoreAllChildFolder(folder.getFolderId(), locationpath) > 0) {
                                if (fu.fakeDeleteAllChildFolder(f.getFolderId()) > 0) {
                                    this.lu.writeRestoreFileEvent(request, folder);
                                    break;
                                }
                            }
                            return "cannotRestoreFiles";
                        case "both":
                            if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FOLDERS_TOTAL_OUT_OF_LIMIT;
                            }

                            if (this.fu.restoreAllChildFolder(folder.getFolderId(), locationpath) > 0) {
                                Map<String, String> map2 = new HashMap<String, String>();
                                map2.put("folderId", folder.getFolderId());
                                map2.put("newName", FileNodeUtil.getNewFolderName(folder.getFolderName(),
                                        flm.queryByParentId(locationpath)));
                                fu.changeChildFolderConstraint(folder.getFolderId(), pc);
                                if (flm.updateFolderNameById(map2) <= 0) {
                                    return "cannotRestoreFiles";
                                }
                                this.lu.writeRestoreFileEvent(request, folder);
                                break;
                            }
                            this.lu.writeRestoreFileEvent(request, folder);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FOLDERS_TOTAL_OUT_OF_LIMIT;
                    }

                    if (this.fu.restoreAllChildFolder(folder.getFolderId(), locationpath) > 0) {
                        fu.changeChildFolderConstraint(folder.getFolderId(), pc);
                        this.lu.writeRestoreFileEvent(request, folder);
                    } else {
                        return "cannotRestoreFiles";
                    }
                }
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "restoreFilesSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }

    private String copyFolderHelp(Folder folder, String newFolderParent) {
        List<String> ipPidList = fm.queryNodeTree(folder.getFolderCreator());
        int folderConstraint = folder.getFolderConstraint();
        String account = folder.getFolderCreator();
        ConcurrentHashMap<String, String> data = new ConcurrentHashMap<String, String>();
        ipPidList.stream().parallel().forEach(e -> {
            String[] idPid = e.split(",");
            String id = idPid[0];
            String pid = idPid[1];
            data.put(id, pid);
        });
        NodeTreeUtil.TagTreeNode rootNode = NodeTreeUtil.TreeBuilder.createOneTree(data, folder.getFolderId());

        folder.setFolderParent(newFolderParent);
        String newFolderId = UUID.randomUUID().toString();
        folder.setFolderId(newFolderId);
        // folder.setFolderConstraint(folderConstraint);
        flm.insertNewFolder(folder);

        NodeTreeUtil.Tag rootTag = (NodeTreeUtil.Tag) rootNode.getUserObject();
        rootTag.setId(newFolderId);
        rootTag.setPid(newFolderParent);

        Queue<NodeTreeUtil.TagTreeNode> queue = new LinkedList<>();
        queue.add(rootNode);
        while (!queue.isEmpty()) {
            NodeTreeUtil.TagTreeNode node = queue.poll();
            NodeTreeUtil.Tag nodeTag = (NodeTreeUtil.Tag) node.getUserObject();
            Enumeration<NodeTreeUtil.TagTreeNode> childs = node.children();
            while (childs.hasMoreElements()) {
                NodeTreeUtil.TagTreeNode child = childs.nextElement();
                NodeTreeUtil.Tag childTag = (NodeTreeUtil.Tag) child.getUserObject();

                if (!child.isLeaf()) {
                    String childFolderId = childTag.getId();
                    Folder childFolder = flm.queryById(childFolderId);
                    String newChildFolderId = UUID.randomUUID().toString();
                    childFolder.setFolderId(newChildFolderId);
                    childFolder.setFolderCreator(account);
                    // childFolder.setFolderConstraint(folderConstraint);
                    childFolder.setFolderParent(nodeTag.getId());
                    flm.insertNewFolder(childFolder);

                    childTag.setId(newChildFolderId);
                    childTag.setPid(nodeTag.getId());
                } else {
                    String childId = childTag.getId();
                    Node childFile = fm.queryById(childId);
                    if (childFile != null) {
                        String newChildId = UUID.randomUUID().toString();
                        childFile.setFileId(newChildId);
                        childFile.setFileCreator(account);
                        childFile.setFileParentFolder(nodeTag.getId());
                        fm.insert(childFile);

                        childTag.setId(newChildId);
                        childTag.setPid(nodeTag.getId());
                    } else {
                        String childFolderId = childTag.getId();
                        Folder childFolder = flm.queryById(childFolderId);
                        String newChildFolderId = UUID.randomUUID().toString();
                        childFolder.setFolderId(newChildFolderId);
                        childFolder.setFolderCreator(account);
                        // childFolder.setFolderConstraint(folderConstraint);
                        childFolder.setFolderParent(nodeTag.getId());
                        flm.insertNewFolder(childFolder);

                        childTag.setId(newChildFolderId);
                        childTag.setPid(nodeTag.getId());
                    }
                }
                queue.add(child);
            }
        }
        return newFolderId;
    }

    @Override
    public String doCopyFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String strOptMap = request.getParameter("strOptMap");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        if (targetFolder == null) {
            return ERROR_PARAMETER;
        }
        if (!accessAuthUtil.accessFolder(targetFolder, account)) {
            return NO_AUTHORIZED;
        }
        if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(locationpath))) {
            return NO_AUTHORIZED;
        }
        try {
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            final Map<String, String> optMap = gson.fromJson(strOptMap, new TypeToken<Map<String, String>>() {
            }.getType());
            for (final String id : idList) {
                if (id == null || id.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                Node node = this.fm.queryById(id);
                FileSend fileSend = fsm.queryById(id);
                if (fileSend != null){
                    node = this.fm.queryById(fileSend.getFileId());
                }
                final Node fNode = node;
                if (node == null) {
                    return ERROR_PARAMETER;
                }
                if (node.getFileParentFolder().equals(locationpath)) {
                    continue;
                }
                if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.COPY_FILES,
                        fu.getAllFoldersId(node.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }
                if (fm.queryByParentFolderId(locationpath).parallelStream()
                        .filter(e -> e.getFileCreator().equals(account))
                        .anyMatch((e) -> e.getFileName().equals(fNode.getFileName()))) {
                    if (optMap.get(id) == null) {
                        return ERROR_PARAMETER;
                    }
                    switch (optMap.get(id)) {
                        case "cover":
                            if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            final Node n = fm.queryByParentFolderId(locationpath).parallelStream()
                                    .filter((e) -> e.getFileName().equals(fNode.getFileName()) && e.getFileCreator().equals(account)).findFirst().get();
                            if (fm.deleteById(n.getFileId()) > 0) {
                                node.setFileParentFolder(locationpath);
                                node.setFileId(UUID.randomUUID().toString());
                                if (account != null) {
                                    node.setFileCreator(account);
                                } else {
                                    node.setFileCreator("\u533f\u540d\u7528\u6237");
                                }
                                node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                                int i = 0;
                                // 尽可能避免UUID重复的情况发生，重试10次
                                while (true) {
                                    try {
                                        if (this.fm.insert(node) > 0) {
                                            break;
                                        }
                                    } catch (Exception e) {
                                        node.setFileId(UUID.randomUUID().toString());
                                        i++;
                                    }
                                    if (i >= 10) {
                                        return "cannotCopyFiles";
                                    }
                                }
                            }
                            this.lu.writeCopyFileEvent(request, node);
                            break;
                        case "both":
                            if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FILES_TOTAL_OUT_OF_LIMIT;
                            }
                            node.setFileName(FileNodeUtil.getNewNodeName(node.getFileName(),
                                    fm.queryByParentFolderId(locationpath)));
                            node.setFileParentFolder(locationpath);
                            node.setFileId(UUID.randomUUID().toString());
                            if (account != null) {
                                node.setFileCreator(account);
                            } else {
                                node.setFileCreator("\u533f\u540d\u7528\u6237");
                            }
                            node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                            int i = 0;
                            // 尽可能避免UUID重复的情况发生，重试10次
                            while (true) {
                                try {
                                    if (this.fm.insert(node) > 0) {
                                        break;
                                    }
                                } catch (Exception e) {
                                    node.setFileId(UUID.randomUUID().toString());
                                    i++;
                                }
                                if (i >= 10) {
                                    return "cannotCopyFiles";
                                }
                            }

                            this.lu.writeCopyFileEvent(request, node);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FILES_TOTAL_OUT_OF_LIMIT;
                    }
                    node.setFileParentFolder(locationpath);
                    node.setFileId(UUID.randomUUID().toString());
                    if (account != null) {
                        node.setFileCreator(account);
                    } else {
                        node.setFileCreator("\u533f\u540d\u7528\u6237");
                    }
                    node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                    int i = 0;
                    // 尽可能避免UUID重复的情况发生，重试10次
                    while (true) {
                        try {
                            if (this.fm.insert(node) > 0) {
                                break;
                            }
                        } catch (Exception e) {
                            node.setFileId(UUID.randomUUID().toString());
                            i++;
                        }
                        if (i >= 10) {
                            return "cannotCopyFiles";
                        }
                    }
                    this.lu.writeCopyFileEvent(request, node);
                }
            }
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (final String fid : fidList) {
                if (fid == null || fid.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                Folder folder = this.flm.queryById(fid);
                FileSend folderSend = this.fsm.queryById(fid);
                if (folderSend != null){
                    folder = this.flm.queryById(folderSend.getFileId());
                }
                 final Folder fFolder = folder;



                if (folder == null) {
                    return ERROR_PARAMETER;
                }
                if (folder.getFolderParent().equals(locationpath)) {
                    continue;
                }
                if (!accessAuthUtil.accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                if (fid.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                        .anyMatch((e) -> e.getFolderId().equals(fFolder.getFolderId()))) {
                    return ERROR_PARAMETER;
                }

                Folder parentFolder = this.flm.queryById(locationpath);
                int pc = parentFolder.getFolderId().equals("root") ? folder.getFolderConstraint() : parentFolder.getFolderConstraint();
                int folderConstraint = folder.getFolderConstraint();
                if (flm.queryByParentId(locationpath).parallelStream()
                        .filter(e -> e.getFolderCreator().equals(account))
                        .anyMatch((e) -> e.getFolderName().equals(fFolder.getFolderName()))) {
                    if (optMap.get(fid) == null) {
                        return ERROR_PARAMETER;
                    }

                    switch (optMap.get(fid)) {
                        case "cover":
                            if (!accessAuthUtil.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            Folder f = flm.queryByParentId(locationpath).parallelStream()
                                    .filter((e) -> e.getFolderName().equals(fFolder.getFolderName()) && e.getFolderCreator().equals(account)).findFirst().get();

                            folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
                            if (account != null) {
                                folder.setFolderCreator(account);
                            } else {
                                folder.setFolderCreator("匿名用户");
                            }
                            folder.setFolderConstraint(pc);
                            String newFolderId;
                            if (folderSend != null){
                                newFolderId = copySendFolderHelp(folderSend, locationpath);
                            }else {
                                newFolderId = copyFolderHelp(folder, locationpath);
                            }
                            // 设置子文件夹约束等级，子文件夹的约束等于父文件夹
                            fu.changeChildFolderConstraint(newFolderId, pc);
                            if (fu.deleteAllChildFolder(f.getFolderId()) > 0) {
                                this.lu.writeCopyFileEvent(request, folder);
                                break;
                            }
                            return "cannotCopyFiles";
                        case "both":
                            if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FOLDERS_TOTAL_OUT_OF_LIMIT;
                            }

                            if (folderConstraint > 0 && account == null) {
                                return "cannotCopyFolder";
                            }

                            folder.setFolderName(FileNodeUtil.getNewFolderName(folder.getFolderName(),
                                    flm.queryByParentId(locationpath)));
                            folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
                            if (account != null) {
                                folder.setFolderCreator(account);
                            } else {
                                folder.setFolderCreator("匿名用户");
                            }

                            String newFolderId2;
                            if (folderSend != null){
                                newFolderId2 = copySendFolderHelp(folderSend, locationpath);
                            }else {
                                newFolderId2 = copyFolderHelp(folder, locationpath);
                            }
                            // 设置子文件夹约束等级
                            fu.changeChildFolderConstraint(newFolderId2, pc);
                            this.lu.writeCopyFileEvent(request, folder);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FOLDERS_TOTAL_OUT_OF_LIMIT;
                    }

                    // 设置子文件夹约束等级，不允许子文件夹的约束等级比父文件夹低
                    if (folderConstraint > 0 && account == null) {
                        return "cannotCopyFolder";
                    }
                    folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
                    if (account != null) {
                        folder.setFolderCreator(account);
                    } else {
                        folder.setFolderCreator("匿名用户");
                    }
                    String newFolderId;
                    if (folderSend != null){
                        newFolderId = copySendFolderHelp(folderSend, locationpath);
                    }else {
                        newFolderId = copyFolderHelp(folder, locationpath);
                    }
                    fu.changeChildFolderConstraint(newFolderId, pc);
                }
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "copyFilesSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }

    @Override
    public String doCopySendFiles(HttpServletRequest request){
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String strOptMap = request.getParameter("strOptMap");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        if (targetFolder == null) {
            return ERROR_PARAMETER;
        }
        if (!accessAuthUtil.accessFolder(targetFolder, account)) {
            return NO_AUTHORIZED;
        }
        if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(locationpath))) {
            return NO_AUTHORIZED;
        }
        try {
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            final Map<String, String> optMap = gson.fromJson(strOptMap, new TypeToken<Map<String, String>>() {
            }.getType());
            for (final String id : idList) {
                if (id == null || id.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final FileSend fileSend = fsm.queryById(id);
                final Node node = this.fm.queryById(fileSend.getFileId());
                if (node == null) {
                    return ERROR_PARAMETER;
                }
                if (node.getFileParentFolder().equals(locationpath)) {
                    continue;
                }
                if (!accessAuthUtil.accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                    return NO_AUTHORIZED;
                }
                if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES,
                        fu.getAllFoldersId(node.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }
                if (fm.queryByParentFolderId(locationpath).parallelStream()
                        .filter(e -> e.getFileCreator().equals(account))
                        .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                    if (optMap.get(id) == null) {
                        return ERROR_PARAMETER;
                    }
                    switch (optMap.get(id)) {
                        case "cover":
                            if (!accessAuthUtil.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            final Node n = fm.queryByParentFolderId(locationpath).parallelStream()
                                    .filter((e) -> e.getFileName().equals(node.getFileName()) && e.getFileCreator().equals(account)).findFirst().get();
                            if (fm.deleteById(n.getFileId()) > 0) {
                                node.setFileParentFolder(locationpath);
                                node.setFileId(UUID.randomUUID().toString());
                                if (account != null) {
                                    node.setFileCreator(account);
                                } else {
                                    node.setFileCreator("\u533f\u540d\u7528\u6237");
                                }
                                node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                                int i = 0;
                                // 尽可能避免UUID重复的情况发生，重试10次
                                while (true) {
                                    try {
                                        if (this.fm.insert(node) > 0) {
                                            break;
                                        }
                                    } catch (Exception e) {
                                        node.setFileId(UUID.randomUUID().toString());
                                        i++;
                                    }
                                    if (i >= 10) {
                                        return "cannotCopyFiles";
                                    }
                                }
                            }
                            this.lu.writeCopyFileEvent(request, node);
                            break;
                        case "both":
                            if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FILES_TOTAL_OUT_OF_LIMIT;
                            }
                            node.setFileName(FileNodeUtil.getNewNodeName(node.getFileName(),
                                    fm.queryByParentFolderId(locationpath)));
                            node.setFileParentFolder(locationpath);
                            node.setFileId(UUID.randomUUID().toString());
                            if (account != null) {
                                node.setFileCreator(account);
                            } else {
                                node.setFileCreator("\u533f\u540d\u7528\u6237");
                            }
                            node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                            int i = 0;
                            // 尽可能避免UUID重复的情况发生，重试10次
                            while (true) {
                                try {
                                    if (this.fm.insert(node) > 0) {
                                        break;
                                    }
                                } catch (Exception e) {
                                    node.setFileId(UUID.randomUUID().toString());
                                    i++;
                                }
                                if (i >= 10) {
                                    return "cannotCopyFiles";
                                }
                            }

                            this.lu.writeCopyFileEvent(request, node);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (fm.countByParentFolderId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FILES_TOTAL_OUT_OF_LIMIT;
                    }
                    node.setFileParentFolder(locationpath);
                    node.setFileId(UUID.randomUUID().toString());
                    if (account != null) {
                        node.setFileCreator(account);
                    } else {
                        node.setFileCreator("\u533f\u540d\u7528\u6237");
                    }
                    node.setFileCreationDate(ServerTimeUtil.accurateToSecond());
                    int i = 0;
                    // 尽可能避免UUID重复的情况发生，重试10次
                    while (true) {
                        try {
                            if (this.fm.insert(node) > 0) {
                                break;
                            }
                        } catch (Exception e) {
                            node.setFileId(UUID.randomUUID().toString());
                            i++;
                        }
                        if (i >= 10) {
                            return "cannotCopyFiles";
                        }
                    }
                    this.lu.writeCopyFileEvent(request, node);
                }
            }
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (final String fid : fidList) {
                if (fid == null || fid.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final FileSend fileSend = this.fsm.queryById(fid);
                final Folder folder = this.flm.queryById(fileSend.getFileId());
                if (folder == null) {
                    return ERROR_PARAMETER;
                }
                if (folder.getFolderParent().equals(locationpath)) {
                    continue;
                }
                if (!accessAuthUtil.accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                if (fid.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                        .anyMatch((e) -> e.getFolderId().equals(folder.getFolderId()))) {
                    return ERROR_PARAMETER;
                }

                Folder parentFolder = this.flm.queryById(locationpath);
                int pc = parentFolder.getFolderId().equals("root") ? folder.getFolderConstraint() : parentFolder.getFolderConstraint();
                int folderConstraint = folder.getFolderConstraint();
                if (flm.queryByParentId(locationpath).parallelStream()
                        .filter(e -> e.getFolderCreator().equals(account))
                        .anyMatch((e) -> e.getFolderName().equals(folder.getFolderName()))) {
                    if (optMap.get(fid) == null) {
                        return ERROR_PARAMETER;
                    }

                    switch (optMap.get(fid)) {
                        case "cover":
                            if (!accessAuthUtil.authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
                                    fu.getAllFoldersId(locationpath))) {
                                return NO_AUTHORIZED;
                            }
                            Folder f = flm.queryByParentId(locationpath).parallelStream()
                                    .filter((e) -> e.getFolderName().equals(folder.getFolderName()) && e.getFolderCreator().equals(account)).findFirst().get();


                            folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
                            if (account != null) {
                                folder.setFolderCreator(account);
                            } else {
                                folder.setFolderCreator("匿名用户");
                            }
                            folder.setFolderConstraint(pc);
                            String newFolderId = copySendFolderHelp(fileSend, locationpath);
                            // 设置子文件夹约束等级，子文件夹的约束等于父文件夹
                            fu.changeChildFolderConstraint(newFolderId, pc);
                            if (fu.deleteAllChildFolder(f.getFolderId()) > 0) {
                                this.lu.writeCopyFileEvent(request, folder);
                                break;
                            }
                            return "cannotCopyFiles";
                        case "both":
                            if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                                return FOLDERS_TOTAL_OUT_OF_LIMIT;
                            }

                            if (folderConstraint > 0 && account == null) {
                                return "cannotCopyFolder";
                            }

                            folder.setFolderName(FileNodeUtil.getNewFolderName(folder.getFolderName(),
                                    flm.queryByParentId(locationpath)));
                            folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
                            if (account != null) {
                                folder.setFolderCreator(account);
                            } else {
                                folder.setFolderCreator("匿名用户");
                            }

                            String newFolderId2 = copySendFolderHelp(fileSend, locationpath);
                            // 设置子文件夹约束等级
                            fu.changeChildFolderConstraint(newFolderId2, pc);
                            this.lu.writeCopyFileEvent(request, folder);
                            break;
                        case "skip":
                            break;
                        default:
                            return ERROR_PARAMETER;
                    }
                } else {
                    if (flm.countByParentId(locationpath) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                        return FOLDERS_TOTAL_OUT_OF_LIMIT;
                    }

                    // 设置子文件夹约束等级，不允许子文件夹的约束等级比父文件夹低
                    if (folderConstraint > 0 && account == null) {
                        return "cannotCopyFolder";
                    }
                    folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
                    if (account != null) {
                        folder.setFolderCreator(account);
                    } else {
                        folder.setFolderCreator("匿名用户");
                    }
                    String newFolderId = copySendFolderHelp(fileSend, locationpath);
                    fu.changeChildFolderConstraint(newFolderId, pc);
                }
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "copyFilesSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }


    // 发送文件前的确认检查 （发送文件的前置操作）
    @Override
    public String confirmSendFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String fileReceiver = request.getParameter("fileReceiver");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        int needSendfilesCount = 0;
        int needSendFoldersCount = 0;

        try {
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());

            List<Node> repeNodes = new ArrayList<>();
            List<Folder> repeFolders = new ArrayList<>();

            Map<String, String> key = new HashMap<String, String>();
            key.put("fileSender", account);
            key.put("fileReceiver", fileReceiver);

            for (final String fileId : idList) {
                if (fileId == null || fileId.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final Node node = this.fm.queryById(fileId);
                if (node == null) {
                    return ERROR_PARAMETER;
                }
                if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.SEND_FILES,
                        fu.getAllFoldersId(node.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }

                if (fsm.queryBySenderAndReceiver(key).parallelStream()
                        .filter(e -> e.getFileType().equals(FileSendType.FILE.getName()))
                        .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                    repeNodes.add(node);
                } else {
                    needSendfilesCount++;
                }
            }
            for (final String folderId : fidList) {
                if (folderId == null || folderId.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                final Folder folder = this.flm.queryById(folderId);
                if (folder == null) {
                    return ERROR_PARAMETER;
                }
                if (!ConfigureReader.instance().accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!ConfigureReader.instance().authorized(account, AccountAuth.SEND_FILES,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }
                if (fsm.queryBySenderAndReceiver(key).parallelStream()
                        .filter(e -> e.getFileType().equals(FileSendType.FOLDER.getName()))
                        .anyMatch((e) -> e.getFileName().equals(folder.getFolderName()))) {
                    repeFolders.add(folder);
                } else {
                    needSendFoldersCount++;
                }
            }

            if (repeNodes.size() > 0 || repeFolders.size() > 0) {
                Map<String, List<? extends Object>> repeMap = new HashMap<>();
                repeMap.put("repeFolders", repeFolders);
                repeMap.put("repeNodes", repeNodes);
                return "duplicationFileName:" + gson.toJson(repeMap);
            }
            return "confirmSendFiles";

        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }

    private String copySendFolderHelp(FileSend fs, String newFolderParent) {
        String receiver = fs.getFileReceiver();
        List<String> ipPidList = fsm.queryFileSendTree(receiver);
        Folder folder = flm.queryById(fs.getFileId());
        String account = folder.getFolderCreator();
        ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();
        ipPidList.stream().parallel().forEach(e -> {
            String[] idPid = e.split(",");
            String id = idPid[0];
            String pid = idPid[1];
            data.put(id, pid);
        });
        NodeTreeUtil.TagTreeNode rootNode = NodeTreeUtil.TreeBuilder.createOneTree(data, fs.getId());

        folder.setFolderParent(newFolderParent);
        folder.setFolderCreator(receiver);
        String newFolderId = UUID.randomUUID().toString();
        folder.setFolderId(newFolderId);
        flm.insertNewFolder(folder);
        Pair<String,NodeTreeUtil.TagTreeNode> rootPair = new Pair<>(newFolderId,rootNode);

        Queue<Pair<String,NodeTreeUtil.TagTreeNode>> queue = new LinkedList<>();
        queue.add(rootPair);
        while (!queue.isEmpty()) {
            Pair<String,NodeTreeUtil.TagTreeNode> pair = queue.poll();
            Enumeration<NodeTreeUtil.TagTreeNode> childs = pair.getValue().children();
            while (childs.hasMoreElements()) {
                NodeTreeUtil.TagTreeNode child = childs.nextElement();
                Pair<String,NodeTreeUtil.TagTreeNode> childPair;
                NodeTreeUtil.Tag childTag = (NodeTreeUtil.Tag) child.getUserObject();
                if (!child.isLeaf()) {
                    String childId = childTag.getId();
                    FileSend childFileSend = fsm.queryById(childId);
                    String childFolderId = childFileSend.getFileId();
                    Folder childFolder = flm.queryById(childFolderId);
                    String newChildFolderId = UUID.randomUUID().toString();
                    childFolder.setFolderId(newChildFolderId);
                    childFolder.setFolderCreator(receiver);
                    childFolder.setFolderParent(pair.getKey());
                    flm.insertNewFolder(childFolder);
                    childPair = new Pair<>(newChildFolderId, child);
                } else {
                    String childId = childTag.getId();
                    FileSend childFileSend = fsm.queryById(childId);
                    if (childFileSend.getFileType().equals(FileSendType.FILE.getName())) {
                        Node childFile = fm.queryById(childFileSend.getFileId());
                        String newChildFileId = UUID.randomUUID().toString();
                        childFile.setFileId(newChildFileId);
                        childFile.setFileCreator(receiver);
                        childFile.setFileParentFolder(pair.getKey());
                        fm.insert(childFile);
                         childPair = new Pair<>(newChildFileId, child);
                    } else {
                        String childFolderId = childFileSend.getFileId();
                        Folder childFolder = flm.queryById(childFolderId);
                        String newChildFolderId = UUID.randomUUID().toString();
                        childFolder.setFolderId(newChildFolderId);
                        childFolder.setFolderCreator(account);
                        childFolder.setFolderParent(pair.getKey());
                        flm.insertNewFolder(childFolder);
                        childPair = new Pair<>(newChildFolderId, child);
                    }
                }
                queue.add(childPair);
            }
        }
        return newFolderId;
    }


    @Override
    public String doSendFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String fileReceiver = request.getParameter("fileReceiver");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Map<String, Object> key = new HashMap<>();
        key.put("pid", "receive");
        key.put("fileReceiver", fileReceiver);
        key.put("offset", 0L);
        key.put("rows", Integer.MAX_VALUE);

        try {
            final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
            }.getType());
            for (final String id : idList) {
                if (id == null || id.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                // 包含在自身文件空间里面和收到文件空间两种情况
                Node node;
                FileSend fileSend = this.fsm.queryById(id);
                if(fileSend != null){
                    node = this.fm.queryById(fileSend.getFileId());
                }else{
                    node = this.fm.queryById(id);
                }
                if (node == null) {
                    return ERROR_PARAMETER;
                }
                if (!accessAuthUtil.accessSendFile(node, account)) {
                    return NO_AUTHORIZED;
                }
                if (!accessAuthUtil.authorized(account, AccountAuth.SEND_FILES,
                        fu.getAllFoldersId(node.getFileParentFolder()))) {
                    return NO_AUTHORIZED;
                }

                FileSend fs = new FileSend();
                fs.setId(UUID.randomUUID().toString());
                fs.setPid("receive");
                fs.setFileId(id);
                fs.setFileName(node.getFileName());
                fs.setFileParent("receive");
                fs.setFileSendDate(ServerTimeUtil.accurateToSecond());
                fs.setFileSender(account);
                fs.setFileReceiver(fileReceiver);
                fs.setFileSendState(FileSendState.ON_SENDER_AND_RECEIVER.getName());
                fs.setFileType(FileSendType.FILE.getName());
                Stream<FileSend> fileSends = fsm.queryByReceiver(key).parallelStream()
                        .filter(e -> e.getFileType().equals(FileSendType.FILE.getName()));
                if (fileSends.anyMatch(e -> e.getFileName().equals(node.getFileName()))) {
                    // 文件名重复的处理
                    fs.setFileName(FileNodeUtil.getNewReceiveFileName(fs.getFileName(),fileSends));
                }
                fsm.insert(fs);
                this.lu.writeSendFileEvent(request, fs);
            }
            final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
            }.getType());
            for (final String fid : fidList) {
                if (fid == null || fid.length() <= 0) {
                    return ERROR_PARAMETER;
                }
                Folder folder;
                FileSend folderSend = this.fsm.queryById(fid);
                if (folderSend != null){
                    folder = this.flm.queryById(folderSend.getFileId());
                }else{
                    folder = this.flm.queryById(fid);
                }

                if (folder == null) {
                    return ERROR_PARAMETER;
                }
                if (!accessAuthUtil.accessFolder(folder, account)) {
                    return NO_AUTHORIZED;
                }
                if (!accessAuthUtil.authorized(account, AccountAuth.SEND_FILES,
                        fu.getAllFoldersId(folder.getFolderParent()))) {
                    return NO_AUTHORIZED;
                }

                FileSend fs = new FileSend();
                fs.setId(UUID.randomUUID().toString());
                fs.setPid("receive");
                fs.setFileId(fid);
                fs.setFileName(folder.getFolderName());
                fs.setFileParent("receive");
                fs.setFileSendDate(ServerTimeUtil.accurateToSecond());
                fs.setFileSender(account);
                fs.setFileReceiver(fileReceiver);
                fs.setFileSendState(FileSendState.ON_SENDER_AND_RECEIVER.getName());
                fs.setFileType(FileSendType.FOLDER.getName());

                List<FileSend> folderSends = fsm.queryByReceiver(key).parallelStream()
                        .filter(e -> e.getFileType().equals(FileSendType.FOLDER.getName())).collect(Collectors.toList());

                if (folderSends.stream().anyMatch((e) -> e.getFileName().equals(folder.getFolderName()))) {
                    // 文件夹名重复的处理
                    fs.setFileName(FileNodeUtil.getNewReceiveFolderName(fs.getFileName(),folderSends));
                }
                doSendFolderHelp(fs,folder.getFolderCreator());
                this.lu.writeSendFolderEvent(request, fs);
            }
            if (fidList.size() > 0) {
                ServerInitListener.needCheck = true;
            }
            return "sendFilesSuccess";
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
    }

    private void doSendFolderHelp(FileSend folder,String owner) {
        String receiver = folder.getFileReceiver();
        List<String> ipPidList = fm.queryNodeTree(owner);
        ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();
        ipPidList.stream().parallel().forEach(e -> {
            String[] idPid = e.split(",");
            String id = idPid[0];
            String pid = idPid[1];
            data.put(id, pid);
        });
        // 以传入的folder为树的根节点
        NodeTreeUtil.TagTreeNode rootNode = NodeTreeUtil.TreeBuilder.createOneTree(data, folder.getFileId());
        Pair<String,NodeTreeUtil.TagTreeNode> rootPair = new Pair<>(folder.getId(),rootNode);
        fsm.insert(folder);

        //Queue<NodeTreeUtil.TagTreeNode> queue = new LinkedList<>();
        Queue<Pair<String,NodeTreeUtil.TagTreeNode>> queue = new LinkedList<>();
        queue.add(rootPair);

        FileSend fs = new FileSend();
        fs.setFileSender(folder.getFileSender());
        fs.setFileReceiver(receiver);
        fs.setFileSendDate(folder.getFileSendDate());
        fs.setFileSendState(FileSendState.ON_SENDER_AND_RECEIVER.getName());
        while (!queue.isEmpty()) {
            // NodeTreeUtil.TagTreeNode node = queue.poll();
            Pair<String,NodeTreeUtil.TagTreeNode> nodePair = queue.poll();
            String pid = nodePair.getKey();
            Enumeration<NodeTreeUtil.TagTreeNode> childs = nodePair.getValue().children();
            while (childs.hasMoreElements()) {
                NodeTreeUtil.TagTreeNode child = childs.nextElement();
                NodeTreeUtil.Tag childTag = (NodeTreeUtil.Tag) child.getUserObject();

                String childTagId = childTag.getId();
                Folder childFolder = flm.queryById(childTagId);
                Node childFile = fm.queryById(childTagId);

                String newChildFileSendId = UUID.randomUUID().toString();
                fs.setId(newChildFileSendId);
                fs.setPid(pid);
                fs.setFileId(childTagId);
                fs.setFileParent(childTag.getPid());
                if (childFolder != null){
                    fs.setFileName(childFolder.getFolderName());
                    fs.setFileType(FileSendType.FOLDER.getName());
                }

                if (childFile != null){
                    fs.setFileName(childFile.getFileName());
                    fs.setFileType(FileSendType.FILE.getName());
                }

                fsm.insert(fs);
                // queue.add(child);
                queue.add(new Pair<>(newChildFileSendId,child));
            }
        }
    }


    // 移动文件前的确认检查（可视作移动的前置操作）
    @Override
    public String confirmMoveFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        int needMovefilesCount = 0;
        int needMoveFoldersCount = 0;
        if (ConfigureReader.instance().accessFolder(targetFolder, account) && ConfigureReader.instance()
                .authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(locationpath))) {
            try {
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                List<Node> repeNodes = new ArrayList<>();
                List<Folder> repeFolders = new ArrayList<>();
                for (final String fileId : idList) {
                    if (fileId == null || fileId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    final Node node = this.fm.queryById(fileId);
                    if (node == null) {
                        return ERROR_PARAMETER;
                    }
                    if (node.getFileParentFolder().equals(locationpath)) {
                        continue;
                    }
                    if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                            fu.getAllFoldersId(node.getFileParentFolder()))) {
                        return NO_AUTHORIZED;
                    }
                    if (fm.queryByParentFolderId(locationpath).parallelStream()
                            .filter(e -> e.getFileCreator().equals(account))
                            .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                        repeNodes.add(node);
                    } else {
                        needMovefilesCount++;
                    }
                }
                for (final String folderId : fidList) {
                    if (folderId == null || folderId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    final Folder folder = this.flm.queryById(folderId);
                    if (folder == null) {
                        return ERROR_PARAMETER;
                    }
                    if (folder.getFolderParent().equals(locationpath)) {
                        continue;
                    }
                    if (!ConfigureReader.instance().accessFolder(folder, account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                            fu.getAllFoldersId(folder.getFolderParent()))) {
                        return NO_AUTHORIZED;
                    }
                    if (folderId.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                            .anyMatch((e) -> e.getFolderId().equals(folder.getFolderId()))) {
                        return "CANT_MOVE_TO_INSIDE:" + folder.getFolderName();
                    }
                    if (flm.queryByParentId(locationpath).parallelStream()
                            .filter(e -> e.getFolderCreator().equals(account))
                            .anyMatch((e) -> e.getFolderName().equals(folder.getFolderName()))) {
                        repeFolders.add(folder);
                    } else {
                        needMoveFoldersCount++;
                    }
                }
                long estimateFilesTotal = fm.countByParentFolderId(locationpath) + needMovefilesCount;
                if (estimateFilesTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFilesTotal < 0) {
                    return FILES_TOTAL_OUT_OF_LIMIT;
                }
                long estimateFoldersTotal = flm.countByParentId(locationpath) + needMoveFoldersCount;
                if (estimateFoldersTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFoldersTotal < 0) {
                    return FOLDERS_TOTAL_OUT_OF_LIMIT;
                }
                if (repeNodes.size() > 0 || repeFolders.size() > 0) {
                    Map<String, List<? extends Object>> repeMap = new HashMap<>();
                    repeMap.put("repeFolders", repeFolders);
                    repeMap.put("repeNodes", repeNodes);
                    return "duplicationFileName:" + gson.toJson(repeMap);
                }
                return "confirmMoveFiles";
            } catch (Exception e) {
                return ERROR_PARAMETER;
            }
        }
        return NO_AUTHORIZED;
    }

    // 还原文件前的确认检查（可视作还原的前置操作）
    @Override
    public String confirmRestoreFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        int needMovefilesCount = 0;
        int needMoveFoldersCount = 0;
        if (ConfigureReader.instance().accessFolder(targetFolder, account) && ConfigureReader.instance()
                .authorized(account, AccountAuth.MOVE_FILES, fu.getAllFoldersId(locationpath))) {
            try {
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                List<Node> repeNodes = new ArrayList<>();
                List<Folder> repeFolders = new ArrayList<>();
                for (final String fileId : idList) {
                    if (fileId == null || fileId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    final Node node = this.fm.queryById(fileId);
                    if (node == null) {
                        return ERROR_PARAMETER;
                    }
                    if (node.getFileParentFolder().equals(locationpath)) {
                        continue;
                    }
                    if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                            fu.getAllFoldersId(node.getFileParentFolder()))) {
                        return NO_AUTHORIZED;
                    }
                    if (fm.queryByParentFolderId(locationpath).parallelStream()
                            .filter(e -> e.getFileCreator().equals(account))
                            .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                        repeNodes.add(node);
                    } else {
                        needMovefilesCount++;
                    }
                }
                for (final String folderId : fidList) {
                    if (folderId == null || folderId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    final Folder folder = this.flm.queryById(folderId);
                    if (folder == null) {
                        return ERROR_PARAMETER;
                    }
                    if (folder.getFolderParent().equals(locationpath)) {
                        continue;
                    }
                    if (!ConfigureReader.instance().accessFolder(folder, account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!ConfigureReader.instance().authorized(account, AccountAuth.MOVE_FILES,
                            fu.getAllFoldersId(folder.getFolderParent()))) {
                        return NO_AUTHORIZED;
                    }
                    if (folderId.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                            .anyMatch((e) -> e.getFolderId().equals(folder.getFolderId()))) {
                        return "CANT_MOVE_TO_INSIDE:" + folder.getFolderName();
                    }
                    if (flm.queryByParentId(locationpath).parallelStream()
                            .filter(e -> e.getFolderCreator().equals(account))
                            .anyMatch((e) -> e.getFolderName().equals(folder.getFolderName()))) {
                        repeFolders.add(folder);
                    } else {
                        needMoveFoldersCount++;
                    }
                }
                long estimateFilesTotal = fm.countByParentFolderId(locationpath) + needMovefilesCount;
                if (estimateFilesTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFilesTotal < 0) {
                    return FILES_TOTAL_OUT_OF_LIMIT;
                }
                long estimateFoldersTotal = flm.countByParentId(locationpath) + needMoveFoldersCount;
                if (estimateFoldersTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFoldersTotal < 0) {
                    return FOLDERS_TOTAL_OUT_OF_LIMIT;
                }
                if (repeNodes.size() > 0 || repeFolders.size() > 0) {
                    Map<String, List<? extends Object>> repeMap = new HashMap<>();
                    repeMap.put("repeFolders", repeFolders);
                    repeMap.put("repeNodes", repeNodes);
                    return "duplicationFileName:" + gson.toJson(repeMap);
                }
                return "confirmRestoreFiles";
            } catch (Exception e) {
                return ERROR_PARAMETER;
            }
        }
        return NO_AUTHORIZED;
    }

    // 复制文件前的确认检查（可视作复制的前置操作）
    @Override
    public String confirmCopyFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        int needCopyfilesCount = 0;
        int needCopyFoldersCount = 0;
        if (accessAuthUtil.accessFolder(targetFolder, account) && accessAuthUtil
                .authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(locationpath))) {
            try {
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                List<Node> repeNodes = new ArrayList<>();
                List<Folder> repeFolders = new ArrayList<>();
                for (final String fileId : idList) {
                    if (fileId == null || fileId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    Node node = this.fm.queryById(fileId);
                    FileSend fileSend = this.fsm.queryById(fileId);
                    if (fileSend != null){
                        node = this.fm.queryById(fileSend.getFileId());
                    }
                    final Node fNode = node;
                    if (node == null) {
                        return ERROR_PARAMETER;
                    }
                    if (node.getFileParentFolder().equals(locationpath)) {
                        continue;
                    }
                    if (!ConfigureReader.instance().accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!ConfigureReader.instance().authorized(account, AccountAuth.COPY_FILES,
                            fu.getAllFoldersId(node.getFileParentFolder()))) {
                        return NO_AUTHORIZED;
                    }
                    if (fm.queryByParentFolderId(locationpath).parallelStream()
                            .filter(e -> e.getFileCreator().equals(account))
                            .anyMatch((e) -> e.getFileName().equals(fNode.getFileName()))) {
                        repeNodes.add(node);
                    } else {
                        needCopyfilesCount++;
                    }
                }
                for (final String folderId : fidList) {
                    if (folderId == null || folderId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    Folder folder = this.flm.queryById(folderId);
                    FileSend fileSend = this.fsm.queryById(folderId);
                    if (fileSend != null){
                        folder = this.flm.queryById(fileSend.getFileId());
                    }
                    final Folder fFolder = folder;
                    if (folder == null) {
                        return ERROR_PARAMETER;
                    }
                    if (folder.getFolderParent().equals(locationpath)) {
                        continue;
                    }
                    if (!ConfigureReader.instance().accessFolder(folder, account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!ConfigureReader.instance().authorized(account, AccountAuth.COPY_FILES,
                            fu.getAllFoldersId(folder.getFolderParent()))) {
                        return NO_AUTHORIZED;
                    }
                    if (folderId.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                            .anyMatch((e) -> e.getFolderId().equals(fFolder.getFolderId()))) {
                        return "CANT_COPY_TO_INSIDE:" + folder.getFolderName();
                    }
                    if (flm.queryByParentId(locationpath).parallelStream()
                            .filter(e -> e.getFolderCreator().equals(account))
                            .anyMatch((e) -> e.getFolderName().equals(fFolder.getFolderName()))) {
                        repeFolders.add(folder);
                    } else {
                        needCopyFoldersCount++;
                    }
                }
                long estimateFilesTotal = fm.countByParentFolderId(locationpath) + needCopyfilesCount;
                if (estimateFilesTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFilesTotal < 0) {
                    return FILES_TOTAL_OUT_OF_LIMIT;
                }
                long estimateFoldersTotal = flm.countByParentId(locationpath) + needCopyFoldersCount;
                if (estimateFoldersTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFoldersTotal < 0) {
                    return FOLDERS_TOTAL_OUT_OF_LIMIT;
                }
                if (repeNodes.size() > 0 || repeFolders.size() > 0) {
                    Map<String, List<? extends Object>> repeMap = new HashMap<>();
                    repeMap.put("repeFolders", repeFolders);
                    repeMap.put("repeNodes", repeNodes);
                    return "duplicationFileName:" + gson.toJson(repeMap);
                }
                return "confirmCopyFiles";
            } catch (Exception e) {
                return ERROR_PARAMETER;
            }
        }
        return NO_AUTHORIZED;
    }

    // 复制收到文件中的文件前的确认检查（可视作复制的前置操作）
    @Override
    public String confirmCopySendFiles(HttpServletRequest request) {
        final String strIdList = request.getParameter("strIdList");
        final String strFidList = request.getParameter("strFidList");
        final String locationpath = request.getParameter("locationpath");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        Folder targetFolder = flm.queryById(locationpath);
        int needCopyfilesCount = 0;
        int needCopyFoldersCount = 0;
        if (accessAuthUtil.accessFolder(targetFolder, account) && accessAuthUtil
                .authorized(account, AccountAuth.COPY_FILES, fu.getAllFoldersId(locationpath))) {
            try {
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                List<Node> repeNodes = new ArrayList<>();
                List<Folder> repeFolders = new ArrayList<>();
                for (final String fileId : idList) {
                    if (fileId == null || fileId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    FileSend fileSend = this.fsm.queryById(fileId);
                    final Node node = this.fm.queryById(fileSend.getFileId());
                    if (node == null) {
                        return ERROR_PARAMETER;
                    }
                    if (node.getFileParentFolder().equals(locationpath)) {
                        continue;
                    }
                    if (!accessAuthUtil.accessFolder(flm.queryById(node.getFileParentFolder()), account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES,
                            fu.getAllFoldersId(node.getFileParentFolder()))) {
                        return NO_AUTHORIZED;
                    }
                    if (fm.queryByParentFolderId(locationpath).parallelStream()
                            .filter(e -> e.getFileCreator().equals(account))
                            .anyMatch((e) -> e.getFileName().equals(node.getFileName()))) {
                        repeNodes.add(node);
                    } else {
                        needCopyfilesCount++;
                    }
                }
                for (final String folderId : fidList) {
                    if (folderId == null || folderId.length() <= 0) {
                        return ERROR_PARAMETER;
                    }
                    FileSend folderSend = this.fsm.queryById(folderId);
                    final Folder folder = this.flm.queryById(folderSend.getFileId());
                    if (folder == null) {
                        return ERROR_PARAMETER;
                    }
                    if (folder.getFolderParent().equals(locationpath)) {
                        continue;
                    }
                    if (!accessAuthUtil.accessFolder(folder, account)) {
                        return NO_AUTHORIZED;
                    }
                    if (!accessAuthUtil.authorized(account, AccountAuth.COPY_FILES,
                            fu.getAllFoldersId(folder.getFolderParent()))) {
                        return NO_AUTHORIZED;
                    }
                    if (folderId.equals(locationpath) || fu.getParentList(locationpath).parallelStream()
                            .anyMatch((e) -> e.getFolderId().equals(folder.getFolderId()))) {
                        return "CANT_COPY_TO_INSIDE:" + folder.getFolderName();
                    }
                    if (flm.queryByParentId(locationpath).parallelStream()
                            .filter(e -> e.getFolderCreator().equals(account))
                            .anyMatch((e) -> e.getFolderName().equals(folder.getFolderName()))) {
                        repeFolders.add(folder);
                    } else {
                        needCopyFoldersCount++;
                    }
                }
                long estimateFilesTotal = fm.countByParentFolderId(locationpath) + needCopyfilesCount;
                if (estimateFilesTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFilesTotal < 0) {
                    return FILES_TOTAL_OUT_OF_LIMIT;
                }
                long estimateFoldersTotal = flm.countByParentId(locationpath) + needCopyFoldersCount;
                if (estimateFoldersTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimateFoldersTotal < 0) {
                    return FOLDERS_TOTAL_OUT_OF_LIMIT;
                }
                if (repeNodes.size() > 0 || repeFolders.size() > 0) {
                    Map<String, List<? extends Object>> repeMap = new HashMap<>();
                    repeMap.put("repeFolders", repeFolders);
                    repeMap.put("repeNodes", repeNodes);
                    return "duplicationFileName:" + gson.toJson(repeMap);
                }
                return "confirmCopyFiles";
            } catch (Exception e) {
                return ERROR_PARAMETER;
            }
        }
        return NO_AUTHORIZED;
    }


    // 上传文件夹的先行检查
    @Override
    public String checkImportFolder(HttpServletRequest request) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        final String folderId = request.getParameter("folderId");
        final String folderName = request.getParameter("folderName");
        final String maxUploadFileSize = request.getParameter("maxSize");
        CheckImportFolderRespons cifr = new CheckImportFolderRespons();
        // 基本文件夹名称合法性检查
        if (folderName == null || folderName.length() == 0) {
            cifr.setResult(ERROR_PARAMETER);
            return gson.toJson(cifr);
        }
        // 上传目标参数检查
        if (folderId == null || folderId.length() == 0) {
            cifr.setResult(ERROR_PARAMETER);
            return gson.toJson(cifr);
        }
        // 检查上传的目标文件夹是否存在
        Folder folder = flm.queryById(folderId);
        if (folder == null) {
            cifr.setResult(ERROR_PARAMETER);
            return gson.toJson(cifr);
        }
        // 先行权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER,
                fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            cifr.setResult(NO_AUTHORIZED);
            return gson.toJson(cifr);
        }
        // 开始文件上传体积限制检查
        try {
            // 获取最大文件体积（以Byte为单位）
            long mufs = Long.parseLong(maxUploadFileSize);
            long pMaxUploadSize = ConfigureReader.instance().getUploadFileSize(account);
            if (pMaxUploadSize >= 0) {
                if (mufs > pMaxUploadSize) {
                    cifr.setResult("fileOverSize");
                    cifr.setMaxSize(FormatFileSizeUtil.formatSize(ConfigureReader.instance().getUploadFileSize(account)));
                    return gson.toJson(cifr);
                }
            }
        } catch (Exception e) {
            cifr.setResult(ERROR_PARAMETER);
            return gson.toJson(cifr);
        }
        // 开始文件夹命名冲突检查，若无重名则允许上传。否则检查该文件夹是否具备覆盖条件（具备该文件夹的访问权限且具备删除权限），如无则可选择保留两者或取消
        final List<Folder> folders = flm.queryByParentId(folderId);
        try {
            Folder testFolder = folders.stream().parallel()
                    .filter(e -> e.getFolderCreator().equals(account))
                    .filter((n) -> n.getFolderName().equals(
                            new String(folderName.getBytes(Charset.forName("UTF-8")), Charset.forName("UTF-8"))))
                    .findAny().get();
            if (ConfigureReader.instance().accessFolder(testFolder, account) && ConfigureReader.instance()
                    .authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER, fu.getAllFoldersId(folderId))) {
                cifr.setResult("repeatFolder_coverOrBoth");
            } else {
                cifr.setResult("repeatFolder_Both");
            }
            return gson.toJson(cifr);
        } catch (NoSuchElementException e) {
            if (flm.countByParentId(folderId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
                // 检查目标文件夹内的文件夹数目是否超限
                cifr.setResult(FOLDERS_TOTAL_OUT_OF_LIMIT);
            } else {
                // 通过所有检查，允许上传
                cifr.setResult("permitUpload");
            }
            return gson.toJson(cifr);
        }
    }

    @Override
    public String pretendImportFolder(final HttpServletRequest request) {
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        String folderId = request.getParameter("folderId");
        String newFolderName = request.getParameter("newFolderName");
        final String originalFileName = request.getParameter("originalFileName");
        final String fileMd5 = request.getParameter("fileMd5");
        final Long fileSize = Long.valueOf(request.getParameter("fileSize"));
        String folderConstraint = request.getParameter("folderConstraint");
        // 再次检查上传文件名与目标目录ID
        if (folderId == null || folderId.length() <= 0 || originalFileName == null || originalFileName.length() <= 0) {
            return UPLOADERROR;
        }
        // 检查上传的目标文件夹是否存在
        Folder folder = flm.queryById(folderId);
        if (folder == null) {
            return UPLOADERROR;
        }
        // 检查上传权限
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER,
                fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            return UPLOADERROR;
        }
        // 检查上传文件体积是否超限
        long mufs = ConfigureReader.instance().getUploadFileSize(account);
        if (mufs >= 0 && fileSize > mufs) {
            return UPLOADERROR;
        }
        // 检查是否具备创建文件夹权限，若有则使用请求中提供的文件夹访问级别，否则使用默认访问级别
        int pc = folder.getFolderConstraint();
        if (folderConstraint != null) {
            try {
                int ifc = Integer.parseInt(folderConstraint);
                if (ifc != 0 && account == null) {
                    return UPLOADERROR;
                }
                if (ifc < pc) {
                    return UPLOADERROR;
                }
            } catch (Exception e) {
                return UPLOADERROR;
            }
        } else {
            return UPLOADERROR;
        }
        // 计算相对路径的文件夹ID（即真正要保存的文件夹目标）
        String[] paths = getParentPath(originalFileName);
        // 检查上传路径是否正确（必须包含至少一层文件夹）
        if (paths.length == 0) {
            return UPLOADERROR;
        }
        // 若声明了替代文件夹名称，则使用替代文件夹名称作为最上级文件夹名称
        if (newFolderName != null && newFolderName.length() > 0) {
            paths[0] = newFolderName;
        }
        // 执行创建文件夹和上传文件操作
        for (String pName : paths) {
            Folder newFolder;
            try {
                newFolder = fu.createNewFolder(folderId, account, pName, folderConstraint);
            } catch (FoldersTotalOutOfLimitException e1) {
                return FOLDERS_TOTAL_OUT_OF_LIMIT;
            }
            if (newFolder == null) {
                Map<String, String> key = new HashMap<String, String>();
                key.put("parentId", folderId);
                key.put("folderName", pName);
                try {
                    Folder target = flm.queryByParentIdAndFolderName(key).parallelStream()
                            .filter(e -> e.getFolderCreator().equals(account)).findFirst().get();
                    if (target != null) {
                        folderId = target.getFolderId();// 向下迭代直至将父路径全部迭代完毕并找到最终路径
                    } else {
                        return UPLOADERROR;
                    }
                } catch (MyBatisSystemException e) {
                    return UPLOADERROR;
                }
            } else {
                if (fu.hasRepeatFolder(newFolder, account)) {
                    return UPLOADERROR;
                }
                folderId = newFolder.getFolderId();
            }
        }
        String fileName = getFileNameFormPath(originalFileName);
        // 检查是否存在同名文件。存在则直接失败（确保上传的文件夹内容的原始性）
        final List<Node> files = this.fm.queryByParentFolderId(folderId);
        if (files.parallelStream()
                .filter(e -> e.getFileCreator().equals(account))
                .anyMatch((e) -> e.getFileName().equals(fileName))) {
            return UPLOADERROR;
        }
        // 判断上传数目是否超过限额
        if (fm.countByParentFolderId(folderId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
            return FILES_TOTAL_OUT_OF_LIMIT;
        }

        // 将文件存入节点并获取其存入生成路径，型如“UUID.block”形式。

        final Node f2 = new Node();
        f2.setFileId(UUID.randomUUID().toString());
        if (account != null) {
            f2.setFileCreator(account);
        } else {
            f2.setFileCreator("\u533f\u540d\u7528\u6237");
        }
        f2.setFileCreationDate(ServerTimeUtil.accurateToSecond());
        f2.setFileName(fileName);
        f2.setFileParentFolder(folderId);
        Node node = fm.queryByFileMd5(fileMd5).get(0);
        f2.setFilePath(node.getFilePath());
        f2.setFileLength(node.getFileLength());
        f2.setFileMd5(node.getFileMd5());
        f2.setDelFlag(FileDelFlag.FALSE.getName());
        f2.setFileSize(node.getFileSize());
        int i = 0;
        // 尽可能避免UUID重复的情况发生，重试10次
        while (true) {
            try {
                if (this.fm.insert(f2) > 0) {
                    if (hasRepeatNode(f2)) {
                        return UPLOADERROR;
                    } else {
                        this.lu.writeUploadFileEvent(request, f2, account);
                        return UPLOADSUCCESS;
                    }
                }
                break;
            } catch (Exception e) {
                f2.setFileId(UUID.randomUUID().toString());
                i++;
            }
            if (i >= 10) {
                break;
            }
        }
        return UPLOADERROR;
    }

    @Override
    public String doImportFolder(HttpServletRequest request, MultipartFile file) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        String folderId = request.getParameter("folderId");
        final String originalFileName = new String(file.getOriginalFilename().getBytes(Charset.forName("UTF-8")),
                Charset.forName("UTF-8"));
        String folderConstraint = request.getParameter("folderConstraint");
        String newFolderName = request.getParameter("newFolderName");
        // 再次检查上传文件名与目标目录ID
        if (folderId == null || folderId.length() <= 0 || originalFileName == null || originalFileName.length() <= 0) {
            return UPLOADERROR;
        }
        // 检查上传的目标文件夹是否存在
        Folder folder = flm.queryById(folderId);
        if (folder == null) {
            return UPLOADERROR;
        }
        // 检查上传权限
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER,
                fu.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            return UPLOADERROR;
        }
        // 检查上传文件体积是否超限
        long mufs = ConfigureReader.instance().getUploadFileSize(account);
        if (mufs >= 0 && file.getSize() > mufs) {
            return UPLOADERROR;
        }
        // 检查是否具备创建文件夹权限，若有则使用请求中提供的文件夹访问级别，否则使用默认访问级别
        int pc = folder.getFolderConstraint();
        if (folderConstraint != null) {
            try {
                int ifc = Integer.parseInt(folderConstraint);
                if (ifc != 0 && account == null) {
                    return UPLOADERROR;
                }
                if (ifc < pc) {
                    return UPLOADERROR;
                }
            } catch (Exception e) {
                return UPLOADERROR;
            }
        } else {
            return UPLOADERROR;
        }
        // 计算相对路径的文件夹ID（即真正要保存的文件夹目标）
        String[] paths = getParentPath(originalFileName);
        // 检查上传路径是否正确（必须包含至少一层文件夹）
        if (paths.length == 0) {
            return UPLOADERROR;
        }
        // 若声明了替代文件夹名称，则使用替代文件夹名称作为最上级文件夹名称
        if (newFolderName != null && newFolderName.length() > 0) {
            paths[0] = newFolderName;
        }
        // 执行创建文件夹和上传文件操作
        for (String pName : paths) {
            Folder newFolder;
            try {
                newFolder = fu.createNewFolder(folderId, account, pName, folderConstraint);
            } catch (FoldersTotalOutOfLimitException e1) {
                return FOLDERS_TOTAL_OUT_OF_LIMIT;
            }
            if (newFolder == null) {
                Map<String, String> key = new HashMap<String, String>();
                key.put("parentId", folderId);
                key.put("folderName", pName);
                try {
                    Folder target = flm.queryByParentIdAndFolderName(key).parallelStream()
                            .filter(e -> e.getFolderCreator().equals(account)).findFirst().get();
                    if (target != null) {
                        folderId = target.getFolderId();// 向下迭代直至将父路径全部迭代完毕并找到最终路径
                    } else {
                        return UPLOADERROR;
                    }
                } catch (MyBatisSystemException e) {
                    return UPLOADERROR;
                }
            } else {
                if (fu.hasRepeatFolder(newFolder, account)) {
                    return UPLOADERROR;
                }
                folderId = newFolder.getFolderId();
            }
        }
        String fileName = getFileNameFormPath(originalFileName);
        // 检查是否存在同名文件。存在则直接失败（确保上传的文件夹内容的原始性）
        final List<Node> files = this.fm.queryByParentFolderId(folderId);
        if (files.parallelStream()
                .filter(e -> e.getFileCreator().equals(account))
                .anyMatch((e) -> e.getFileName().equals(fileName))) {
            return UPLOADERROR;
        }
        // 判断上传数目是否超过限额
        if (fm.countByParentFolderId(folderId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
            return FILES_TOTAL_OUT_OF_LIMIT;
        }

        // 将文件存入节点并获取其存入生成路径，型如“UUID.block”形式。
        String md5, path;
        try {
            md5 = DigestUtils.md5Hex(file.getInputStream());
        } catch (IOException e) {
            return UPLOADERROR;
        }
        List<Node> sameBlockFiles = fm.queryByFileMd5(md5);
        if (sameBlockFiles == null || sameBlockFiles.size() == 0) {
            path = this.fbu.saveToFileBlocks(file);
            if (path.equals("ERROR")) {
                return UPLOADERROR;
            }
        } else {
            path = sameBlockFiles.get(0).getFilePath();
        }

        final long flength = file.getSize();
        final String fsize = FormatFileSizeUtil.formatSize(flength);
        final Node f2 = new Node();
        f2.setFileId(UUID.randomUUID().toString());
        if (account != null) {
            f2.setFileCreator(account);
        } else {
            f2.setFileCreator("\u533f\u540d\u7528\u6237");
        }
        f2.setFileCreationDate(ServerTimeUtil.accurateToSecond());
        f2.setFileName(fileName);
        f2.setFileParentFolder(folderId);
        f2.setFilePath(path);
        f2.setFileLength(flength);
        f2.setFileMd5(md5);
        f2.setDelFlag(FileDelFlag.FALSE.getName());
        f2.setFileSize(fsize);
        int i = 0;
        // 尽可能避免UUID重复的情况发生，重试10次
        while (true) {
            try {
                if (this.fm.insert(f2) > 0) {
                    if (hasRepeatNode(f2)) {
                        return UPLOADERROR;
                    } else {
                        this.lu.writeUploadFileEvent(request, f2, account);
                        return UPLOADSUCCESS;
                    }
                }
                break;
            } catch (Exception e) {
                f2.setFileId(UUID.randomUUID().toString());
                i++;
            }
            if (i >= 10) {
                break;
            }
        }
        return UPLOADERROR;
    }

    /**
     * <h2>解析相对路径字符串</h2>
     * <p>
     * 根据相对路径获得文件夹的层级名称，并以数组的形式返回。若无层级则返回空数组，若层级名称为空字符串则忽略。
     * </p>
     * <p>
     * 示例1：输入"aaa/bbb/ccc.c"，返回["aaa","bbb"]。
     * </p>
     * <p>
     * 示例2：输入"bbb.c"，返回[]。
     * </p>
     * <p>
     * 示例3：输入"aaa//bbb/ccc.c"，返回["aaa","bbb"]。
     * </p>
     *
     * @param path java.lang.String 原路径字符串
     * @return java.lang.String[] 解析出的目录层级
     * @author ggh@sxw.cn
     */
    private String[] getParentPath(String path) {
        if (path != null) {
            String[] paths = path.split("/");
            List<String> result = new ArrayList<String>();
            for (int i = 0; i < paths.length - 1; i++) {
                if (paths[i].length() > 0) {
                    result.add(paths[i]);
                }
            }
            return result.toArray(new String[0]);
        }
        return new String[0];
    }

    /**
     * <h2>解析相对路径中的文件名</h2>
     * <p>
     * 从相对路径中获得文件名，若解析失败则返回null。
     * </p>
     *
     * @param path 需要解析的相对路径
     * @return java.lang.String 文件名
     * @author ggh@sxw.cn
     */
    private String getFileNameFormPath(String path) {
        if (path != null) {
            String[] paths = path.split("/");
            if (paths.length > 0) {
                return paths[paths.length - 1];
            }
        }
        return null;
    }

    // 检查新增的文件是否存在同名问题
    private boolean hasRepeatNode(Node n) {
        Node[] repeats = fm.queryByParentFolderId(n.getFileParentFolder()).parallelStream()
                .filter((e) -> e.getFileName().equals(n.getFileName())).toArray(Node[]::new);
        if (repeats.length > 1) {
            File f = fbu.getFileFromBlocks(n);
            if (f != null) {
                f.delete();
            }
            fm.deleteById(n.getFileId());
            return true;
        } else {
            return false;
        }
    }
}
