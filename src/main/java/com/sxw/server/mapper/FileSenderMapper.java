package com.sxw.server.mapper;

import com.sxw.server.model.FileSend;
import java.util.List;
import java.util.Map;

public interface FileSenderMapper
{
    List<FileSend> queryBySenderAndReceiver(final Map<String, String> map);

    FileSend queryById(final String id);

    List<FileSend> queryByReceiver(final Map<String, Object> map);

    List<FileSend> queryByPid(final Map<String, Object> map);

    List<FileSend> queryFileSendByReceiver(final String account);

    long countByPid(final String pid);

    int insert(final FileSend f);
    
    int update(final FileSend f);

    int deleteById(final String id);
}
