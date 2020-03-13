package com.sxw.server.configation;

import com.sxw.server.util.ConfigureReader;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.resource.*;

import com.google.gson.Gson;

import org.springframework.beans.factory.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

import com.sxw.server.pojo.ExtendStores;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.*;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.*;

/**
 * 
 * <h2>Web功能-MVC相关配置类</h2>
 * <p>
 * 该Spring配置类主要负责配置sxwpan网页服务器的处理行为。
 * </p>
 * 
 * @author ggh@sxw.cn
 * @version 1.0
 */
@Configurable
@ComponentScan({ "com.sxw.server.controller", "com.sxw.server.service.impl", "com.sxw.server.util" })
@ServletComponentScan({ "com.sxw.server.listener", "com.sxw.server.filter" })
@Import({ DataAccess.class })
public class MVC extends ResourceHttpRequestHandler implements WebMvcConfigurer {

	@Override
	public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		// TODO 自动生成的方法存根
		configurer.enable();
	}

    @Bean
    public FilterRegistrationBean corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 设置你要允许的网站域名，如果全允许则设为 *
        config.addAllowedOrigin("*");
        // 如果要限制 HEADER 或 METHOD 请自行更改
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter(source));
        // 这个顺序很重要哦，为避免麻烦请设置在最前
        bean.setOrder(0);
        return bean;
    }

	public void addResourceHandlers(final ResourceHandlerRegistry registry) {
		// 将静态页面资源所在文件夹加入至资源路径中
		registry.addResourceHandler(new String[] { "/**" }).addResourceLocations(new String[] {
				"file:" + ConfigureReader.instance().getPath() + File.separator + "webContext" + File.separator });
		// 将所有文件块的保存路径加入至资源路径中（提供某些预览服务）
		List<String> paths = new ArrayList<>();
		paths.add("file:" + ConfigureReader.instance().getFileBlockPath());
		for (ExtendStores es : ConfigureReader.instance().getExtendStores()) {
			paths.add(
					"file:" + (es.getPath().getAbsolutePath().endsWith(File.separator) ? es.getPath().getAbsolutePath()
							: es.getPath().getAbsolutePath() + File.separator));
		}
		registry.addResourceHandler(new String[] { "/fileblocks/**" })
				.addResourceLocations(paths.toArray(new String[0]));

		//定向swagger 位置
		registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
		registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");

	}

	@Bean
	public MultipartConfigElement multipartConfigElement() {
		final MultipartConfigFactory factory = new MultipartConfigFactory();
		factory.setMaxFileSize(-1L);
		factory.setLocation(ConfigureReader.instance().getTemporaryfilePath());
		return factory.createMultipartConfig();
	}

	@Bean
	public Gson gson() {
		return new Gson();
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
