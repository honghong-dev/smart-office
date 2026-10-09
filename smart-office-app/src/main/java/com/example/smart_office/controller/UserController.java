package com.example.smart_office.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.smart_office.annotation.RequiresPermission;
import com.example.smart_office.common.Result;
import com.example.smart_office.entity.SysUser;
import com.example.smart_office.entity.dto.AssignDeptDTO;
import com.example.smart_office.entity.dto.AssignRoleDTO;
import com.example.smart_office.entity.dto.LoginDTO;
import com.example.smart_office.entity.dto.RegisterDTO;
import com.example.smart_office.service.SysUserService;

import com.example.smart_office.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtUtils jwtUtils;


    @RequiresPermission("user:list")
    @GetMapping("/page")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<SysUser> page = sysUserService.getUserPage(pageNum, pageSize);
        return Result.success(page);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        String token = sysUserService.login(loginDTO);
        return Result.success(token);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterDTO registerDTO) {
        boolean success = sysUserService.register(registerDTO);
        return success ? Result.success("注册成功") : Result.error(500, "注册失败");
    }

    @GetMapping("/permissions/{userId}")
    public Result<List<String>> getPermissions(@PathVariable Long userId) {
        List<String> permissions = sysUserService.getPermissionCodesByUserId(userId);
        return Result.success(permissions);
    }

    @PostMapping("/assignRole")
    @RequiresPermission("user:assignRole")
    public Result<String> assignRole(@RequestBody AssignRoleDTO dto) {
        sysUserService.assignRole(dto.getUserId(), dto.getRoleIds());
        return Result.success("分配成功");
    }

    @PostMapping("/assignDept")
    @RequiresPermission("user:assignRole")
    public Result<String> assignDept(@RequestBody AssignDeptDTO dto) {
        sysUserService.assignDept(dto.getUserId(), dto.getDeptId());
        return Result.success("分配部门成功");
    }
}