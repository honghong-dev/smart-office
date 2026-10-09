# Smart-Office 数据库设计文档

## 一、基本信息

- 数据库名：`smart_office`
- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_0900_ai_ci`
- 存储引擎：`InnoDB`

**说明**：另有一个 `nacos_config` 库，用于 Nacos 配置中心，见本文第 7 节。设备遥测数据同时双写 MySQL 和 TDengine，见第 9 节。

---

## 二、表结构设计

### 表 1：sys_user（用户表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| username | VARCHAR(50) | 否 | 无 | 用户名（唯一索引） |
| password | VARCHAR(100) | 否 | 无 | BCrypt 加密后的密码 |
| nickname | VARCHAR(50) | 是 | 无 | 昵称 |
| email | VARCHAR(100) | 是 | 无 | 邮箱 |
| status | TINYINT | 是 | 1 | 状态（1正常 0禁用） |
| dept_id | BIGINT | 是 | 无 | 部门 ID（数据权限） |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |
| update_time | DATETIME | 是 | 当前时间 | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

### 表 2：sys_role（角色表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| role_name | VARCHAR(50) | 否 | 无 | 角色名称 |
| role_code | VARCHAR(50) | 否 | 无 | 角色编码（唯一索引） |
| description | VARCHAR(200) | 是 | 无 | 角色描述 |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |
| update_time | DATETIME | 是 | 当前时间 | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**四种角色**：

| role_code | role_name | description |
|-----------|-----------|-------------|
| SUPER_ADMIN | 超级管理员 | 拥有所有权限，唯一，不能新增，不能被修改 |
| ADMIN | 管理员 | 拥有所有权限，能管理除超级管理员外的所有用户 |
| SUB_ADMIN | 副管理员 | 有 user:assignRole 权限，但业务逻辑限制只能改员工 |
| USER | 普通用户 | 基础权限，不能管理 |

### 表 3：sys_user_role（用户角色关联表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| user_id | BIGINT | 否 | 无 | 用户 ID |
| role_id | BIGINT | 否 | 无 | 角色 ID |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |

**约束**：`uk_user_role` 唯一索引（user_id + role_id）。

### 表 4：sys_permission（权限表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| permission_name | VARCHAR(50) | 否 | 无 | 权限名称 |
| permission_code | VARCHAR(50) | 否 | 无 | 权限编码（唯一索引） |
| type | VARCHAR(20) | 是 | API | 类型 |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |
| update_time | DATETIME | 是 | 当前时间 | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**现有权限**：

| permission_code | permission_name |
|-----------------|-----------------|
| user:list | 查询用户列表 |
| user:delete | 删除用户 |
| user:assignRole | 分配角色 |
| user:assignDept | 分配部门 |
| user:profile | 查看修改个人信息 |
| approval:submit | 提交审批 |
| approval:handle | 处理审批 |

### 表 5：sys_role_permission（角色权限关联表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| role_id | BIGINT | 否 | 无 | 角色 ID |
| permission_id | BIGINT | 否 | 无 | 权限 ID |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |

**约束**：`uk_role_permission` 唯一索引（role_id + permission_id）。

**角色-权限矩阵**：

| 角色 | 权限 |
|------|------|
| SUPER_ADMIN | user:list、user:delete、user:assignRole、user:assignDept、user:profile、approval:submit、approval:handle |
| ADMIN | user:list、user:delete、user:assignRole、user:assignDept、user:profile、approval:submit、approval:handle |
| SUB_ADMIN | user:list、user:delete、user:assignRole、user:assignDept、user:profile、approval:submit、approval:handle |
| USER | user:profile、approval:submit |

### 表 6：sys_dept（部门表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| dept_name | VARCHAR(50) | 否 | 无 | 部门名称 |
| dept_code | VARCHAR(50) | 否 | 无 | 部门编码 |
| parent_id | BIGINT | 是 | 0 | 上级部门 ID |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

**示例数据**：

| id | dept_name | dept_code |
|----|-----------|-----------|
| 1 | 研发部 | DEV |
| 2 | 销售部 | SALES |
| 3 | 冲焊车间 | WELD |

### 表 7：sys_approval（审批单主表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| title | VARCHAR(100) | 否 | 无 | 审批标题 |
| type | VARCHAR(50) | 否 | 无 | 审批类型 |
| content | VARCHAR(500) | 是 | 无 | 审批内容 |
| applicant_id | BIGINT | 否 | 无 | 申请人 ID |
| approver_id | BIGINT | 否 | 无 | 当前审批人 ID |
| status | TINYINT | 是 | 0 | 状态（0待审批 1已通过 2已驳回） |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |
| update_time | DATETIME | 是 | 当前时间 | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除 |

### 表 8：sys_approval_record（审批记录表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| approval_id | BIGINT | 否 | 无 | 审批单 ID |
| operator_id | BIGINT | 否 | 无 | 操作人 ID |
| action | VARCHAR(20) | 否 | 无 | 动作（SUBMIT/APPROVE/REJECT） |
| comment | VARCHAR(200) | 是 | 无 | 审批意见 |
| create_time | DATETIME | 是 | 当前时间 | 操作时间 |

### 表 9：sys_audit_log（审计日志表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| operator_id | BIGINT | 否 | 无 | 操作人 ID |
| operator_name | VARCHAR(50) | 是 | 无 | 操作人用户名 |
| operation | VARCHAR(50) | 否 | 无 | 操作类型（ASSIGN_ROLE / HANDLE_APPROVAL） |
| target_id | BIGINT | 是 | 无 | 目标 ID |
| target_type | VARCHAR(50) | 是 | 无 | 目标类型 |
| old_value | VARCHAR(500) | 是 | 无 | 旧值 |
| new_value | VARCHAR(500) | 是 | 无 | 新值 |
| ip | VARCHAR(50) | 是 | 无 | 操作 IP |
| create_time | DATETIME | 是 | 当前时间 | 创建时间 |

**索引**：

- `idx_operator`（operator_id）
- `idx_create_time`（create_time）

### 表 10：device_telemetry（设备遥测数据表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| device_id | VARCHAR(50) | 否 | 无 | 设备 ID |
| temperature | DECIMAL(5,2) | 否 | 无 | 温度 |
| report_time | DATETIME | 是 | 无 | 上报时间（由 MyBatis-Plus 自动填充） |

**索引**：

- `idx_device_time`（device_id + report_time）：按设备 + 时间段查询，防止全表扫描。

### 表 11：mqtt_message_log（MQTT 消息处理日志表）

| 字段名 | 类型 | 是否为空 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 否 | 自增 | 主键 ID |
| message_id | VARCHAR(128) | 否 | 无 | 幂等键（deviceId + timestamp） |
| topic | VARCHAR(255) | 否 | 无 | MQTT 主题 |
| payload | TEXT | 是 | 无 | 消息原文 |
| status | VARCHAR(32) | 否 | 无 | 状态（RECEIVED / PROCESSED / FAILED / DEAD） |
| reason | VARCHAR(255) | 是 | 无 | 失败原因 |
| created_at | DATETIME | 是 | 当前时间 | 创建时间 |
| updated_at | DATETIME | 是 | 当前时间 | 更新时间 |

**索引**：

- `uk_message_id`（message_id 唯一）
- `idx_status`（status）
- `idx_created_at`（created_at）

---

## 三、表关系说明

### RBAC 关系

```text
sys_user ←→ sys_user_role ←→ sys_role ←→ sys_role_permission ←→ sys_permission
(1) (N) (1) (N) (1)
sys_user 与 sys_role 通过 sys_user_role 形成多对多关系。

sys_role 与 sys_permission 通过 sys_role_permission 形成多对多关系。

数据权限关系
text
sys_user → sys_dept
(N) (1)
sys_user 与 sys_dept 通过 dept_id 形成多对一关系。

审批流关系
text
sys_approval ←→ sys_approval_record
(1) (N)
一个审批单可以有多条操作记录（提交、通过、驳回）。

设备遥测与 TDengine 的关系
text
MQTT → device_telemetry（MySQL，业务全量）
     → telemetry（TDengine，超级表 + 子表，时序存储）
MySQL 保留设备遥测全量数据，主链路，告警依赖。

TDengine 存时序数据（超级表 telemetry，按设备建子表），旁路，用于后续时间范围查询、看板、冷热分离。

两边数据一致（压测 5 万条：49802 = 49802）。

四、通用字段规范
所有业务表统一包含以下字段：

字段名	说明
create_time	创建时间（自动填充）
update_time	更新时间（自动填充）
deleted	逻辑删除标记（0未删 1已删）
例外：

device_telemetry 和 mqtt_message_log 是物联网数据表，不加 deleted 逻辑删除。

report_time 由应用层 MyMetaObjectHandler 填充，不设数据库默认值。

五、角色与权限初始化数据
部署时，直接跑这些 SQL，初始化角色和权限。

sql
USE smart_office;

-- 1. 插入权限
INSERT IGNORE INTO sys_permission (permission_name, permission_code, type) VALUES
('查询用户列表', 'user:list', 'API'),
('删除用户', 'user:delete', 'API'),
('分配角色', 'user:assignRole', 'API'),
('分配部门', 'user:assignDept', 'API'),
('查看修改个人信息', 'user:profile', 'API'),
('提交审批', 'approval:submit', 'API'),
('处理审批', 'approval:handle', 'API');

-- 2. 插入角色
INSERT IGNORE INTO sys_role (role_name, role_code, description) VALUES
('超级管理员', 'SUPER_ADMIN', '拥有所有权限，唯一，不能新增，不能被修改'),
('管理员', 'ADMIN', '拥有所有权限，能管理除超级管理员外的所有用户'),
('副管理员', 'SUB_ADMIN', '有 user:assignRole 权限，但业务逻辑限制只能改员工'),
('普通用户', 'USER', '基础权限，不能管理');

-- 3. 给 SUPER_ADMIN 分配所有权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT (SELECT id FROM sys_role WHERE role_code = 'SUPER_ADMIN'), id FROM sys_permission;

-- 4. 给 ADMIN 分配所有权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT (SELECT id FROM sys_role WHERE role_code = 'ADMIN'), id FROM sys_permission;

-- 5. 给 SUB_ADMIN 分配所有权限（含 user:assignRole）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT (SELECT id FROM sys_role WHERE role_code = 'SUB_ADMIN'), id FROM sys_permission;

-- 6. 给 USER 分配基础权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT (SELECT id FROM sys_role WHERE role_code = 'USER'), id FROM sys_permission
WHERE permission_code IN ('user:profile', 'approval:submit');

-- 7. 插入部门
INSERT IGNORE INTO sys_dept (dept_name, dept_code) VALUES
('研发部', 'DEV'),
('销售部', 'SALES'),
('冲焊车间', 'WELD');
六、建表 SQL（可直接复制到 DBeaver 执行）
sql
CREATE DATABASE IF NOT EXISTS smart_office DEFAULT CHARSET utf8mb4;
USE smart_office;

-- 用户表
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt加密后的密码',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态(1正常 0禁用)',
    dept_id BIGINT COMMENT '部门ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除(0未删 1已删)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(200) COMMENT '角色描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除(0未删 1已删)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 部门表
CREATE TABLE sys_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dept_name VARCHAR(50) NOT NULL COMMENT '部门名称',
    dept_code VARCHAR(50) NOT NULL COMMENT '部门编码',
    parent_id BIGINT DEFAULT 0 COMMENT '上级部门ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id) COMMENT '用户和角色唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 权限表
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    permission_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(50) NOT NULL UNIQUE COMMENT '权限编码',
    type VARCHAR(20) DEFAULT 'API' COMMENT '类型',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色权限关联表
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 审批单主表
CREATE TABLE sys_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title VARCHAR(100) NOT NULL COMMENT '审批标题',
    type VARCHAR(50) NOT NULL COMMENT '审批类型',
    content VARCHAR(500) COMMENT '审批内容',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    approver_id BIGINT NOT NULL COMMENT '当前审批人ID',
    status TINYINT DEFAULT 0 COMMENT '状态(0待审批 1已通过 2已驳回)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批单主表';

-- 审批记录表
CREATE TABLE sys_approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    approval_id BIGINT NOT NULL COMMENT '审批单ID',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    action VARCHAR(20) NOT NULL COMMENT '动作(SUBMIT/APPROVE/REJECT)',
    comment VARCHAR(200) COMMENT '审批意见',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 审计日志表
CREATE TABLE sys_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人用户名',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型',
    target_id BIGINT COMMENT '目标ID',
    target_type VARCHAR(50) COMMENT '目标类型',
    old_value VARCHAR(500) COMMENT '旧值',
    new_value VARCHAR(500) COMMENT '新值',
    ip VARCHAR(50) COMMENT '操作IP',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_operator (operator_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- 设备遥测数据表
CREATE TABLE device_telemetry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_id VARCHAR(50) NOT NULL COMMENT '设备ID',
    temperature DECIMAL(5,2) NOT NULL COMMENT '温度',
    report_time DATETIME COMMENT '上报时间（应用层填充）',
    KEY idx_device_time (device_id, report_time) COMMENT '设备+时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备遥测数据表';

-- MQTT 消息处理日志表
CREATE TABLE mqtt_message_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(128) NOT NULL COMMENT '幂等键',
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT主题',
    payload TEXT COMMENT '消息原文',
    status VARCHAR(32) NOT NULL COMMENT '状态(RECEIVED/PROCESSED/FAILED/DEAD)',
    reason VARCHAR(255) COMMENT '失败原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_message_id (message_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQTT消息处理日志表';
七、Nacos 配置数据库（nacos_config）
Nacos 用独立的数据库 nacos_config 存配置，建表 SQL 见 docs/sql/nacos.sql。

必须用官方完整版，否则会踩坑（见 BUG_LOG 问题 49-53）。

关键表：

config_info：配置内容

his_config_info：配置历史

config_info_aggr：配置聚合

users / roles / permissions：Nacos 用户权限

八、时序库设计（TDengine）
数据库设计
sql
CREATE DATABASE IF NOT EXISTS smart_office
  KEEP 7              -- 数据保留 7 天，超期自动删除（冷热分离）
  DURATION 1          -- 按天分片
  PRECISION 'ms';     -- 毫秒精度
超级表设计
sql
CREATE STABLE IF NOT EXISTS telemetry (
    ts          TIMESTAMP,
    temperature DOUBLE
) TAGS (
    device_id   NCHAR(64)
);
说明：

超级表 telemetry：一张表管所有设备。

子表：按设备自动建表（d_{device_id}），USING ... TAGS 幂等创建。

KEEP 7：数据自动保留 7 天，超期自动清理——最朴素的冷热分离。

主键 (ts, tag)：同一设备同一时间戳，后写覆盖前写。压测脚本用 baseTs + i 全局递增，保证唯一。

与 MySQL 的双写
MySQL 主链路：告警、事务依赖，强一致。

TDengine 旁路：时序存储、时间范围查询，最终一致。

异常隔离：主链路失败整批进死信；旁路失败只记死信，不影响主链路。

写入优化：多子表合并 INSERT（一条 SQL 写多个子表），单批 8~10ms。

九、数据库维护建议
索引优化
sys_user.username：唯一索引（防重）

sys_permission.permission_code：唯一索引（防重）

device_telemetry (device_id, report_time)：组合索引，避免全表扫描

sys_audit_log (operator_id)、sys_audit_log (create_time)：审计查询

mqtt_message_log (message_id)：唯一索引，幂等

逻辑删除
业务表用 deleted 字段做逻辑删除。

注册时查 username 要加 deleted = 0 条件，否则会因唯一索引冲突。

或者，唯一索引改成 (username, deleted) 联合唯一。

字符集
统一 utf8mb4，兼容 emoji。

排序规则 utf8mb4_0900_ai_ci：大小写不敏感。注册时用户名统一转小写。

批量插入优化
JDBC URL 加 rewriteBatchedStatements=true，批量插入速度提升 5-10 倍。

双写一致性
时间戳唯一：TDengine 主键 (ts, tag)，同一设备同一时间戳会覆盖。

reportTime 显式设置：不依赖 MyBatis-Plus 自动填充（只填充 MySQL 库值，内存对象可能为 null）。

压测验证：5 万条，两边数据一致（49802 = 49802）。