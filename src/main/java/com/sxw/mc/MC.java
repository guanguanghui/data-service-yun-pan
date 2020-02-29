package com.sxw.mc;

/**
 * 
 * <h2>sxwpan主类（启动类）</h2>
 * <p>
 * 该类为程序主类，内部的main方法为sxwpan程序的唯一入口。
 * </p>
 * <ul>
 * </ul>
 * 
 * @author ggh@sxw.cn
 * @version 1.0
 */

public class MC {
	/**
	 * 
	 * <h2>主方法</h2>
	 * <p>
	 * 这里是整个sxwpan应用的入口，即程序的主方法。
	 * </p>
	 * 
	 * @author ggh@sxw.cn
	 * @param args
	 *            String[] 接收控制台传入参数，例如“-console“
	 */
	public static void main(final String[] args) {
		if (args == null || args.length == 0) {
			System.out.println(new String(
						"错误！您可以尝试使用命令模式参数“-console”来启动并开始使用sxwpan。".getBytes()));
		} else {
			ConsoleRunner.build(args);// 以控制台模式启动sxwpan。
		}
	}
}
