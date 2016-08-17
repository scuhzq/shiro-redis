package org.zunpeng.shiro.core;

import org.apache.shiro.web.servlet.ShiroHttpServletRequest;
import org.apache.shiro.web.servlet.ShiroHttpServletResponse;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by dapeng on 16/8/10.
 */
public class ZeusShiroHttpServletResponse extends ShiroHttpServletResponse {

	public ZeusShiroHttpServletResponse(HttpServletResponse wrapped, ServletContext context, ShiroHttpServletRequest request) {
		super(wrapped, context, request);
	}

	@Override
	public String encodeRedirectURL(String url) {
		return url;
	}

	@Override
	public String encodeURL(String url) {
		return url;
	}
}
