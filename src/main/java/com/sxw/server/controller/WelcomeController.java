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
		return "redirect:/home.html";
	}

}
