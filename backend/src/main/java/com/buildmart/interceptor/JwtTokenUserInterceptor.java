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
public class JwtTokenUserInterceptor implements HandlerInterceptor {
    private final JwtTokenService jwtTokenService;

    public JwtTokenUserInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        try {
            Claims claims = jwtTokenService.parseUser(bearerToken(request));
            Long userId = claims.get("userId", Long.class);
            if (userId == null) throw new IllegalArgumentException("JWT 用户 ID 为空");
            AuthContext.setUserId(userId);
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
    }
}
