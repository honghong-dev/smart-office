//用户注册、登录、分页、assignRole、assignDept
package com.example.smart_office.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smart_office.entity.SysUser;
import com.example.smart_office.entity.dto.LoginDTO;
import com.example.smart_office.entity.dto.RegisterDTO;

import java.util.List;

public interface SysUserService extends IService<SysUser> {
    List<SysUser> listAllUsers();

    // 新增登录方法，返回 Token
    String login(LoginDTO loginDTO);
    // 新增注册方法
    boolean register(RegisterDTO registerDTO);
    Page<SysUser> getUserPage(Integer pageNum, Integer pageSize);

    List<String> getPermissionCodesByUserId(Long userId);

    void assignRole(Long userId, List<Long> roleIds);

    void assignDept(Long userId, Long deptId);
}