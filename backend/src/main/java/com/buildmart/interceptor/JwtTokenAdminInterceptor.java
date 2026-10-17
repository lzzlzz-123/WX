package com.buildmart.interceptor;

import com.buildmart.common.AuthContext;
import com.buildmart.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtTokenAdminInterceptor implements HandlerInterceptor {
    private static final String UNAUTHORIZED_BODY =
            "{\"code\":-401,\"message\":\"管理员登录已失效，请重新登录\",\"data\":null}";

    private final JwtTokenService jwtTokenService;

    public JwtTokenAdminInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        try {
            Claims claims = jwtTokenService.parseAdmin(bearerToken(request));
            Long adminId = claims.get("adminId", Long.class);
            if (adminId == null) throw new IllegalArgumentException("JWT 管理员 ID 为空");
            AuthContext.setAdminId(adminId);
            return true;
        } catch (Exception ex) {
            reject(response);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 请求头格式错误");
        }
        return authorization.substring(7).trim();
    }

    private void reject(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(UNAUTHORIZED_BODY);
        } catch (java.io.IOException ignored) {
            // The request is already being rejected; there is nothing useful to recover here.
        }
    }
}
