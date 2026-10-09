package com.example.smart_office.interceptor;

import com.example.smart_office.annotation.RequiresPermission;
import com.example.smart_office.entity.SysUser;
import com.example.smart_office.mapper.SysUserMapper;
import com.example.smart_office.service.SysUserService;
import com.example.smart_office.utils.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 只拦截 Controller 的方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 2. 从 Header 拿 Token
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.getWriter().write("Token is missing");
            return false;
        }
        String token = header.substring(7);

        // 3. 校验 Token
        if (!jwtUtils.validateToken(token)) {
            response.setStatus(401);
            response.getWriter().write("Token is invalid");
            return false;
        }

        // 4. 检查 @RequiresPermission
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequiresPermission permissionAnnotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (permissionAnnotation == null) {
            return true;
        }

        // 5. 需要的权限
        String requiredPermission = permissionAnnotation.value();

        // 6. 解析用户名，查 userId
        String username = jwtUtils.getUsernameFromToken(token);
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            response.setStatus(401);
            response.getWriter().write("User not found");
            return false;
        }

        // 7. 查 Redis 权限缓存
        String key = "user:permissions:" + userId;
        String permissionsJson = redisTemplate.opsForValue().get(key);

        // 8. 缓存未命中，查数据库，重建缓存
        if (permissionsJson == null) {
            List<String> permissionList = userService.getPermissionCodesByUserId(userId);
            permissionsJson = objectMapper.writeValueAsString(permissionList);
            redisTemplate.opsForValue().set(key, permissionsJson, 30, TimeUnit.MINUTES);
        }

        // 9. 判断权限
        List<String> permissions = objectMapper.readValue(permissionsJson, new TypeReference<List<String>>() {});
        if (!permissions.contains(requiredPermission)) {
            response.setStatus(403);
            response.getWriter().write("Forbidden: missing permission " + requiredPermission);
            return false;
        }

        return true;
    }

    /**
     * 通过用户名查 userId（真实查库）
     */
    private Long getUserIdByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", username)
        );
        return user != null ? user.getId() : null;
    }
}