package com.sxw.server.service.impl;

import com.google.gson.reflect.TypeToken;
import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.listener.ServerInitListener;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.model.Folder;
import com.sxw.server.service.FolderService;
import com.sxw.server.util.*;
import com.sxw.server.service.*;
import org.springframework.stereotype.*;

import com.google.gson.Gson;

import com.sxw.server.mapper.*;
import javax.annotation.*;
import javax.servlet.http.*;
import com.sxw.server.enumeration.*;
import com.sxw.server.model.*;
import com.sxw.server.pojo.CreateNewFolderByNameRespons;
import com.sxw.server.util.*;

import java.nio.charset.Charset;
import java.util.*;

@Service
public class FolderServiceImpl implements FolderService {

	@Resource
	private FolderMapper fm;
	@Resource
	private NodeMapper nm;
	@Resource
	private FolderUtil fu;
	@Resource
	private LogUtil lu;
	@Resource
	private Gson gson;

	public String newFolder(final HttpServletRequest request) {
		final String parentId = request.getParameter("parentId");
		final String folderName = request.getParameter("folderName");
		final String folderConstraint = request.getParameter("folderConstraint");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		if (parentId == null || folderName == null || parentId.length() <= 0 || folderName.length() <= 0) {
			return "errorParameter";
		}
		if (!TextFormateUtil.instance().matcherFolderName(folderName) || folderName.indexOf(".") == 0) {
			return "errorParameter";
		}
		final Folder parentFolder = this.fm.queryById(parentId);
		if (parentFolder == null || !ConfigureReader.instance().accessFolder(parentFolder, account)) {
			return "errorParameter";
		}
		if (!ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER,
				fu.getAllFoldersId(parentId))) {
			return "noAuthorized";
		}
		if (fm.queryByParentId(parentId).parallelStream()
				.filter(e -> e.getFolderCreator().equals(account))
				.anyMatch((e) -> e.getFolderName().equals(folderName))) {
			return "nameOccupied";
		}
		if (fm.countByParentId(parentId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
			return "foldersTotalOutOfLimit";
		}
		Folder f = new Folder();
		// 设置子文件夹约束等级，不允许子文件夹的约束等级比父文件夹低
		int pc = parentFolder.getFolderConstraint();
		if (folderConstraint != null) {
			try {
				int ifc = Integer.parseInt(folderConstraint);
				if (ifc != 0 && account == null) {
					return "errorParameter";
				}
				if (ifc < pc) {
					return "errorParameter";
				} else {
					f.setFolderConstraint(ifc);
				}
			} catch (Exception e) {
				return "errorParameter";
			}
		} else {
			return "errorParameter";
		}
		f.setFolderId(UUID.randomUUID().toString());
		f.setFolderName(folderName);
		f.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
		f.setDelFlag(FileDelFlag.FALSE.getName());
		if (account != null) {
			f.setFolderCreator(account);
		} else {
			f.setFolderCreator("匿名用户");
		}
		f.setFolderParent(parentId);
		int i = 0;
		while (true) {
			try {
				final int r = this.fm.insertNewFolder(f);
				if (r > 0) {
					if (fu.hasRepeatFolder(f, account)) {
						return "cannotCreateFolder";
					} else {
						this.lu.writeCreateFolderEvent(request, f);
						return "createFolderSuccess";
					}
				}
				break;
			} catch (Exception e) {
				f.setFolderId(UUID.randomUUID().toString());
				i++;
			}
			if (i >= 10) {
				break;
			}
		}
		return "cannotCreateFolder";
	}

	// 彻底删除文件夹的实现方法
	public String reallyDeleteFolder(final HttpServletRequest request) {
		final String folderId = request.getParameter("folderId");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		// 检查删除目标的ID参数是否正确
		if (folderId == null || folderId.length() == 0 || "root".equals(folderId)) {
			return "errorParameter";
		}
		final Folder folder = this.fm.queryById(folderId);
		if (folder == null) {
			return "deleteFolderSuccess";
		}
		// 检查删除者是否具备删除目标的访问许可
		if (!ConfigureReader.instance().accessFolder(folder, account)) {
			return "noAuthorized";
		}
		// 检查权限
		if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
				fu.getAllFoldersId(folder.getFolderParent()))) {
			return "noAuthorized";
		}
		// 执行迭代删除
		final List<Folder> l = this.fu.getParentList(folderId);
		if (this.fu.deleteAllChildFolder(folderId) > 0) {
			this.lu.writeDeleteFolderEvent(request, folder, l);
			ServerInitListener.needCheck = true;
			return "deleteFolderSuccess";
		}
		return "cannotDeleteFolder";
	}


	// 删除文件夹(放入到回收站)的实现方法
	public String deleteFolder(final HttpServletRequest request) {
		final String folderId = request.getParameter("folderId");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		// 检查删除目标的ID参数是否正确
		if (folderId == null || folderId.length() == 0 || "root".equals(folderId)) {
			return "errorParameter";
		}
		final Folder folder = this.fm.queryById(folderId);
		if (folder == null) {
			return "deleteFolderSuccess";
		}
		// 检查删除者是否具备删除目标的访问许可
		if (!ConfigureReader.instance().accessFolder(folder, account)) {
			return "noAuthorized";
		}
		// 检查权限
		if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
				fu.getAllFoldersId(folder.getFolderParent()))) {
			return "noAuthorized";
		}

		// 执行迭代删除
		final List<Folder> l = this.fu.getParentList(folderId);
		if (this.fu.fakeDeleteAllChildFolder(folderId) > 0) {
			this.lu.writeDeleteFolderEvent(request, folder, l);
			ServerInitListener.needCheck = true;
			return "deleteFolderSuccess";
		}
		return "cannotDeleteFolder";
	}

	// 对编辑文件夹的实现
	public String renameFolder(final HttpServletRequest request) {
		final String folderId = request.getParameter("folderId");
		final String newName = request.getParameter("newName");
		final String folderConstraint = request.getParameter("folderConstraint");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		if (folderId == null || folderId.length() == 0 || newName == null || newName.length() == 0
				|| "root".equals(folderId)) {
			return "errorParameter";
		}
		if (!TextFormateUtil.instance().matcherFolderName(newName) || newName.indexOf(".") == 0) {
			return "errorParameter";
		}
		final Folder folder = this.fm.queryById(folderId);
		if (folder == null) {
			return "errorParameter";
		}
		if (!ConfigureReader.instance().accessFolder(folder, account)) {
			return "noAuthorized";
		}
		if (!ConfigureReader.instance().authorized(account, AccountAuth.RENAME_FILE_OR_FOLDER,
				fu.getAllFoldersId(folder.getFolderParent()))) {
			return "noAuthorized";
		}
		final Folder parentFolder = this.fm.queryById(folder.getFolderParent());
		int pc = parentFolder.getFolderConstraint();
		if (folderConstraint != null) {
			try {
				int ifc = Integer.parseInt(folderConstraint);
				if (ifc > 0 && account == null) {
					return "errorParameter";
				}
				if (ifc < pc) {
					return "errorParameter";
				} else {
					Map<String, Object> map = new HashMap<>();
					map.put("newConstraint", ifc);
					map.put("folderId", folderId);
					fm.updateFolderConstraintById(map);
					changeChildFolderConstraint(folderId, ifc);
					if (!folder.getFolderName().equals(newName)) {
						if (fm.queryByParentId(parentFolder.getFolderId()).parallelStream()
								.filter(e -> e.getFolderCreator().equals(account))
								.anyMatch((e) -> e.getFolderName().equals(newName))) {
							return "nameOccupied";
						}
						Map<String, String> map2 = new HashMap<String, String>();
						map2.put("folderId", folderId);
						map2.put("newName", newName);
						if (this.fm.updateFolderNameById(map2) == 0) {
							return "errorParameter";
						}
					}
					this.lu.writeRenameFolderEvent(request, folder, newName, folderConstraint);
					return "renameFolderSuccess";
				}
			} catch (Exception e) {
				return "errorParameter";
			}
		} else {
			return "errorParameter";
		}
	}

	/**
	 * 
	 * <h2>迭代修改子文件夹约束</h2>
	 * <p>
	 * 当某一文件夹的约束被修改时，其所有子文件夹的约束等级均不得低于其父文件夹。 例如：
	 * 父文件夹的约束等级改为1（仅小组）时，所有约束等级为0（公开的）的子文件夹的约束等级也会提升为1， 而所有约束等级为2（仅自己）的子文件夹则不会受影响。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param folderId
	 *            要修改的文件夹ID
	 * @param c
	 *            约束等级
	 */
	private void changeChildFolderConstraint(String folderId, int c) {
		List<Folder> cfs = fm.queryByParentId(folderId);
		for (Folder cf : cfs) {
			if (cf.getFolderConstraint() != c) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("newConstraint", c);
				map.put("folderId", cf.getFolderId());
				fm.updateFolderConstraintById(map);
			}
			changeChildFolderConstraint(cf.getFolderId(), c);
		}
	}

	@Override
	public String deleteFolderByName(HttpServletRequest request) {
		final String parentId = request.getParameter("parentId");
		final String folderName = request.getParameter("folderName");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		if (parentId == null || parentId.length() == 0) {
			return "deleteError";
		}
		Folder p = fm.queryById(parentId);
		if (p == null) {
			return "deleteError";
		}
		if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER,
				fu.getAllFoldersId(parentId)) || !ConfigureReader.instance().accessFolder(p, account)) {
			return "deleteError";
		}
		final Folder[] repeatFolders = this.fm.queryByParentId(parentId).parallelStream()
				.filter((f) -> f.getFolderName()
						.equals(new String(folderName.getBytes(Charset.forName("UTF-8")), Charset.forName("UTF-8"))))
				.toArray(Folder[]::new);
		for (Folder rf : repeatFolders) {
			if (!ConfigureReader.instance().accessFolder(rf, account)) {
				return "deleteError";
			}
			final List<Folder> l = this.fu.getParentList(rf.getFolderId());
			if (this.fu.deleteAllChildFolder(rf.getFolderId()) > 0) {
				this.lu.writeDeleteFolderEvent(request, rf, l);
			} else {
				return "deleteError";
			}
		}
		ServerInitListener.needCheck = true;
		return "deleteSuccess";
	}

	@Override
	public String createNewFolderByName(HttpServletRequest request) {
		final String parentId = request.getParameter("parentId");
		final String folderName = request.getParameter("folderName");
		final String folderConstraint = request.getParameter("folderConstraint");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		CreateNewFolderByNameRespons cnfbnr = new CreateNewFolderByNameRespons();
		if (parentId == null || folderName == null || parentId.length() <= 0 || folderName.length() <= 0) {
			cnfbnr.setResult("error");
			return gson.toJson(cnfbnr);
		}
		if (!TextFormateUtil.instance().matcherFolderName(folderName) || folderName.indexOf(".") == 0) {
			cnfbnr.setResult("error");
			return gson.toJson(cnfbnr);
		}
		final Folder parentFolder = this.fm.queryById(parentId);
		if (parentFolder == null || !ConfigureReader.instance().accessFolder(parentFolder, account)) {
			cnfbnr.setResult("error");
			return gson.toJson(cnfbnr);
		}
		if (!ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER,
				fu.getAllFoldersId(parentId))) {
			cnfbnr.setResult("error");
			return gson.toJson(cnfbnr);
		}
		if (fm.countByParentId(parentId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
			cnfbnr.setResult("foldersTotalOutOfLimit");
			return gson.toJson(cnfbnr);
		}
		Folder f = new Folder();
		if (fm.queryByParentId(parentId).parallelStream()
				.filter(e -> e.getFolderCreator().equals(account))
				.anyMatch((e) -> e.getFolderName().equals(folderName))) {
			f.setFolderName(FileNodeUtil.getNewFolderName(folderName, fm.queryByParentId(parentId)));
		} else {
			cnfbnr.setResult("error");
			return gson.toJson(cnfbnr);
		}
		// 设置子文件夹约束等级，不允许子文件夹的约束等级比父文件夹低
		int pc = parentFolder.getFolderConstraint();
		if (folderConstraint != null) {
			try {
				int ifc = Integer.parseInt(folderConstraint);
				if (ifc != 0 && account == null) {
					cnfbnr.setResult("error");
					return gson.toJson(cnfbnr);
				}
				if (ifc < pc) {
					cnfbnr.setResult("error");
					return gson.toJson(cnfbnr);
				} else {
					f.setFolderConstraint(ifc);
				}
			} catch (Exception e) {
				cnfbnr.setResult("error");
				return gson.toJson(cnfbnr);
			}
		} else {
			cnfbnr.setResult("error");
			return gson.toJson(cnfbnr);
		}
		f.setFolderId(UUID.randomUUID().toString());
		f.setDelFlag(FileDelFlag.FALSE.getName());
		f.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
		if (account != null) {
			f.setFolderCreator(account);
		} else {
			f.setFolderCreator("匿名用户");
		}
		f.setFolderParent(parentId);
		int i = 0;
		while (true) {
			try {
				final int r = this.fm.insertNewFolder(f);
				if (r > 0) {
					if (fu.hasRepeatFolder(f, account)) {
						cnfbnr.setResult("error");
						return gson.toJson(cnfbnr);
					} else {
						this.lu.writeCreateFolderEvent(request, f);
						cnfbnr.setResult("success");
						cnfbnr.setNewName(f.getFolderName());
						return gson.toJson(cnfbnr);
					}
				}
				break;
			} catch (Exception e) {
				f.setFolderId(UUID.randomUUID().toString());
				i++;
			}
			if (i >= 10) {
				break;
			}
		}
		cnfbnr.setResult("error");
		return gson.toJson(cnfbnr);
	}

}
