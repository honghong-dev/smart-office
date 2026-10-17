# Smart-Office RBAC 权限模型设计

本文档记录 Smart-Office 项目 RBAC 权限模型的完整设计、实现和验证过程。

---

## 一、什么是 RBAC

RBAC（Role-Based Access Control，基于角色的访问控制）的核心思想是：

不直接给"用户"分配权限，而是给"角色"分配权限，再把角色分配给用户。

模型：用户 -> 角色 -> 权限

---

## 二、为什么需要 RBAC

- 如果直接给用户分配权限：用户多、权限多时，维护成本极高。
- 用 RBAC：权限变更只改角色，所有拥有该角色的用户自动生效。
- 支持多对多：一个用户可以拥有多个角色，一个角色也可以拥有多个权限。

---

## 三、数据模型

### 表 1：sys_user（用户表）
| 字段 | 说明 |
|------|------|
| id | 用户 ID |
| username | 用户名 |
| password | BCrypt 加密的密码 |
| dept_id | 部门 ID（用于数据权限） |

### 表 2：sys_role（角色表）
| 字段 | 说明 |
|------|------|
| id | 角色 ID |
| role_name | 角色名称 |
| role_code | 角色编码 |

### 表 3：sys_user_role（用户角色关联表）
| 字段 | 说明 |
|------|------|
| user_id | 用户 ID |
| role_id | 角色 ID |

### 表 4：sys_permission（权限表）
| 字段 | 说明 |
|------|------|
| id | 权限 ID |
| permission_name | 权限名称 |
| permission_code | 权限编码 |
| type | 类型（MENU / BUTTON / API） |

### 表 5：sys_role_permission（角色权限关联表）
| 字段 | 说明 |
|------|------|
| role_id | 角色 ID |
| permission_id | 权限 ID |

### 表 6：sys_dept（部门表）
| 字段 | 说明 |
|------|------|
| id | 部门 ID |
| dept_name | 部门名称 |
| dept_code | 部门编码 |
| parent_id | 上级部门 ID |

---

## 四、四种角色（三级管理）

| 角色 | 权限 | 能改谁 |
|------|------|--------|
| SUPER_ADMIN（老板） | 所有权限 | 除自己外所有人，不能造第二个老板 |
| ADMIN（厂长） | 所有权限 | 除老板外所有人，能造厂长 |
| SUB_ADMIN（班长） | 有 user:assignRole 权限，但业务逻辑限制只能改员工 | 只能改员工，能把员工提拔为班长 |
| USER（员工） | 基础权限（approval:submit、user:profile） | 不能改任何人 |

### 三级管理规则
| 当前用户 | 能改自己 | 能改厂长 | 能改班长 | 能改员工 | 能造老板 |
|----------|----------|----------|----------|----------|----------|
| 老板 | 否 | 是 | 是 | 是 | 否 |
| 厂长 | 是 | 是 | 是 | 是 | 否 |
| 班长 | 否 | 否 | 否 | 是 | 否 |
| 员工 | 否 | 否 | 否 | 否 | 否 |

核心：不能越级，不能造老板。

### 注册自动分配 USER 角色
新用户注册时，自动分配 USER 角色，保证新用户有基础权限（approval:submit、user:profile），避免"注册后什么都不能干"。

### 部门手动分配（数据权限）
注册时，只分配 USER 身份。部门（`dept_id`）、岗位（角色），需要管理员手动分配。这是真实企业流程，就像入职长城汽车，前两天是"新员工"，两天后才分到"冲焊车间"，定岗定级。

---

## 五、权限编码规范

命名格式：`模块:动作`

| 模块 | 动作 | 编码示例 |
|------|------|----------|
| user | list / delete / assignRole / assignDept / profile | user:list、user:assignRole |
| approval | submit / handle / todo | approval:submit、approval:handle |
| device | telemetry / control | device:telemetry |
| mqtt | message / deadLetter | mqtt:message |

规范要点：
1. 全小写，冒号分隔。
2. 模块名用单数。
3. 动作名用动词。

---

## 六、权限校验流程

1. 用户登录成功后，根据 userId 查询权限编码列表。
2. 将权限列表写入 Redis（Key: `user:permissions:{userId}`，30 分钟过期）。
3. 每次请求进入 `JwtInterceptor`：
    - 校验 JWT 是否有效。
    - 检查方法上是否有 `@RequiresPermission` 注解。
    - 从 Redis 读取用户权限，比对是否包含所需权限。
    - 缓存未命中时，查数据库，重建缓存（用户无感，不用重新登录）。
    - 无权限返回 403。
4. 接口级别鉴权通过自定义注解 + 反射实现。

**关键设计**：JWT 里只放 userId，不放权限。权限每次从 Redis 实时查。

---

## 七、接口级鉴权实现方式

在 Controller 方法上加自定义注解：

```java
@RequiresPermission("user:delete")
@DeleteMapping("/{id}")
public Result<String> deleteUser(@PathVariable Long id) { ... }
```
拦截器通过反射读取 @RequiresPermission 注解，拿到所需的权限编码，与当前用户的权限集合比对。

八、核心代码结构
类/文件	作用
annotation/RequiresPermission.java	自定义权限注解
interceptor/JwtInterceptor.java	校验 JWT + 校验权限 + 缓存重建
service/SysUserServiceImpl.java	登录时把权限写入 Redis；注册自动分配 USER；assignRole 三级校验
mapper/SysUserMapper.java	联表查询权限编码
mapper/SysRoleMapper.java	selectRoleCodesByUserId，查用户角色
config/PermissionCacheRefreshListener.java	订阅 permission:refresh，清缓存
controller/UserController.java	使用 @RequiresPermission 注解
九、核心 SQL
sql
-- 根据用户 ID 查询权限编码
SELECT p.permission_code FROM sys_permission p
JOIN sys_role_permission rp ON p.id = rp.permission_id
JOIN sys_user_role ur ON rp.role_id = ur.role_id
WHERE ur.user_id = #{userId} AND p.deleted = 0;

-- 根据用户 ID 查询角色编码
SELECT r.role_code FROM sys_role r
JOIN sys_user_role ur ON r.id = ur.role_id
WHERE ur.user_id = #{userId} AND r.deleted = 0;
十、实测验证
场景一：有权限
用户拥有 user:list 权限。请求 GET /api/user/page，带 Token。返回 200，正常显示用户分页数据。

场景二：无权限
删除 user:list 权限的关联数据。清空 Redis，重新登录。请求 GET /api/user/page，带 Token。返回 403 Forbidden: missing permission user:list。

场景三：三级管理
操作	预期
老板改自己	失败：超级管理员不能修改自己的角色
老板改厂长	成功
老板造老板	失败：不能将用户提升为超级管理员
厂长改自己	成功
厂长改班长	成功
班长改自己	失败：低级管理员不能修改自己的角色
班长改厂长	失败：低级管理员不能修改管理员的角色
班长改员工	成功
班长把员工改成班长	成功
员工改任何人	失败：没有 user:assignRole 权限
场景四：权限缓存刷新
用户 xh 有 user:list 权限，请求 /api/user/page 返回 200。管理员调 assignRole，把 xh 的角色改成"无 user:list 的角色"。xh 不重新登录，用旧 Token，请求 /api/user/page，返回 403。期间，xh 不用重新登录。

场景五：缓存未命中，查库重建
管理员调 assignRole，删了 user:permissions:123。xh 请求 /api/user/page，JwtInterceptor 缓存未命中，查数据库，重建缓存。返回 200 或 403，取决于新权限。

场景六：数据权限
admin1（dept_id = 1，非管理员）调 /api/user/page，只看到 dept_id = 1 的用户。Boss（SUPER_ADMIN）调 /api/user/page，看到所有用户。

十一、权限缓存刷新机制（已完成）
问题
当前权限缓存在 Redis 里 30 分钟过期。管理员改了权限，用户要等 30 分钟或重新登录才生效。

方案：Redis Pub/Sub 主动刷新
管理员改权限的接口，调用 redisTemplate.convertAndSend("permission:refresh", userId)。
各 Java 实例订阅 permission:refresh 频道，收到消息后执行 redisTemplate.delete("user:permissions:" + userId)。
下次请求，权限缓存未命中，重新从数据库查，写入 Redis。

代码示例
java
// 发布端（管理员改权限后）
redisTemplate.convertAndSend("permission:refresh", userId.toString());

// 订阅端（各实例）
@Bean
public RedisMessageListenerContainer container(RedisConnectionFactory factory) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener((message, pattern) -> {
        String userId = new String(message.getBody());
        redisTemplate.delete("user:permissions:" + userId);
        log.info("权限缓存已刷新: userId={}", userId);
    }, new ChannelTopic("permission:refresh"));
    return container;
}
优点：权限变更秒级生效，不用重新登录。
缺点：需要处理多实例订阅、消息丢失等情况。

十二、数据权限（已完成）
方案
加部门表 sys_dept。用户表加 dept_id。查询时，如果是非管理员，加 WHERE dept_id = 当前用户的部门。

代码
java
// 3.2 查当前用户的角色
List<String> currentRoles = roleMapper.selectRoleCodesByUserId(currentUser.getId());
boolean isSuperAdmin = currentRoles.contains("SUPER_ADMIN");
boolean isAdmin = currentRoles.contains("ADMIN");

// 3.3 构造分页 + 数据权限
LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
if (!isSuperAdmin && !isAdmin) {
    wrapper.eq(SysUser::getDeptId, currentUser.getDeptId());
}
缓存 Key 要带 userId
java
String cacheKey = "user:page:" + currentUser.getId() + ":" + pageNum + ":" + pageSize;
如果 cacheKey 不带 userId，admin1 和 Boss 会共用同一个缓存，数据会串。

十三、性能优化（规划中）
问题
每次请求都查 Redis，高并发下有压力。

方案：本地 Caffeine 缓存 + Redis 二级缓存
本地 Caffeine：缓存 userId -> Set<permissionCode>，过期时间 5 分钟。

Redis：作为二级缓存，30 分钟过期。

请求进来，先查 Caffeine，未命中再查 Redis，都未命中再查数据库。

优点：减少 Redis 访问，提升性能。
缺点：本地缓存有延迟，权限变更时要同时清 Caffeine 和 Redis。

十四、SUB_ADMIN 权限说明
SUB_ADMIN（班长）拥有 user:assignRole 权限，能调用 assignRole 接口。
但业务逻辑做双向校验：

校验目标用户当前角色：不能是 SUPER_ADMIN、ADMIN、SUB_ADMIN。

校验目标角色：不能是 SUPER_ADMIN、ADMIN。

只能把员工改成班长。

所以 SUB_ADMIN 能进接口，但会被业务逻辑拦住越级操作。

注意：原文档「除 user:assignRole 外的所有权限」的表述，与场景三「班长改员工成功」矛盾。以本节说明为准。

十五、常见坑
问题	原因	解决方案
权限查询返回空数组	数据库关联数据缺失	手动插入 sys_user_role 和 sys_role_permission 数据
无权限时接口仍返回 200	拦截器没有严格校验注解	检查 JwtInterceptor 是否正确读取 @RequiresPermission
Redis 里没有权限缓存	登录逻辑没写入	检查 SysUserServiceImpl.login 是否调用了写入 Redis 的代码
权限编码不一致	注解里的编码与数据库不匹配	统一权限编码命名规范
改权限后不生效	JWT 里放了权限，Token 签发后固定	JWT 只放 userId，权限每次从 Redis 实时查
改了权限缓存还在	没清 Redis 缓存	用 Redis Pub/Sub 主动清缓存，JwtInterceptor 缓存未命中时查库重建
管理员把自己锁了	管理员能改自己的角色	老板不能改自己，班长不能改自己，只有厂长能改自己
数据权限，缓存串数据	cacheKey 不带 userId	cacheKey 加 currentUser.getId()
十六、下一步优化方向
权限动态刷新：Redis Pub/Sub 主动清缓存（已完成，详见第十一节）。

三级管理：老板、厂长、班长、员工，越级拦截（已完成，详见第四节）。

注册自动分配 USER：新用户有基础权限（已完成，详见第四节）。

审计日志：记录权限变更（已完成）。

数据权限：部门隔离（已完成，详见第十二节）。

本地 Caffeine 缓存：减少 Redis 访问（规划中，详见第十三节）。

菜单权限 + 前端联动：登录后返回用户可见的菜单列表。