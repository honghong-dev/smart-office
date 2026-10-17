//注册 JwtInterceptor，配置跨域
package com.example.smart_office.config;

import com.example.smart_office.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**") // 拦截所有 /api/ 开头的接口
                .excludePathPatterns(
                        "/api/user/login",      // 登录接口必须放行，不然谁都没法登录
                        "/api/user/register",   // 放行注册接口
                        "/actuator/**",         // 放行 Actuator 端点
                        "/api/test/**",         // 放行测试接口
                        "/ws/**"                // ★ 必须加这行：放行 WebSocket 握手
                );

    }
}