package com.example.smart_office.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)       // 只能贴在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时保留，方便拦截器反射读取
public @interface RequiresPermission {
    String value(); // 需要的权限编码，比如 "user:delete"
}