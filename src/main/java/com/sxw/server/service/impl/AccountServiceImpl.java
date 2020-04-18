package com.sxw.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sxw.printer.Printer;
import com.sxw.server.enumeration.UserRootSpace;
import com.sxw.server.enumeration.VCLevel;
import com.sxw.server.mapper.FolderMapper;
import com.sxw.server.pojo.ChangePasswordInfoPojo;
import com.sxw.server.pojo.LoginInfoPojo;
import com.sxw.server.pojo.PublicKeyInfo;
import com.sxw.server.pojo.SignUpInfoPojo;
import com.sxw.server.service.AccountService;
import com.sxw.server.util.*;
import org.springframework.stereotype.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.*;

@Service
public class AccountServiceImpl implements AccountService {
	@Resource
	private RSAKeyUtil ku;
	@Resource
	private LogUtil lu;
	@Resource
	private SxwApiUtil sau;
	@Resource
	private FolderMapper fm;
	@Resource
    private FolderUtil fu;

	// 登录密钥有效期
	private static final long TIME_OUT = 30000L;

	@Resource
	private Gson gson;

	private VerificationCodeFactory vcf;

	private CharsetEncoder ios8859_1Encoder;

	{
		ios8859_1Encoder = Charset.forName("ISO-8859-1").newEncoder();
		if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
			int line = 0;
			int oval = 0;
			switch (ConfigureReader.instance().getVCLevel()) {
			case Standard: {
				line = 6;
				oval = 2;
				break;
			}
			case Simplified: {
				line = 1;
				oval = 0;
				break;
			}
			default:
				break;
			}
			// 验证码生成工厂，包含了一些不太容易误认的字符
			vcf = new VerificationCodeFactory(45, line, oval, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm',
					'n', 'p', 'q', 'r', 's', 't', 'w', 'x', 'y', 'z', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
					'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z');
		}
	}

	// 关注账户，当任意一个账户登录失败后将加入至该集合中，登录成功则移除。登录集合中的账户必须进行验证码验证
	private static final Set<String> focusAccount = new HashSet<>();

	public String checkLoginRequest(final HttpServletRequest request, final HttpSession session) {
		final String encrypted = request.getParameter("encrypted");
		try {
			final String loginInfoStr = RSADecryptUtil.dncryption(encrypted, ku.getPrivateKey());
			final LoginInfoPojo info = gson.fromJson(loginInfoStr, LoginInfoPojo.class);
			if (System.currentTimeMillis() - Long.parseLong(info.getTime()) > TIME_OUT) {
				return "error";
			}
			final String accountId = info.getAccountId();
			if (!ConfigureReader.instance().foundAccount(accountId)) {
				return "accountnotfound";
			}
			// 如果验证码开启且该账户已被关注，则要求提供验证码
			if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
				synchronized (focusAccount) {
					if (focusAccount.contains(accountId)) {
						String reqVerCode = request.getParameter("vercode");
						String trueVerCode = (String) session.getAttribute("VERCODE");
						session.removeAttribute("VERCODE");// 确保一个验证码只会生效一次，无论对错
						if (reqVerCode == null || trueVerCode == null
								|| !trueVerCode.equals(reqVerCode.toLowerCase())) {
							return "needsubmitvercode";
						}
					}
				}
			}
			if (ConfigureReader.instance().checkAccountPwd(accountId, info.getAccountPwd())) {
				session.setAttribute("ACCOUNT", (Object) accountId);
				// 如果该账户输入正确且是一个被关注的账户，则解除该账户的关注，释放空间
				if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
					synchronized (focusAccount) {
						focusAccount.remove(accountId);
					}
				}
				return "permitlogin";
			}
			// 如果账户密码不匹配，则将该账户加入到关注账户集合，避免对方进一步破解
			synchronized (focusAccount) {
				if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
					focusAccount.add(accountId);
				}
			}
			return "accountpwderror";
		} catch (Exception e) {
			return "error";
		}
	}

	public void logout(final HttpSession session) {
		session.invalidate();
	}

	public String getPublicKey() {
		PublicKeyInfo pki = new PublicKeyInfo();
		pki.setPublicKey(ku.getPublicKey());
		pki.setTime(System.currentTimeMillis());
		return gson.toJson(pki);
	}

	@Override
	public void getNewLoginVerCode(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
		try {
			if (ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
				response.sendError(404);
			} else {
				VerificationCode vc = vcf.next(4);
				session.setAttribute("VERCODE", vc.getCode());
				response.setContentType("image/png");
				OutputStream out = response.getOutputStream();
				vc.saveTo(out);
				out.flush();
				out.close();
			}
		} catch (IOException e) {
			try {
				response.sendError(500);
			} catch (IOException e1) {

			}
		}
	}

	@Override
	public String doPong(HttpServletRequest request) {
		if (request.getSession().getAttribute("ACCOUNT") != null) {
			return "pong";// 只有登录了的账户才有必要进行应答
		} else {
			return "";// 未登录则不返回标准提示，但也做应答（此时，前端应停止后续应答以节省线程开支）
		}
	}

	@Override
	public String changePassword(HttpServletRequest request) {
		// 验证是否开启了用户修改密码功能
		if (!ConfigureReader.instance().isAllowChangePassword()) {
			return "illegal";
		}
		// 必须登录了一个账户
		HttpSession session = request.getSession();
		final String account = (String) session.getAttribute("ACCOUNT");
		if (account == null) {
			return "mustlogin";
		}
		// 解析修改密码请求
		final String encrypted = request.getParameter("encrypted");
		try {
			final String changePasswordInfoStr = RSADecryptUtil.dncryption(encrypted, ku.getPrivateKey());
			final ChangePasswordInfoPojo info = gson.fromJson(changePasswordInfoStr, ChangePasswordInfoPojo.class);
			if (System.currentTimeMillis() - Long.parseLong(info.getTime()) > TIME_OUT) {
				return "error";
			}
			// 如果验证码开启且该账户已被关注，则要求提供验证码
			if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
				synchronized (focusAccount) {
					if (focusAccount.contains(account)) {
						String reqVerCode = request.getParameter("vercode");
						String trueVerCode = (String) session.getAttribute("VERCODE");
						session.removeAttribute("VERCODE");// 确保一个验证码只会生效一次，无论对错
						if (reqVerCode == null || trueVerCode == null
								|| !trueVerCode.equals(reqVerCode.toLowerCase())) {
							return "needsubmitvercode";
						}
					}
				}
			}
			if (ConfigureReader.instance().checkAccountPwd(account, info.getOldPwd())) {
				// 如果该账户输入正确且是一个被关注的账户，则解除该账户的关注，释放空间
				if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
					synchronized (focusAccount) {
						focusAccount.remove(account);
					}
				}
				String newPassword = info.getNewPwd();
				// 新密码合法性检查
				if (newPassword != null && newPassword.length() >= 3 && newPassword.length() <= 32
						&& ios8859_1Encoder.canEncode(newPassword)) {
					if (ConfigureReader.instance().changePassword(account, newPassword)) {
						lu.writeChangePasswordEvent(request, account, newPassword);
						return "success";
					}
				}
				return "invalidnewpwd";
			} else {
				// 如果账户密码不匹配，则将该账户加入到关注账户集合，避免对方进一步破解
				synchronized (focusAccount) {
					if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
						focusAccount.add(account);
					}
				}
				return "oldpwderror";
			}
		} catch (Exception e) {
			lu.writeException(e);
			return "cannotchangepwd";
		}
	}

	@Override
	public String isAllowSignUp() {
		return ConfigureReader.instance().isAllowSignUp() ? "true" : "false";
	}

	@Override
	public String doSignUp(HttpServletRequest request) {
		// 验证是否开启了注册功能
		if (!ConfigureReader.instance().isAllowSignUp()) {
			return "illegal";
		}
		HttpSession session = request.getSession();
		// 如果已经登入一个账户了，必须先注销
		if (session.getAttribute("ACCOUNT") != null) {
			return "mustlogout";
		}
		// 如果开启了验证码则必须输入
		String reqVerCode = request.getParameter("vercode");
		if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
			String trueVerCode = (String) session.getAttribute("VERCODE");
			session.removeAttribute("VERCODE");// 确保一个验证码只会生效一次，无论对错
			if (reqVerCode == null || trueVerCode == null || !trueVerCode.equals(reqVerCode.toLowerCase())) {
				return "needvercode";
			}
		}
		// 解析注册请求
		final String encrypted = request.getParameter("encrypted");
		try {
			final String signUpInfoStr = RSADecryptUtil.dncryption(encrypted, ku.getPrivateKey());
			final SignUpInfoPojo info = gson.fromJson(signUpInfoStr, SignUpInfoPojo.class);
			if (System.currentTimeMillis() - Long.parseLong(info.getTime()) > TIME_OUT) {
				return "error";
			}
			if (ConfigureReader.instance().foundAccount(info.getAccount())) {
				return "accountexists";
			}
			String account = info.getAccount();
			String password = info.getPwd();
			// 新账户和密码的合法性检查
			if (account != null && account.length() >= 3 && account.length() <= 32
					&& ios8859_1Encoder.canEncode(account)) {
				if(account.indexOf("=") < 0 && account.indexOf(":") < 0) {
					if (password != null && password.length() >= 3 && password.length() <= 32
							&& ios8859_1Encoder.canEncode(password)) {
						if (ConfigureReader.instance().createNewAccount(account, password)) {
                            // 初始化用户空间
                            fu.initUserFolder(UserRootSpace.ROOT.getVaue(),account);
                            fu.initUserFileSend(UserRootSpace.RECEIVE.getVaue(),account);
                            fu.initUserFolder(UserRootSpace.RECYCLE.getVaue(),account);
							lu.writeSignUpEvent(request, account, password);
							session.setAttribute("ACCOUNT", account);
							return "success";
						} else {
							return "cannotsignup";
						}
					} else {
						return "invalidpwd";
					}
				}else {
					return "illegalaccount";
				}
			} else {
				return "invalidaccount";
			}
		} catch (Exception e) {
			lu.writeException(e);
			return "cannotsignup";
		}
	}

	@Override
	public String getGoodFriends(final HttpServletRequest request){
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		if (account == null) {
			return "mustlogin";
		}
		final ConfigureReader cr = ConfigureReader.instance();
		List<String> goodFriends = cr.getExistUsers().parallelStream()
				.filter(e -> !e.equals(account))
				.collect(Collectors.toList());
		return gson.toJson(goodFriends);
	}

	// 获得智慧校园组织机构树
    private static void getAllDepartmentTree(JSONArray nodes, JSONArray targetTreeArr){
        if (nodes == null){
            return;
        }
        if(targetTreeArr == null){
            targetTreeArr = new JSONArray();
        }
        for (int i=0;i<nodes.size();i++){
            JSONObject department = (JSONObject) nodes.get(i);
            JSONObject newDepartment = new JSONObject();

            // 添加部门节点的 text，href，tags
            newDepartment.put("text",department.getString("name"));
            newDepartment.put("href",department.getString("id"));
            JSONArray tags = new JSONArray();
            tags.add(0,""+department.getLong("deptTeacherNum"));
            newDepartment.put("tags",tags);

            // 添加组织节点的负责人
            JSONArray userOutputDtos = department.getJSONArray("userOutputDtos");
            JSONArray newUserOutputDtos = new JSONArray();
            for(int k=0;k<userOutputDtos.size();k++){
                JSONObject userOutputDto = (JSONObject) userOutputDtos.get(k);
                JSONObject newUserOutputDto = new JSONObject();
                newUserOutputDto.put("text",userOutputDto.getString("userName") + " [" + userOutputDto.getString("duty") + "]") ;
                newUserOutputDto.put("href",userOutputDto.getString("userId"));
                JSONArray userTags = new JSONArray();
                userTags.add(0,"0");
                newUserOutputDto.put("tags",userTags);
                newUserOutputDtos.add(k,newUserOutputDto);
            }

            // 添加子部门
            JSONArray sunDepartments = department.getJSONArray("sunDepartment");
            newDepartment.put("nodes",newUserOutputDtos);
            targetTreeArr.add(newDepartment);
            getAllDepartmentTree(sunDepartments,newUserOutputDtos);

        }
    }

	@Override
	public String getAllDepartmentInfo(final HttpServletRequest request){
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		if (account == null) {
			return "mustlogin";
		}
		final ConfigureReader cr = ConfigureReader.instance();
		final String token = (String) request.getSession().getAttribute("TOKEN");
		if(token != null){
		    String body = sau.getAllDepartment(cr.getAllDepartmentUrl(),token);
		    try {
                JSONArray nodes = JSON.parseObject(body).getJSONArray("data");
                JSONArray targetTreeArr = new JSONArray();
                getAllDepartmentTree(nodes,targetTreeArr);
                return targetTreeArr.toJSONString();
            }catch (Exception e){
                Printer.instance.print(e.getMessage());
                return "noDepartmentInfo";
            }
		}else {
			return "noDepartmentInfo";
		}
	}

}
