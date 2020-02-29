package com.sxw.server.controller;
import com.sxw.server.service.*;
import com.sxw.server.util.ConfigureReader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.*;
import javax.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import javax.servlet.http.*;

/**
 * 
 * <h2>主控制器</h2>
 * <p>
 * 该控制器用于负责处理sxwpan主页（home.html）的所有请求，具体过程请见各个方法注释。
 * </p>
 * 
 * @author ggh@sxw.cn
 * @version 1.0
 */
@Controller
@RequestMapping({ "/homeController" })
public class HomeController {
	private static final String CHARSET_BY_AJAX = "text/html; charset=utf-8";

	@Resource
	private AccountService as;
	@Resource
	private FolderViewService fvs;
	@Resource
	private FolderService fs;
	@Resource
	private FileService fis;
	@Resource
	private PlayVideoService pvs;
	@Resource
	private ShowPictureService sps;
	@Resource
	private PlayAudioService pas;
	@Resource
	private FileChainService fcs;

	@RequestMapping({ "/getFileOccupancySpace.ajax" })
	@ResponseBody
	public String getFileOccupancySpace(final HttpServletRequest request) {
		return this.fis.getFileOccupancySpace(request);
	}

	@RequestMapping({ "/getFileIsUpLoaded.ajax" })
	@ResponseBody
	public String getFileIsUpLoaded(final HttpServletRequest request) {
		return this.fis.getFileIsUpLoaded(request);
	}

	@RequestMapping(value = { "/getPublicKey.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getPublicKey() {
		return this.as.getPublicKey();
	}

	@RequestMapping({ "/doLogin.ajax" })
	@ResponseBody
	public String doLogin(final HttpServletRequest request, final HttpSession session) {
		return this.as.checkLoginRequest(request, session);
	}

	// 获取一个新验证码并存入请求者的Session中
	@RequestMapping({ "/getNewVerCode.do" })
	public void getNewVerCode(final HttpServletRequest request, final HttpServletResponse response,
			final HttpSession session) {
		as.getNewLoginVerCode(request, response, session);
	}

	// 修改密码
	@RequestMapping(value = { "/doChangePassword.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String doChangePassword(final HttpServletRequest request) {
		return as.changePassword(request);
	}

	@RequestMapping(value = { "/getFolderView.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getFolderView(final String fid, final HttpSession session, final HttpServletRequest request) {
		return fvs.getFolderViewToJson(fid, session, request);
	}

	@RequestMapping(value = { "/getRecycleBinView.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getRecycleBinView(final String fid, final HttpSession session, final HttpServletRequest request) {
		return fvs.getRecycleBinViewToJson(fid, session, request);
	}

	@RequestMapping(value = { "/getReceiveBinView.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getReceiveBinView(final String fid, final HttpSession session, final HttpServletRequest request) {
		return fvs.getReceiveViewToJson(fid, session, request);
	}

	@RequestMapping(value = { "/getRemainingFolderView.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getRemainingFolderView(final HttpServletRequest request) {
		return fvs.getRemainingFolderViewToJson(request);
	}

	@RequestMapping({ "/doLogout.ajax" })
	public String doLogout(final HttpSession session) {
		this.as.logout(session);
		String loginUrl = ConfigureReader.instance().getLoginUrl();
		if(loginUrl == null || StringUtils.isBlank(loginUrl)){
		    return "SUCCESS";
        }else{
            return "redirect:" + ConfigureReader.instance().getLoginUrl();
        }
	}

	@RequestMapping({ "/newFolder.ajax" })
	@ResponseBody
	public String newFolder(final HttpServletRequest request) {
		return this.fs.newFolder(request);
	}

	@RequestMapping({ "/deleteFolder.ajax" })
	@ResponseBody
	public String deleteFolder(final HttpServletRequest request) {
		return this.fs.deleteFolder(request);
	}

	@RequestMapping({ "/reallyDeleteFolder.ajax" })
	@ResponseBody
	public String reallyDeleteFolder(final HttpServletRequest request) {
		return this.fs.reallyDeleteFolder(request);
	}

	@RequestMapping({ "/renameFolder.ajax" })
	@ResponseBody
	public String renameFolder(final HttpServletRequest request) {
		return this.fs.renameFolder(request);
	}

	@RequestMapping(value = { "/douploadFile.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String douploadFile(final HttpServletRequest request, final HttpServletResponse response,
			final MultipartFile file) {
		return this.fis.doUploadFile(request, response, file);
	}

	@RequestMapping(value = { "/pretendUploadFile.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String pretendUploadFile(final HttpServletRequest request, final HttpServletResponse response) {
		return this.fis.pretendUploadFile(request, response);
	}

	@RequestMapping(value = { "/checkUploadFile.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String checkUploadFile(final HttpServletRequest request, final HttpServletResponse response) {
		return this.fis.checkUploadFile(request, response);
	}

	// 上传文件夹的前置检查流程
	@RequestMapping(value = { "/checkImportFolder.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String checkImportFolder(final HttpServletRequest request) {
		return this.fis.checkImportFolder(request);
	}



	// 执行文件夹上传操作
	@RequestMapping(value = { "/pretendImportFolder.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String pretendImportFolder(final HttpServletRequest request) {
		return fis.pretendImportFolder(request);
	}

	// 执行文件夹上传操作
	@RequestMapping(value = { "/doImportFolder.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String doImportFolder(final HttpServletRequest request, final MultipartFile file) {
		return fis.doImportFolder(request, file);
	}

	// 上传文件夹时，若存在同名文件夹并选择覆盖，则应先执行该方法，执行成功后再上传新的文件夹
	@RequestMapping(value = { "/deleteFolderByName.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String deleteFolderByName(final HttpServletRequest request) {
		return fs.deleteFolderByName(request);
	}

	// 上传文件夹时，若存在同名文件夹并选择保留两者，则应先执行该方法，执行成功后使用返回的新文件夹名进行上传
	@RequestMapping(value = { "/createNewFolderByName.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String createNewFolderByName(final HttpServletRequest request) {
		return fs.createNewFolderByName(request);
	}

	@RequestMapping({ "/deleteFile.ajax" })
	@ResponseBody
	public String deleteFile(final HttpServletRequest request) {
		return this.fis.deleteFile(request);
	}

	@RequestMapping({ "/reallyDeleteFile.ajax" })
	@ResponseBody
	public String reallyDeleteFile(final HttpServletRequest request) {
		return this.fis.reallyDeleteFile(request);
	}


	@RequestMapping({ "/downloadFile.do" })
	public void downloadFile(final HttpServletRequest request, final HttpServletResponse response) {
		this.fis.doDownloadFile(request, response);
	}

	@RequestMapping({ "/renameFile.ajax" })
	@ResponseBody
	public String renameFile(final HttpServletRequest request) {
		return this.fis.doRenameFile(request);
	}

	@RequestMapping(value = { "/playVideo.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String playVideo(final HttpServletRequest request, final HttpServletResponse response) {
		return this.pvs.getPlayVideoJson(request);
	}

	/**
	 * 
	 * <h2>预览图片请求</h2>
	 * <p>
	 * 该方法用于处理预览图片请求。配合Viewer.js插件，返回指定格式的JSON数据。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param request
	 *            HttpServletRequest 请求对象
	 * @return String 预览图片的JSON信息
	 */
	@RequestMapping(value = { "/getPrePicture.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getPrePicture(final HttpServletRequest request) {
		return this.sps.getPreviewPictureJson(request);
	}

	/**
	 * 
	 * <h2>获取压缩的预览图片</h2>
	 * <p>
	 * 该方法用于预览较大图片时获取其压缩版本以加快预览速度，该请求会根据预览目标的大小自动决定压缩等级。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param request
	 *            HttpServletRequest 请求对象，其中应包含fileId指定预览图片的文件块ID。
	 * @param response
	 *            HttpServletResponse 响应对象，用于写出压缩后的数据流。
	 */
	@RequestMapping({ "/showCondensedPicture.do" })
	public void showCondensedPicture(final HttpServletRequest request, final HttpServletResponse response) {
		sps.getCondensedPicture(request, response);
	}

	@RequestMapping({ "/deleteCheckedFiles.ajax" })
	@ResponseBody
	public String deleteCheckedFiles(final HttpServletRequest request) {
		return this.fis.deleteCheckedFiles(request);
	}

	@RequestMapping({ "/fakerDeleteCheckedFiles.ajax" })
	@ResponseBody
	public String fakerDeleteCheckedFiles(final HttpServletRequest request) {
		return this.fis.fakerDeleteCheckedFiles(request);
	}

	@RequestMapping({ "/getPackTime.ajax" })
	@ResponseBody
	public String getPackTime(final HttpServletRequest request) {
		return this.fis.getPackTime(request);
	}

	@RequestMapping({ "/downloadCheckedFiles.ajax" })
	@ResponseBody
	public String downloadCheckedFiles(final HttpServletRequest request) {
		return this.fis.downloadCheckedFiles(request);
	}

	@RequestMapping({ "/downloadCheckedFilesZip.do" })
	public void downloadCheckedFilesZip(final HttpServletRequest request, final HttpServletResponse response)
			throws Exception {
		this.fis.downloadCheckedFilesZip(request, response);
	}

	@RequestMapping(value = { "/playAudios.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String playAudios(final HttpServletRequest request) {
		return this.pas.getAudioInfoListByJson(request);
	}

	@RequestMapping(value = { "/confirmMoveFiles.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String confirmMoveFiles(final HttpServletRequest request) {
		return fis.confirmMoveFiles(request);
	}

	@RequestMapping(value = { "/confirmRestoreFiles.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String confirmRestoreFiles(final HttpServletRequest request) {
		return fis.confirmRestoreFiles(request);
	}

	@RequestMapping({ "/restoreCheckedFiles.ajax" })
	@ResponseBody
	public String restoreCheckedFiles(final HttpServletRequest request) {
		return fis.doRestoreFiles(request);
	}

	@RequestMapping({ "/moveCheckedFiles.ajax" })
	@ResponseBody
	public String moveCheckedFiles(final HttpServletRequest request) {
		return fis.doMoveFiles(request);
	}

	@RequestMapping(value = { "/confirmCopyFiles.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String confirmCopyFiles(final HttpServletRequest request) {
		return fis.confirmCopyFiles(request);
	}

	@RequestMapping({ "/copyCheckedFiles.ajax" })
	@ResponseBody
	public String copyCheckedFiles(final HttpServletRequest request) {
		return fis.doCopyFiles(request);
	}

	@RequestMapping(value = {"/confirmSendFiles.ajax"}, produces = { CHARSET_BY_AJAX })
	public String confirmSendFiles(final HttpServletRequest request){
		return fis.confirmSendFiles(request);
	}

	@RequestMapping({ "/sendFiles.ajax" })
	@ResponseBody
	public String sendCheckedFiles(final HttpServletRequest request) {
		return fis.doSendFiles(request);
	}


	@RequestMapping(value = { "/sreachInCompletePath.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String sreachInCompletePath(final HttpServletRequest request) {
		return fvs.getSreachViewToJson(request);
	}

	/**
	 * 
	 * <h2>应答机制</h2>
	 * <p>
	 * 该机制旨在防止某些长耗时操作可能导致Session失效的问题（例如上传、视频播放等），方便用户持续操作。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @return String “pong”或“”
	 */
	@RequestMapping(value = { "/ping.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String pong(final HttpServletRequest request) {
		return as.doPong(request);
	}

	// 询问是否开启自由注册新账户功能
	@RequestMapping(value = { "/askForAllowSignUpOrNot.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String askForAllowSignUpOrNot(final HttpServletRequest request) {
		return as.isAllowSignUp();
	}

	// 处理注册新账户请求
	@RequestMapping(value = { "/doSigUp.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String doSigUp(final HttpServletRequest request) {
		return as.doSignUp(request);
	}

	// 获得发送文件时的接收人员列表
	@RequestMapping(value = { "/getFilesReceivers.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getFilesReceivers(final HttpServletRequest request){
		return as.getGoodFriends(request);
	}

	// 获取永久资源链接的对应ckey
	@RequestMapping(value = { "/getFileChainKey.ajax" }, produces = { CHARSET_BY_AJAX })
	@ResponseBody
	public String getFileChainKey(final HttpServletRequest request) {
		return fcs.getChainKeyByFid(request);
	}
}
