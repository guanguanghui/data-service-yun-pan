package com.sxw.server.pojo;

import java.util.*;

import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
/**
 * 
 * <h2>文件夹视图封装POJO</h2>
 * <p>
 * 该POJO用于封装文件夹视图数据，方便转化为标准JSON格式并发送至前端，具体内容请见该类中的get和set方法。
 * 文件夹视图是主页上最基础的数据封装类型。
 * </p>
 * @author ggh@sxw.cn
 * @version 1.0
 */
// @ApiModel(description = "文件夹响应视图")
public class FolderView {
	// @ApiModelProperty("文件夹")
	private Folder folder;
	// @ApiModelProperty("文件夹的绝对路径")
	private List<Folder> parentList;
	// @ApiModelProperty("子文件夹列表")
	private List<Folder> folderList;
	// @ApiModelProperty("子文件列表")
	private List<Node> fileList;
	// @ApiModelProperty("用户名")
	private String account;
	// @ApiModelProperty("用户权限列表")
	private List<String> authList;
	// @ApiModelProperty("最后上传时间")
	private String publishTime;
	// @ApiModelProperty("是否允许修改密码")
	private String allowChangePassword;
	// @ApiModelProperty("是否显示文件分享链接")
	private String showFileChain;
	// @ApiModelProperty("是否允许注册")
	private String allowSignUp;
	// @ApiModelProperty("是否允许打包")
	private boolean enableDownloadZip;
	// @ApiModelProperty("是否允许视频解码")
	private boolean enableFFMPEG;
	// @ApiModelProperty("文件夹列表查询偏移量")
	private long foldersOffset;// 文件夹列表查询偏移量
	// @ApiModelProperty("文件列表查询偏移量")
	private long filesOffset;// 文件列表查询偏移量
	// @ApiModelProperty("查询步长")
	private int selectStep;// 查询步长

	public Folder getFolder() {
		return this.folder;
	}

	public void setFolder(final Folder folder) {
		this.folder = folder;
	}

	public List<Folder> getParentList() {
		return this.parentList;
	}

	public void setParentList(final List<Folder> parentList) {
		this.parentList = parentList;
	}

	public List<Folder> getFolderList() {
		return this.folderList;
	}

	public void setFolderList(final List<Folder> folderList) {
		this.folderList = folderList;
	}

	public List<Node> getFileList() {
		return this.fileList;
	}

	public void setFileList(final List<Node> fileList) {
		this.fileList = fileList;
	}

	public List<String> getAuthList() {
		return this.authList;
	}

	public void setAuthList(final List<String> authList) {
		this.authList = authList;
	}

	public String getAccount() {
		return this.account;
	}

	public void setAccount(final String account) {
		this.account = account;
	}

	public String getPublishTime() {
		return this.publishTime;
	}

	public void setPublishTime(final String publishTime) {
		this.publishTime = publishTime;
	}

	public String getAllowChangePassword() {
		return allowChangePassword;
	}

	public void setAllowChangePassword(String allowChangePassword) {
		this.allowChangePassword = allowChangePassword;
	}

	public String getShowFileChain() {
		return showFileChain;
	}

	public void setShowFileChain(String showFileChain) {
		this.showFileChain = showFileChain;
	}

	public String getAllowSignUp() {
		return allowSignUp;
	}

	public void setAllowSignUp(String allowSignUp) {
		this.allowSignUp = allowSignUp;
	}

	public long getFoldersOffset() {
		return foldersOffset;
	}

	public void setFoldersOffset(long foldersOffset) {
		this.foldersOffset = foldersOffset;
	}

	public long getFilesOffset() {
		return filesOffset;
	}

	public void setFilesOffset(long filesOffset) {
		this.filesOffset = filesOffset;
	}

	public int getSelectStep() {
		return selectStep;
	}

	public void setSelectStep(int selectStep) {
		this.selectStep = selectStep;
	}

	public boolean isEnableDownloadZip() {
		return enableDownloadZip;
	}

	public void setEnableDownloadZip(boolean enableDownloadZip) {
		this.enableDownloadZip = enableDownloadZip;
	}

	public boolean isEnableFFMPEG() {
		return enableFFMPEG;
	}

	public void setEnableFFMPEG(boolean enableFFMPEG) {
		this.enableFFMPEG = enableFFMPEG;
	}

}
