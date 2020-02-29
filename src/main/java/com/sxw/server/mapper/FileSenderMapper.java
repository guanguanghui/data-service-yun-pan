package com.sxw.server.mapper;

import com.sxw.server.model.FileSend;
import com.sxw.server.model.Node;

import java.util.List;
import java.util.Map;

public interface FileSenderMapper
{
    List<FileSend> queryBySenderAndReceiver(final Map<String, String> map);

    FileSend queryByFileIdAndReceiver(final Map<String, String> map);

    List<FileSend> queryByReceiver(final Map<String, Object> map);

    long countByParentIdAndReceiver(final Map<String, String> map);

    int insert(final FileSend f);
    
    int update(final FileSend f);
}
