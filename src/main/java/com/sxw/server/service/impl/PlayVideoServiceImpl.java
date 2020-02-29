package com.sxw.server.service.impl;

import com.sxw.printer.Printer;
import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.model.Node;
import com.sxw.server.service.PlayVideoService;
import com.sxw.server.util.*;
import com.sxw.server.service.*;
import org.springframework.stereotype.*;

import com.google.gson.Gson;

import com.sxw.server.mapper.*;

import java.io.File;

import javax.annotation.*;
import javax.servlet.http.*;
import com.sxw.server.model.*;
import com.sxw.server.pojo.VideoInfo;
import com.sxw.server.enumeration.*;
import com.sxw.server.util.*;
import ws.schild.jave.MultimediaObject;

@Service
public class PlayVideoServiceImpl implements PlayVideoService {
	@Resource
	private NodeMapper fm;
	@Resource
	private Gson gson;
	@Resource
	private FileBlockUtil fbu;
	@Resource
	private LogUtil lu;
	@Resource
	private FolderMapper flm;
	@Resource
	private FolderUtil fu;
	@Resource
	private KiftdFFMPEGLocator kfl;

	private VideoInfo foundVideo(final HttpServletRequest request) {
		final String fileId = request.getParameter("fileId");
		if (fileId != null && fileId.length() > 0) {
			final Node f = this.fm.queryById(fileId);
			final VideoInfo vi = new VideoInfo(f);
			if (f != null) {
				final String account = (String) request.getSession().getAttribute("ACCOUNT");
				if (ConfigureReader.instance().authorized(account, AccountAuth.DOWNLOAD_FILES,
						fu.getAllFoldersId(f.getFileParentFolder()))
						&& ConfigureReader.instance().accessFolder(flm.queryById(f.getFileParentFolder()), account)) {
					final String fileName = f.getFileName();
					// 检查视频格式
					final String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
					switch (suffix) {
					case "mp4":
						if(kfl.getFFMPEGExecutablePath() != null) {
							// 对于mp4后缀的视频，进一步检查其编码是否为h264，如果是，则设定无需转码直接播放
							File target = fbu.getFileFromBlocks(f);
							if (target == null || !target.isFile()) {
								return null;
							}
							MultimediaObject mo = new MultimediaObject(target);
							try {
								if (mo.getInfo().getVideo().getDecoder().indexOf("h264") >= 0) {
									vi.setNeedEncode("N");
									return vi;
								}
							} catch (Exception e) {
								Printer.instance.print("错误：视频文件“" + f.getFileName() + "”在解析时出现意外错误。详细信息：" + e.getMessage());
								lu.writeException(e);
							}
							// 对于其他编码格式，则设定需要转码
							vi.setNeedEncode("Y");
						}else {
							vi.setNeedEncode("N");// 如果禁用了ffmpeg，那么怎么都不需要转码
						}
						return vi;
					case "mov":
					case "webm":
					case "avi":
					case "wmv":
					case "mkv":
					case "flv":
						if(kfl.getFFMPEGExecutablePath() != null) {
							vi.setNeedEncode("Y");
						}else {
							vi.setNeedEncode("N");
						}
						return vi;
					default:
						break;
					}
				}
			}
		}
		return null;
	}

	@Override
	public String getPlayVideoJson(final HttpServletRequest request) {
		final VideoInfo v = this.foundVideo(request);
		if (v != null) {
			return gson.toJson((Object) v);
		}
		return "ERROR";
	}
}
