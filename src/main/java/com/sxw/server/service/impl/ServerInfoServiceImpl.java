package com.sxw.server.service.impl;

import com.sxw.server.service.ServerInfoService;
import org.springframework.stereotype.*;
import java.util.*;
import java.text.*;

@Service
public class ServerInfoServiceImpl implements ServerInfoService
{
    @Override
    public String getOSName() {
        return System.getProperty("os.name");
    }
    
    @Override
    public String getServerTime() {
        final Date d = new Date();
        final DateFormat df = new SimpleDateFormat("YYYY\u5e74MM\u6708dd\u65e5 hh:mm");
        return df.format(d);
    }
}
