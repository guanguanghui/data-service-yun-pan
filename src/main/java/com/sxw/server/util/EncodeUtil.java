package com.sxw.server.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * 
 * <h2>字符串编码工具</h2>
 * <p>该工具提供字符串的编码格式化功能。提供的全部方法均为静态的，因此无需创建该类实例。</p>
 * @author ggh@sxw.cn
 * @version 1.0
 */
public class EncodeUtil {
	
	private EncodeUtil() {}

	/**
	 * 
	 * <h2>文件名转码</h2>
	 * <p>
	 * 将文件名转码为UTF-8并确保特殊字符能够正确显示，建议在提供给用户文件之前对文件名进行本转码操作。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param name
	 *            java.lang.String 原始文件名
	 * @return java.lang.String 转码后的文件名
	 */
	public static String getFileNameByUTF8(String name) {
		try {
			return URLEncoder.encode(name, "UTF-8").replaceAll("\\+", "%20").replaceAll("%28", "\\(")
					.replaceAll("%29", "\\)").replaceAll("%3B", ";").replaceAll("%40", "@").replaceAll("%23", "\\#")
					.replaceAll("%26", "\\&");
		} catch (UnsupportedEncodingException e) {
			// TODO 自动生成的 catch 块
			return name.replaceAll("\\+", "%20").replaceAll("%28", "\\(").replaceAll("%29", "\\)")
					.replaceAll("%3B", ";").replaceAll("%40", "@").replaceAll("%23", "\\#").replaceAll("%26", "\\&");
		}
	}

}
