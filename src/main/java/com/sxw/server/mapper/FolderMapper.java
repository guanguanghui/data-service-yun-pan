package com.sxw.server.mapper;

import com.sxw.server.model.Folder;
import java.util.*;

public interface FolderMapper
{
    Folder queryById(final String fid);

    List<Folder> queryDeletedFolders(String account);

    List<Folder> queryByAccount(String account);

    /**
     * 
     * <h2>按照父文件夹的ID查找其下的所有文件夹（分页）</h2>
     * <p>该方法需要传入一个Map作为查询条件，其中需要包含pid（父文件夹的ID），offset（起始偏移），rows（查询行数）。</p>
     * @author ggh@sxw.cn
     * @param keyMap java.util.Map 封装查询条件的Map对象
     * @return java.util.List 查询结果
     */
    List<Folder> queryByParentIdSection(final Map<String, Object> keyMap);
    
    /**
     * 
     * <h2>按照父文件夹的ID统计其下的所有文件夹数目</h2>
     * <p>该方法主要用于配合queryByParentIdSection方法实现分页加载。</p>
     * @author ggh@sxw.cn
     * @param pid java.lang.String 父文件夹ID
     * @return long 文件夹总数
     */
    long countByParentId(final String pid);
    
    /**
     * 
     * <h2>根据目标文件夹ID查询其中的所有文件夹</h2>
     * <p>该方法用于将某个文件夹下的所有文件夹全部查询出，并以列表的形式返回。如果结果数量超出了最大限额，则只查询限额内的结果。</p>
     * @author ggh@sxw.cn
     * @param pid java.lang.String 目标文件夹ID
     * @return java.util.List 文件夹数组
     */
    List<Folder> queryByParentId(final String pid);

    List<Folder> queryByParentIdAndFolderName(final Map<String, String> map);
    
    int insertNewFolder(final Folder f);

    int update(final Folder f);

    int deleteById(final String folderId);
    
    int updateFolderNameById(final Map<String, String> map);
    
    int updateFolderConstraintById(final Map<String, Object> map);

	int moveById(Map<String, String> map);
}
