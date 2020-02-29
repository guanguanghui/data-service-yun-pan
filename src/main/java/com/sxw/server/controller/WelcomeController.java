package com.sxw.server.controller;

import com.sxw.server.exception.BusinessException;
import com.sxw.server.pojo.TokenInfo;
import com.sxw.server.util.ConfigureReader;
import com.sxw.server.util.LogUtil;
import com.sxw.server.util.TokenResolver;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

@Controller
public class WelcomeController {
	@Resource
	private LogUtil lu;

	@RequestMapping({ "/" })
	public String home(final HttpServletRequest request, final HttpSession session) {
		try {
			final String token = request.getParameter((String) request.getParameterMap().keySet().toArray()[0]);
			TokenInfo ti = TokenResolver.readTokenInfo(token);

			String account = ti.getUserId();
			String password = "admin";

			if (ConfigureReader.instance().foundAccount(ti.getUserId())) {
				session.setAttribute("ACCOUNT", (Object) ti.getUserId());
				return "redirect:/home.html";
			}

			// 新账户和密码的合法性检查
			CharsetEncoder ios8859_1Encoder = Charset.forName("ISO-8859-1").newEncoder();
			if (account != null && account.length() >= 3 && ios8859_1Encoder.canEncode(account)) {
				if(account.indexOf("=") < 0 && account.indexOf(":") < 0) {
					if (password != null && password.length() >= 3 && password.length() <= 32
							&& ios8859_1Encoder.canEncode(password)) {
						if (ConfigureReader.instance().createNewAccount(account, password)) {
							lu.writeSignUpEvent(request, account, password);
							session.setAttribute("ACCOUNT", (Object) ti.getUserId());
						}
					}
				}
			}
		} catch (BusinessException e){
			return "redirect:" + ConfigureReader.instance().getLoginUrl();
		}
		catch (Exception e) {
			lu.writeException(e);
			return "redirect:/home.html";
		}
		return "redirect:/home.html";
	}

}
