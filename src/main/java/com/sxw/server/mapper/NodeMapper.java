package com.sxw.server.mapper;

import com.sxw.server.model.Node;
import com.sxw.server.model.*;
import java.util.*;

public interface NodeMapper
{
	/**
	 * 
	 * <h2>根据文件夹ID查询其中的所有文件节点</h2>
	 * <p>该方法用于一次性将目标文件夹下的全部文件节点查询出来，如果超过限值，则只查询限值内的节点数量。</p>
	 * @author ggh@sxw.cn
	 * @param pfid java.lang.String 目标文件夹ID
	 * @return java.util.List 文件节点列表
	 */
    List<Node> queryByParentFolderId(final String pfid);

    /**
     * 构造文件系统的树状结构
     * @author ggh@sxw.cn
     * @return java.util.List <id,pid></>列表
     */
    List<String> queryNodeTree(final String account);

    /**
     * 查询删除的文件和文件夹
     * @author ggh@sxw.cn
     * @return java.util.List <file_id,folder_id></>列表
     */
    List<String> queryDeletedFileOrFolder(String account);

    /**
     * 
     * <h2>按照父文件夹的ID查找其下的所有文件（分页）</h2>
     * <p>该方法需要传入一个Map作为查询条件，其中需要包含pid（父文件夹的ID），offset（起始偏移），rows（查询行数）。</p>
     * @author ggh@sxw.cn
     * @param keyMap java.util.Map 封装查询条件的Map对象
     * @return java.util.List 查询结果
     */
    List<Node> queryByParentFolderIdSection(final Map<String, Object> keyMap);
    
    /**
     * 
     * <h2>按照父文件夹的ID统计其下的所有文件数目</h2>
     * <p>该方法主要用于配合queryByParentFolderIdSection方法实现分页加载。</p>
     * @author ggh@sxw.cn
     * @param pfid java.lang.String 父文件夹ID
     * @return long 文件总数
     */
    long countByParentFolderId(final String pfid);

    /**
     *
     * <h2>按照文件的md5统计引用同一文件块的所有虚拟文件</h2>
     * @author ggh@sxw.cn
     * @param fileMd5 java.lang.String 文件的md5值
     * @return List<Node> 文件列表
     */
    List<Node> queryByFileMd5(final String fileMd5);

    int insert(final Node f);
    
    int update(final Node f);
    
    int deleteByParentFolderId(final String pfid);

    long countByFilePath(final String filePath);

    int deleteById(final String fileId);
    
    Node queryById(final String fileId);

    Long queryFileOccupancySpace(final String account);

    int updateFileNameById(final Map<String, String> map);
    
    Node queryByPath(final String path);
    
    /**
	 * 
	 * <h2>查询与目标文件节点处于同一文件夹下的全部文件节点</h2>
	 * <p>该方法用于一次性将与目标文件同文件夹的文件节点查询出来，如果超过限值，则只查询限值内的节点数量。</p>
	 * @author ggh@sxw.cn
	 * @param fileId java.lang.String 目标文件ID
	 * @return java.util.List 文件节点列表
	 */
    List<Node> queryBySomeFolder(final String fileId);

    /**
     * 
     * <h2>移动文件节点至指定的文件夹节点</h2>
     * <p>该映射用于实现移动文件（剪切-粘贴）功能，将某一文件节点的父级路径更改为新的父级路径。</p>
     * @author ggh@sxw.cn
     * @param map 其中要包括要移动的文件节点id（fileId）以及新的父级文件夹id（locationpath）
     * @return 影响数目
     */
    int moveById(final Map<String, String> map);
}
