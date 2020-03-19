package com.sxw.server.util;

import com.sxw.printer.Printer;
import com.sxw.server.enumeration.AccountAuth;
import com.sxw.server.enumeration.FileSendType;
import com.sxw.server.mapper.FileSenderMapper;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.mapper.NodeMapper;
import com.sxw.server.model.FileSend;
import com.sxw.server.model.Folder;
import com.sxw.server.model.Node;
import org.springframework.stereotype.*;

import javax.annotation.*;
import org.springframework.web.multipart.*;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sxw.server.pojo.ExtendStores;

import java.util.*;
import java.util.zip.ZipEntry;

import org.zeroturnaround.zip.*;

/**
 * 
 * <h2>文件块整合操作工具</h2>
 * <p>
 * 该工具内包含了对文件系统中文件块的所有操作，使用IOC容器进行管理。
 * </p>
 * 
 * @author ggh@sxw.cn
 * @version 1.1
 */
@Component
public class FileBlockUtil {
	@Resource
	private NodeMapper fm;// 节点映射，用于遍历
	@Resource
	private FolderMapper flm;// 文件夹映射，同样用于遍历
	@Resource
	private FileSenderMapper fsm;// 收到文件映射
	@Resource
	private LogUtil lu;// 日志工具
	@Resource
	private FolderUtil fu;// 文件夹操作工具
	@Resource
	private AccessAuthUtil accessAuthUtil;
	/**
	 * 
	 * <h2>清理临时文件夹</h2>
	 * <p>该方法用于清理临时文件夹（如果临时文件夹不存在，则创建它），避免运行时产生的临时文件堆积。该方法应在服务器启动时和关闭过程中调用。</p>
	 * @author ggh@sxw.cn
	 */
	public void initTempDir() {
		final String tfPath = ConfigureReader.instance().getTemporaryfilePath();
		final File f = new File(tfPath);
		if (f.isDirectory()) {
			try {
				Iterator<Path> listFiles = Files.newDirectoryStream(f.toPath()).iterator();
				while(listFiles.hasNext()) {
					listFiles.next().toFile().delete();
				}
			} catch (IOException e) {
				lu.writeException(e);
				Printer.instance.print("错误：临时文件清理失败，请手动清理"+f.getAbsolutePath()+"文件夹内的临时文件。");
			}
		} else {
			if(!f.mkdir()) {
				Printer.instance.print("错误：无法创建临时文件夹"+f.getAbsolutePath()+"，请检查主文件系统存储路径是否可用。");
			}
		}
	}

	/**
	 * 
	 * <h2>将新上传的文件存入文件系统</h2>
	 * <p>
	 * 将一个MultipartFile类型的文件对象存入节点，并返回保存的路径名称。其中，路径名称使用“file_{UUID}.block”
	 * （存放于主文件系统中）或“{存储区编号}_{UUID}.block”（存放在指定编号的扩展存储区中）的形式。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param f
	 *            MultipartFile 上传文件对象
	 * @return String 随机生成的保存路径，如果保存失败则返回“ERROR”
	 */
	public String saveToFileBlocks(final MultipartFile f) {
		// 如果存在扩展存储区，则优先在已有文件块数目最少的扩展存储区中存放文件（避免占用主文件系统）
		List<ExtendStores> ess = ConfigureReader.instance().getExtendStores();// 得到全部扩展存储区
		if (ess.size() > 0) {
			// 将所有扩展存储区按照已存储文件块的数目从小到大进行排序
			Collections.sort(ess, new Comparator<ExtendStores>() {
				@Override
				public int compare(ExtendStores o1, ExtendStores o2) {
					try {
						// 通常情况下，直接比较子文件列表长度即可
						return o1.getPath().list().length - o2.getPath().list().length;
					} catch (Exception e) {
						try {
							// 如果文件太多以至于超出数组上限，则换用如下统计方法
							long dValue = Files.list(o1.getPath().toPath()).count()
									- Files.list(o2.getPath().toPath()).count();
							return dValue > 0L ? 1 : dValue == 0 ? 0 : -1;
						} catch (IOException e1) {
							return 0;
						}
					}
				}
			});
			// 排序完毕后，从文件块最少的开始遍历这些扩展存储区，并尝试将新文件存入一个容量足够的扩展存储区中
			for (ExtendStores es : ess) {
				// 如果该存储区的空余容量大于要存放的文件
				if (es.getPath().getFreeSpace() > f.getSize()) {
					try {
						File file = createNewBlock(es.getIndex() + "_", es.getPath());
						if (file != null) {
							f.transferTo(file);// 则执行存放，并将文件命名为“{存储区编号}_{UUID}.block”的形式
							return file.getName();
						} else {
							continue;// 如果本处无法生成新的文件块，那么在其他路径下继续尝试
						}
					} catch (IOException e) {
						// 如果无法存入（由于体积过大或其他问题），那么继续尝试其他扩展存储区
						continue;
					} catch (Exception e) {
						lu.writeException(e);
						Printer.instance.print(e.getMessage());
						continue;
					}
				}
			}
		}
		// 如果不存在扩展存储区或者最大的扩展存储区无法存放目标文件，则尝试将其存放至主文件系统路径下
		try {
			final File file = createNewBlock("file_", new File(ConfigureReader.instance().getFileBlockPath()));
			if (file != null) {
				f.transferTo(file);// 执行存放，并肩文件命名为“file_{UUID}.block”的形式
				return file.getName();
			}
		} catch (Exception e) {
			lu.writeException(e);
			Printer.instance.print("错误：文件块生成失败，无法存入新的文件数据。详细信息：" + e.getMessage());
		}
		return "ERROR";
	}

	// 生成创建一个在指定路径下名称（编号）绝对不重复的新文件块
	private File createNewBlock(String prefix, File parent) throws IOException {
		int appendIndex = 0;
		int retryNum = 0;
		String newName = prefix + UUID.randomUUID().toString().replace("-", "");
		File newBlock = new File(parent, newName + ".block");
		while (!newBlock.createNewFile()) {
			if (appendIndex >= 0 && appendIndex < Integer.MAX_VALUE) {
				newBlock = new File(parent, newName + "_" + appendIndex + ".block");
				appendIndex++;
			} else {
				if (retryNum >= 5) {
					return null;
				} else {
					newName = prefix + UUID.randomUUID().toString().replace("-", "");
					newBlock = new File(parent, newName + ".block");
					retryNum++;
				}
			}
		}
		return newBlock;
	}

	/**
	 * 
	 * <h2>计算上传文件的体积</h2>
	 * <p>
	 * 该方法用于将上传文件的体积换算以MB表示，以便存入文件系统。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param f
	 *            org.springframework.web.multipart.MultipartFile 上传文件对象
	 * @return java.lang.String 计算出来的体积，以MB为单位
	 */
	public String getFileSize(final MultipartFile f) {
		final long size = f.getSize();
		final int mb = (int) (size / 1048576L);
		return "" + mb;
	}

	/**
	 * 
	 * <h2>删除文件系统中的一个文件块</h2>
	 * <p>
	 * 根据传入的文件节点对象，删除其在文件系统中保存的对应文件块。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param f
	 *            com.sxw.server.model.Node 要删除的文件节点对象
	 * @return boolean 删除结果，true为成功
	 */
	public boolean deleteFromFileBlocks(Node f) {
		// 获取对应的文件块对象
		File file = getFileFromBlocks(f);
		// 获取文件块对象的引用数量
		long num = fm.countByFilePath(f.getFilePath());
		if(num > 1){
			return true;
		}
		if (file != null && num == 1) {
			return file.delete();// 执行删除操作
		}
		return false;
	}

	/**
	 * 
	 * <h2>得到文件系统中的一个文件块</h2>
	 * <p>
	 * 根据传入的文件节点对象，得到其在文件系统中保存的对应文件块。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param f
	 *            com.sxw.server.model.Node 要获得的文件节点对象
	 * @return java.io.File 对应的文件块抽象路径，获取失败则返回null
	 */
	public File getFileFromBlocks(Node f) {
		// 检查该节点对应的文件块存放于哪个位置（主文件系统/扩展存储区）
		try {
			File file = null;
			if (f.getFilePath().startsWith("file_")) {// 存放于主文件系统中
				// 直接从主文件系统的文件块存放区获得对应的文件块
				file = new File(ConfigureReader.instance().getFileBlockPath(), f.getFilePath());
			} else {// 存放于扩展存储区
				short index = Short.parseShort(f.getFilePath().substring(0, f.getFilePath().indexOf('_')));
				// 根据编号查到对应的扩展存储区路径，进而获取对应的文件块
				file = new File(ConfigureReader.instance().getExtendStores().parallelStream()
						.filter((e) -> e.getIndex() == index).findAny().get().getPath(), f.getFilePath());
			}
			if (file.isFile()) {
				return file;
			}
		} catch (Exception e) {
			lu.writeException(e);
			Printer.instance.print("错误：文件数据读取失败。详细信息：" + e.getMessage());
		}
		return null;
	}

	/**
	 * 
	 * <h2>校对文件块与文件节点</h2>
	 * <p>
	 * 将文件系统中不可用的文件块移除，以便保持文件系统的整洁。该操作应在服务器启动或出现问题时执行。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 */
	public void checkFileBlocks() {
		Thread checkThread = new Thread(() -> {
			// 检查是否存在未正确对应文件块的文件节点信息，若有则删除，从而确保文件节点信息不出现遗留问题
			checkNodes("root");
			checkNodes("recycle");
            checkReceiveNodes("receive");
			// 检查是否存在未正确对应文件节点的文件块，若有则删除，从而确保文件块不出现遗留问题
			List<File> paths = new ArrayList<>();
			paths.add(new File(ConfigureReader.instance().getFileBlockPath()));
			for (ExtendStores es : ConfigureReader.instance().getExtendStores()) {
				paths.add(es.getPath());
			}
			for (File path : paths) {
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(path.toPath())) {
					Iterator<Path> blocks = ds.iterator();
					while (blocks.hasNext()) {
						File testBlock = blocks.next().toFile();
						if (testBlock.isFile()) {
							long num = fm.countByFilePath(testBlock.getName());
							if (num == 0) {
								testBlock.delete();
							}
						}
					}
				} catch (IOException e) {
					Printer.instance.print("警告：文件节点效验时发生意外错误，可能未能正确完成文件节点效验。错误信息：" + e.getMessage());
					lu.writeException(e);
				}
			}
		});
		checkThread.start();
	}

	// 删除过期文件
	public void deleteExpiratedFiles(String account){
		List<String> ids = fm.queryDeletedFileOrFolder(account);
		long druation = ConfigureReader.instance().getExpirationDate(account);

		ids.parallelStream().forEach(e -> {
			String items[] = e.split(",");
			String id = items[0];
			String deleteData = items[1];
			boolean isFile = items[2] == "1";
			if(ExpirationDateUtil.isExpirationDate(deleteData,druation)){
				if (isFile){
					Node node = fm.queryById(id);
					deleteFromFileBlocks(node);
				}else {
					flm.deleteById(id);
				}
			}
		});
	}

	// 校对文件节点，要求某一节点必须有对应的文件块，否则将其移除（避免出现死节点）
	private void checkNodes(String fid) {
		List<Node> nodes = fm.queryByParentFolderId(fid);
		for (Node node : nodes) {
			File block = getFileFromBlocks(node);
			if (block == null) {
				fm.deleteById(node.getFileId());
			}
		}
		List<Folder> folders = flm.queryByParentId(fid);
		for (Folder fl : folders) {
			checkNodes(fl.getFolderId());
		}
	}

	// 校对收到文件节点，要求收到的文件必须有对应的文件节点，否则将其移除（避免收到死的收到文件节点）
	public void checkReceiveNodes(String pid){
		Map<String, Object> keyMap1 = new HashMap<>();
		keyMap1.put("pid", pid);
		keyMap1.put("offset", 0L);// 进行查询
		keyMap1.put("rows", Integer.MAX_VALUE);
		List<FileSend> fileSends = fsm.queryByPid(keyMap1);
		fileSends.stream().forEach(e -> {

		    if(e.getFileType().equals(FileSendType.FILE.getName())){
               Node node = fm.queryById(e.getFileId());
               if(node == null){
                   fsm.deleteById(e.getId());
               }
            }else if(e.getFileType().equals(FileSendType.FOLDER.getName())){
		        Folder folder = flm.queryById(e.getFileId());
		        if(folder == null){
                    fu.deleteAllFolderSend(e.getId());
                }else{
                    checkReceiveNodes(e.getId());
                }
            }

        });
	}

	/**
	 * 
	 * <h2>将指定节点及文件夹打包为ZIP压缩文件。</h2>
	 * <p>
	 * 该功能用于创建ZIP压缩文件，线程阻塞。如果压缩目标中存在同名情况，则使用“{文件名} (n).{后缀}”或“{文件夹名} n”的形式重命名。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param idList
	 *            java.util.List<String> 要压缩的文件节点目标ID列表
	 * @param fidList
	 *            java.util.List<String> 要压缩的文件夹目标ID列表，迭代压缩
	 * @param account
	 *            java.lang.String 用户ID，用于判断压缩文件夹是否有效
	 * @return java.lang.String
	 *         压缩后产生的文件名称，命名规则为“tf_{UUID}.zip”，存放于文件系统中的temporaryfiles目录下
	 */
	public String createZip(final List<String> idList, final List<String> fidList, String account) {
		final String zipname = "tf_" + UUID.randomUUID().toString() + ".zip";
		final String tempPath = ConfigureReader.instance().getTemporaryfilePath();
		final File f = new File(tempPath, zipname);
		try {
			final List<ZipEntrySource> zs = new ArrayList<>();
			// 避免压缩时出现同名文件导致打不开：
			final List<Folder> folders = new ArrayList<>();
			for (String fid : fidList) {
				Folder fo = flm.queryById(fid);
				FileSend folderSend = fsm.queryById(fid);
				if (folderSend != null){
					fo = flm.queryById(folderSend.getFileId());
				}

				if (accessAuthUtil.accessFolder(fo, account) && accessAuthUtil
						.authorized(account, AccountAuth.DOWNLOAD_FILES, fu.getAllFoldersId(fo.getFolderParent()))) {
					if (fo != null) {
						folders.add(fo);
					}
				}
			}
			final List<Node> nodes = new ArrayList<>();
			for (String id : idList) {
				Node n = fm.queryById(id);
				FileSend fileSend = fsm.queryById(id);
				if (fileSend != null){
				    n = fm.queryById(fileSend.getFileId());
                }
				if ( (accessAuthUtil.accessFolder(flm.queryById(n.getFileParentFolder()), account) || accessAuthUtil.accessSendFile(n, account))
						&& accessAuthUtil.authorized(account, AccountAuth.DOWNLOAD_FILES,
								fu.getAllFoldersId(n.getFileParentFolder()))) {
					if (n != null) {
						nodes.add(n);
					}
				}
			}
			for (Folder fo : folders) {
				int i = 1;
				String flname = fo.getFolderName();
				while (true) {
					if (folders.parallelStream().filter((e) -> e.getFolderName().equals(fo.getFolderName()))
							.count() > 1) {
						fo.setFolderName(flname + " " + i);
						i++;
					} else {
						break;
					}
				}
				addFoldersToZipEntrySourceArray(fo, zs, account, "");
			}
			for (Node node : nodes) {
					int i = 1;
					String fname = node.getFileName();
					while (true) {
						if (nodes.parallelStream().filter((e) -> e.getFileName().equals(node.getFileName())).count() > 1
								|| folders.parallelStream().filter((e) -> e.getFolderName().equals(node.getFileName()))
										.count() > 0) {
							if (fname.indexOf(".") >= 0) {
								node.setFileName(fname.substring(0, fname.lastIndexOf(".")) + " (" + i + ")"
										+ fname.substring(fname.lastIndexOf(".")));
							} else {
								node.setFileName(fname + " (" + i + ")");
							}
							i++;
						} else {
							break;
						}
					}
					zs.add((ZipEntrySource) new FileSource(node.getFileName(), getFileFromBlocks(node)));
			}
			ZipUtil.pack(zs.toArray(new ZipEntrySource[0]), f);
			return zipname;
		} catch (Exception e) {
			lu.writeException(e);
			Printer.instance.print(e.getMessage());
			return null;
		}
	}

	// 迭代生成ZIP文件夹单元，将一个文件夹内的文件和文件夹也进行打包
	private void addFoldersToZipEntrySourceArray(Folder f, List<ZipEntrySource> zs, String account, String parentPath) {
		if (f != null && accessAuthUtil.accessFolder(f, account)) {
			String folderName = f.getFolderName();
			String thisPath = parentPath + folderName + "/";
			zs.add(new ZipEntrySource() {

				@Override
				public String getPath() {
					return thisPath;
				}

				@Override
				public InputStream getInputStream() throws IOException {
					return null;
				}

				@Override
				public ZipEntry getEntry() {
					return new ZipEntry(thisPath);
				}
			});
			List<Folder> folders = flm.queryByParentId(f.getFolderId());
			for (Folder fo : folders) {
				int i = 1;
				String flname = fo.getFolderName();
				while (true) {
					if (folders.parallelStream().filter((e) -> e.getFolderName().equals(fo.getFolderName()))
							.count() > 1) {
						fo.setFolderName(flname + " " + i);
						i++;
					} else {
						break;
					}
				}
				addFoldersToZipEntrySourceArray(fo, zs, account, thisPath);
			}
			List<Node> nodes = fm.queryByParentFolderId(f.getFolderId());
			for (Node node : nodes) {
				int i = 1;
				String fname = node.getFileName();
				while (true) {
					if (nodes.parallelStream().filter((e) -> e.getFileName().equals(node.getFileName())).count() > 1
							|| folders.parallelStream().filter((e) -> e.getFolderName().equals(node.getFileName()))
									.count() > 0) {
						if (fname.indexOf(".") >= 0) {
							node.setFileName(fname.substring(0, fname.lastIndexOf(".")) + " (" + i + ")"
									+ fname.substring(fname.lastIndexOf(".")));
						} else {
							node.setFileName(fname + " (" + i + ")");
						}
						i++;
					} else {
						break;
					}
				}
				zs.add(new FileSource(thisPath + node.getFileName(), getFileFromBlocks(node)));
			}
		}
	}
}
