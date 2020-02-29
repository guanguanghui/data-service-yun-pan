package com.sxw.server.pojo;

import com.sxw.server.model.Node;

/**
 * 
 * <h2>一种专门针对视频格式的封装</h2>
 * <p>该封装除了具备node对象的属性外，额外增加了“needEncode”属性，其值为“Y”或“N”，用于代表是否需要进行转码。</p>
 * @author ggh@sxw.cn
 * @version 1.0
 */
public class VideoInfo extends Node {
	
	public VideoInfo(Node n) {
		this.setFileId(n.getFileId());
		this.setFileName(n.getFileName());
		this.setFileParentFolder(n.getFileParentFolder());
		this.setFilePath(n.getFilePath());
		this.setFileLength(n.getFileLength());
		this.setFileSize(n.getFileSize());
		this.setFileCreationDate(n.getFileCreationDate());
		this.setFileCreator(n.getFileCreator());
		this.setFileMd5(n.getFileMd5());
		this.setDelFlag(n.getDelFlag());
	}
	
	private String needEncode;

	public String getNeedEncode() {
		return needEncode;
	}

	public void setNeedEncode(String needEncode) {
		this.needEncode = needEncode;
	}
	
}
