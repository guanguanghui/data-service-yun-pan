package com.sxw.server.util;

import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.enumeration.FileDelFlag;
import com.sxw.server.enumeration.FileSendType;
import com.sxw.server.exception.FoldersTotalOutOfLimitException;
import com.sxw.server.mapper.FileSenderMapper;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.model.FileSend;
import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import com.sxw.server.pojo.FolderSendView;
import org.springframework.stereotype.*;
import javax.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FolderUtil {

	@Resource
	private FolderMapper fm;
	@Resource
	private NodeMapper fim;
	@Resource
	private FileSenderMapper fsm;
	@Resource
	private FileBlockUtil fbu;

	/**
	 * 
	 * <h2>获得指定文件夹的所有上级文件夹</h2>
	 * <p>
	 * 该方法将返回目标文件夹的所有父级文件夹，并以列表的形式返回。如果上级层数超过了Integer.MAX_VALUE，那么只获取最后Integer.MAX_VALUE级。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param fid
	 *            java.lang.String 要获取的目标文件夹ID
	 * @return java.util.List
	 *         指定文件夹的所有父级文件夹列表，以com.sxw.server.model.Folder形式封装。
	 */
	public List<Folder> getParentList(final String fid) {
		Folder f = this.fm.queryById(fid);
		final List<Folder> folderList = new ArrayList<Folder>();
		if (f != null) {
			while (!f.getFolderParent().equals("null") && !f.getFolderParent().equals("NULL") && folderList.size() < Integer.MAX_VALUE) {
				f = this.fm.queryById(f.getFolderParent());
				folderList.add(f);
			}
		}
		Collections.reverse(folderList);
		return folderList;
	}

    public List<FolderSendView> getReceiveParentList(final String fid, final String receiver) {
        FileSend f = this.fsm.queryById(fid);
        final List<String> fsList = new ArrayList<String>();
        if (f != null) {
            while (!f.getPid().equals("NULL") && fsList.size() < Integer.MAX_VALUE) {
                f = this.fsm.queryById(f.getPid());
                fsList.add(f.getId());
            }
        }
        Collections.reverse(fsList);
        List<FolderSendView> folderList = fsList.parallelStream().map(folderId -> {
			FileSend fs = fsm.queryById(folderId);
			FolderSendView fsv = new FolderSendView(fm.queryById(fs.getFileId()));
			fsv.setId(fs.getId());
			fsv.setPid(fs.getPid());
            return fsv;
        }).collect(Collectors.toList());
        return folderList;
    }

	public List<String> getAllFoldersId(final String fid) {
		List<String> idList = new ArrayList<>();
		idList.addAll(getParentList(fid).parallelStream().map((e) -> e.getFolderId()).collect(Collectors.toList()));
		idList.add(fid);
		return idList;
	}

	public int deleteAllChildFolder(final String folderId) {
		final Thread deleteChildFolderThread = new Thread(() -> this.iterationDeleteFolder(folderId));
		deleteChildFolderThread.start();
		return this.fm.deleteById(folderId);
	}

	private void iterationDeleteFolder(final String folderId) {
		final List<Folder> cf = (List<Folder>) this.fm.queryByParentId(folderId);
		if (cf.size() > 0) {
			for (final Folder f : cf) {
				this.iterationDeleteFolder(f.getFolderId());
			}
		}
		final List<Node> files = (List<Node>) this.fim.queryByParentFolderId(folderId);
		if (files.size() > 0) {
			this.fim.deleteByParentFolderId(folderId);
			for (final Node f2 : files) {
				this.fbu.deleteFromFileBlocks(f2);
			}
		}
		this.fm.deleteById(folderId);
	}

	public int fakeDeleteAllChildFolder(final String folderId) {
		this.iterationFakeDeleteFolder(folderId);
		Map<String, String> map = new HashMap<>();
		map.put("folderId", folderId);
		map.put("locationpath", "recycle");
		return this.fm.moveById(map);
	}

	public int updateAllChildFolder(final Folder folder) {
		this.iterationUpdateFolder(folder,folder.getFolderCreator());
		return this.fm.update(folder);
	}

	private void iterationUpdateFolder(final Folder folder,String account) {
		String folderId = folder.getFolderId();
		final List<Folder> cf = (List<Folder>) this.fm.queryByParentId(folderId);
		// 执行更新
		folder.setFolderCreator(account);
		fm.update(folder);
		if (cf.size() > 0) {
			for (final Folder f : cf) {
				this.iterationUpdateFolder(f,account);
			}
		}

		final List<Node> files = (List<Node>) this.fim.queryByParentFolderId(folderId);
		if (files.size() > 0) {
			for (final Node f2 : files) {
				f2.setFileCreator(account);
				fim.update(f2);
			}
		}
	}

	private void iterationFakeDeleteFolder(final String folderId) {
		final List<Folder> cf = (List<Folder>) this.fm.queryByParentId(folderId);
		final Folder folder = this.fm.queryById(folderId);
		// 执行删除
		folder.setDelFlag(FileDelFlag.TRUE.getName());
		folder.setFolderCreationDate(String.valueOf(System.currentTimeMillis()));
		fm.update(folder);
		if (cf.size() > 0) {
			for (final Folder f : cf) {
				this.iterationFakeDeleteFolder(f.getFolderId());
			}
		}

		final List<Node> files = (List<Node>) this.fim.queryByParentFolderId(folderId);
		if (files.size() > 0) {
			for (final Node f2 : files) {
				f2.setDelFlag(FileDelFlag.TRUE.getName());
				f2.setFileCreationDate(String.valueOf(System.currentTimeMillis()));
				fim.update(f2);
			}
		}
	}

	public int deleteAllFolderSend(final String folderId) {
		final Thread deleteChildFolderThread = new Thread(() -> this.iterationDeleteFolderSend(folderId));
		deleteChildFolderThread.start();
		return this.fsm.deleteById(folderId);
	}

	private void iterationDeleteFolderSend(final String folderId) {
		Map<String, Object> keyMap1 = new HashMap<>();
		keyMap1.put("pid", folderId);
		keyMap1.put("offset", 0L);// 进行查询
		keyMap1.put("rows", Integer.MAX_VALUE);
		List<FileSend> fileSends = fsm.queryByPid(keyMap1);
		fileSends.stream().forEach(e -> {
			if(e.getFileType().equals(FileSendType.FILE.getName())){
				fsm.deleteById(e.getId());
			}else if(e.getFileType().equals(FileSendType.FOLDER.getName())){
                fsm.deleteById(e.getId());
				iterationDeleteFolderSend(e.getId());
			}
		});

	}

	public int restoreAllChildFolder(final String folderId, String locationpath) {
		this.iterationRestoreFolder(folderId);
		Map<String, String> map = new HashMap<>();
		map.put("folderId", folderId);
		map.put("locationpath", locationpath);
		return this.fm.moveById(map);
	}

	private void iterationRestoreFolder(final String folderId) {
		final List<Folder> cf = (List<Folder>) this.fm.queryByParentId(folderId);
		final Folder folder = this.fm.queryById(folderId);
		// 执行还原
		folder.setDelFlag(FileDelFlag.FALSE.getName());
		folder.setFolderCreationDate(ServerTimeUtil.accurateToSecond());
		fm.update(folder);
		if (cf.size() > 0) {
			for (final Folder f : cf) {
				this.iterationRestoreFolder(f.getFolderId());
			}
		}

		final List<Node> files = (List<Node>) this.fim.queryByParentFolderId(folderId);
		if (files.size() > 0) {
			for (final Node f2 : files) {
				f2.setDelFlag(FileDelFlag.FALSE.getName());
				f2.setFileCreationDate(ServerTimeUtil.accurateToSecond());
				fim.update(f2);
			}
		}
	}

	public Folder createNewFolder(final String parentId, String account, String folderName, String folderConstraint)
			throws FoldersTotalOutOfLimitException {
		if (!ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER, getAllFoldersId(parentId))) {
			return null;
		}
		if (parentId == null || folderName == null || parentId.length() <= 0 || folderName.length() <= 0) {
			return null;
		}
		if (folderName.indexOf(".") == 0) {
			return null;
		}
		final Folder parentFolder = this.fm.queryById(parentId);
		if (parentFolder == null) {
			return null;
		}
		if (!ConfigureReader.instance().accessFolder(parentFolder, account)) {
			return null;
		}
		if (fm.queryByParentId(parentId).parallelStream()
				.filter(e -> e.getFolderCreator().equals(account))
				.anyMatch((e) -> e.getFolderName().equals(folderName))) {
			return null;
		}
		if (fm.countByParentId(parentId) >= FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER) {
			throw new FoldersTotalOutOfLimitException();
		}
		Folder f = new Folder();
		// 设置子文件夹约束等级，不允许子文件夹的约束等级比父文件夹低
		int pc = parentFolder.getFolderConstraint();
		if (folderConstraint != null) {
			try {
				int ifc = Integer.parseInt(folderConstraint);
				if (ifc > 0 && account == null) {
					return null;
				}
				if (ifc < pc) {
					return null;
				} else {
					f.setFolderConstraint(ifc);
				}
			} catch (Exception e) {
				return null;
			}
		} else {
			return null;
		}
		f.setFolderId(UUID.randomUUID().toString());
		f.setFolderName(folderName);
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
					return f;
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
		return null;
	}

	// 检查新建的文件夹是否存在同名问题。若有，删除同名文件夹并返回是否进行了该操作（旨在确保上传文件夹操作不被重复上传干扰）
	public boolean hasRepeatFolder(Folder f) {
		Folder[] repeats = fm.queryByParentId(f.getFolderParent()).parallelStream()
				.filter((e) -> e.getFolderName().equals(f.getFolderName())).toArray(Folder[]::new);
		if (repeats.length > 1) {
			deleteAllChildFolder(f.getFolderId());
			return true;
		} else {
			return false;
		}
	}

	// 检查新建的文件夹是否存在同名问题。若有，删除同名文件夹并返回是否进行了该操作（旨在确保上传文件夹操作不被重复上传干扰）
	public boolean hasRepeatFolder(Folder f, String account) {
		Folder[] repeats = fm.queryByParentId(f.getFolderParent()).parallelStream()
				.filter(e -> e.getFolderCreator().equals(account))
				.filter((e) -> e.getFolderName().equals(f.getFolderName())).toArray(Folder[]::new);
		if (repeats.length > 1) {
			deleteAllChildFolder(f.getFolderId());
			return true;
		} else {
			return false;
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
	public void changeChildFolderConstraint(String folderId, int c) {
		Folder folder = fm.queryById(folderId);
		if (folder.getFolderConstraint() != c){
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("newConstraint", c);
			map.put("folderId", folderId);
			fm.updateFolderConstraintById(map);
		}
		List<Folder> cfs = fm.queryByParentId(folderId);
		for (Folder cf : cfs) {
			changeChildFolderConstraint(cf.getFolderId(), c);
		}
	}
}
