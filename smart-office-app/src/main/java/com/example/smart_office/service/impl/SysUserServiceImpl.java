//用户注册、登录、分页、assignRole、assignDept
package com.example.smart_office.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smart_office.config.RabbitMQConfig;
import com.example.smart_office.entity.SysRole;
import com.example.smart_office.entity.SysUser;
import com.example.smart_office.entity.SysUserRole;
import com.example.smart_office.entity.dto.LoginDTO;
import com.example.smart_office.entity.dto.RegisterDTO;
import com.example.smart_office.mapper.SysRoleMapper;
import com.example.smart_office.mapper.SysUserMapper;
import com.example.smart_office.mapper.SysUserRoleMapper;
import com.example.smart_office.service.AuditLogService;
import com.example.smart_office.service.SysUserService;
import com.example.smart_office.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private JwtUtils jwtUtils;

    // 密码加密器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    @Override
    public List<SysUser> listAllUsers() {
        return this.list();
    }

    @Override
    public String login(LoginDTO loginDTO) {
        // 1. 查用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, loginDTO.getUsername());
        SysUser user = this.getOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 2. 查出该用户的权限列表
        List<String> permissions = this.baseMapper.selectPermissionCodesByUserId(user.getId());

        // 3. 把权限写入 Redis（30分钟过期）
        try {
            String permissionsJson = objectMapper.writeValueAsString(permissions);
            redisTemplate.opsForValue().set("user:permissions:" + user.getId(), permissionsJson, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("=== 权限缓存失败: " + e.getMessage() + " ===");
        }

        // 4. 生成 Token
        return jwtUtils.generateToken(user.getUsername());
    }

    // 注入 RabbitMQ 的发送工具，用来往队列里发消息
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean register(RegisterDTO registerDTO) {
        // 1. 校验用户名
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, registerDTO.getUsername());
        if (this.count(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 保存用户
        SysUser user = new SysUser();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(registerDTO.getNickname());
        user.setEmail(registerDTO.getEmail());
        user.setStatus(1);
        user.setDeleted(0);
        boolean saved = this.save(user);

        // 3. 自动分配 USER 角色
        if (saved) {
            SysRole userRole = roleMapper.selectOne(
                    new QueryWrapper<SysRole>().eq("role_code", "USER")
            );
            if (userRole != null) {
                SysUserRole sysUserRole = new SysUserRole();
                sysUserRole.setUserId(user.getId());
                sysUserRole.setRoleId(userRole.getId());
                userRoleMapper.insert(sysUserRole);
            }
        }

        // 4. 发消息
        if (saved) {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.REGISTER_QUEUE,
                    "用户注册成功: " + user.getUsername()
            );
        }

        return saved;
    }



    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper; // 用于 JSON 序列化

    @Override
    public Page<SysUser> getUserPage(Integer pageNum, Integer pageSize) {
        // 1. 先拿当前用户
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        String currentUsername = jwtUtils.getUsernameFromToken(token);
        SysUser currentUser = userMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", currentUsername)
        );

        // 2. 缓存 Key，加 userId
        String cacheKey = "user:page:" + currentUser.getId() + ":" + pageNum + ":" + pageSize;

        try {
            // 3. 先查 Redis
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                System.out.println("=== 命中 Redis 缓存 ===");
                return objectMapper.readValue(cachedJson,
                        objectMapper.getTypeFactory().constructParametricType(Page.class, SysUser.class));
            }

            // 4. 查 MySQL，加数据权限
            System.out.println("=== 查询 MySQL ===");
            List<String> currentRoles = roleMapper.selectRoleCodesByUserId(currentUser.getId());
            boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
            boolean isAdmin = currentRoles.contains("ADMIN");

            Page<SysUser> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

            if (!isSuperAdmin && !isAdmin) {
                wrapper.eq(SysUser::getDeptId, currentUser.getDeptId());
            }

            Page<SysUser> result = this.page(page, wrapper);

            // 5. 写入 Redis
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, 5, TimeUnit.MINUTES);

            return result;
        } catch (Exception e) {
            System.out.println("=== Redis 异常，降级查库: " + e.getMessage() + " ===");
            Page<SysUser> page = new Page<>(pageNum, pageSize);
            return this.page(page);
        }
    }

    @Override
    public List<String> getPermissionCodesByUserId(Long userId) {
        // 1. 根据 userId 查角色ID
        // 2. 根据角色ID查权限ID
        // 3. 根据权限ID查权限编码
        // 简化版：直接用一条 SQL 联表查询
        return this.baseMapper.selectPermissionCodesByUserId(userId);
    }


    @Autowired
    private SysUserRoleMapper userRoleMapper;
    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private HttpServletRequest request;


    @Autowired
    private AuditLogService auditLogService;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long userId, List<Long> roleIds) {
        // 1. 拿当前登录用户
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new RuntimeException("Token 缺失");
        }
        String token = header.substring(7);
        String currentUsername = jwtUtils.getUsernameFromToken(token);
        SysUser currentUser = userMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", currentUsername)
        );
        if (currentUser == null) {
            throw new RuntimeException("当前用户不存在");
        }

        // 2. 查当前用户的角色
        List<String> currentRoles = roleMapper.selectRoleCodesByUserId(currentUser.getId());
        boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
        boolean isAdmin = currentRoles.contains("ADMIN");
        boolean isSubAdmin = currentRoles.contains("SUB_ADMIN");

        // 3. 查目标用户的角色
        List<String> targetRoles = roleMapper.selectRoleCodesByUserId(userId);

        // 4. 查角色 ID
        SysRole superAdminRole = roleMapper.selectOne(
                new QueryWrapper<SysRole>().eq("role_code", "SUPER_ADMIN")
        );
        SysRole adminRole = roleMapper.selectOne(
                new QueryWrapper<SysRole>().eq("role_code", "ADMIN")
        );

        // ===== 三级校验 =====

        // 4.1 不能改 SUPER_ADMIN 的角色（任何人，包括自己）
        if (targetRoles.contains("SUPER_ADMIN")) {
            throw new RuntimeException("不能修改超级管理员的角色");
        }

        // 4.2 不能分配 SUPER_ADMIN 角色
        if (roleIds.contains(superAdminRole.getId())) {
            throw new RuntimeException("不能将用户提升为超级管理员");
        }

        // 4.3 SUB_ADMIN：不能改自己
        if (isSubAdmin && currentUser.getId().equals(userId)) {
            throw new RuntimeException("低级管理员不能修改自己的角色");
        }

        // 4.4 SUB_ADMIN：不能改上级（ADMIN）
        if (isSubAdmin && targetRoles.contains("ADMIN")) {
            throw new RuntimeException("低级管理员不能修改管理员的角色");
        }

        // 4.5 SUB_ADMIN：不能改同级（另一个 SUB_ADMIN）
        if (isSubAdmin && targetRoles.contains("SUB_ADMIN")) {
            throw new RuntimeException("低级管理员不能修改同级管理员的角色");
        }

        // 4.6 SUB_ADMIN 不能把别人改成 ADMIN
        if (isSubAdmin && roleIds.contains(adminRole.getId())) {
            throw new RuntimeException("低级管理员不能将用户提升为管理员");
        }

        // 4.7 校验角色 ID 是否存在
        for (Long roleId : roleIds) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new RuntimeException("角色不存在: roleId=" + roleId);
            }
        }

        // 5. 删旧角色、插新角色、发消息
        userRoleMapper.delete(
                new QueryWrapper<SysUserRole>().eq("user_id", userId)
        );
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
        // 记录审计日志
        auditLogService.record(
                currentUser.getId(),
                currentUsername,
                "ASSIGN_ROLE",
                userId,
                "USER",
                targetRoles.toString(),
                roleIds.toString()
        );
        redisTemplate.convertAndSend("permission:refresh", userId.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDept(Long userId, Long deptId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setDeptId(deptId);
        userMapper.updateById(user);

        // 审计日志
        auditLogService.record(
                1L, "admin", "ASSIGN_DEPT", userId, "USER",
                null, deptId.toString()
        );
    }
}