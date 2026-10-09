# Smart-Office 开发环境清单

本文档记录开发 Smart-Office 项目所需的所有软件、工具、端口配置和快速换机指南。

---

## 一、开发工具

| 工具 | 用途 |
|------|------|
| IDEA 2025.3 | Java 开发主 IDE |
| DBeaver | 数据库可视化工具 |
| Apifox | HTTP 接口测试 |
| MQTTX | MQTT 客户端，模拟设备发送数据 |
| Git + GitHub | 代码版本控制 |
| Docker Desktop | 容器化运行环境 |
| 浏览器 | 访问 Nacos / Prometheus / Grafana / **EMQX Dashboard** |

---

## 二、IDEA Maven 配置

- Maven home path: Bundled (Maven 3)
- User settings file: `D:\MavenRepo\...\conf\settings.xml`（勾选 Override）
- Local repository: `D:\IdeaProjects\.m`（勾选 Override）
- settings.xml 已配置阿里云镜像：`https://maven.aliyun.com/repository/public`

---

## 三、本地服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 SpringBoot（IDEA） | 8080 | 本地开发 |
| 后端 SpringBoot（Docker） | 8081 | 容器映射到宿主机 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| RabbitMQ 通信 | 5672 | 消息队列 |
| RabbitMQ 管理后台 | 15672 | Web 控制台 |
| EMQX MQTT | 1883 | 设备通信（原生 MQTT 协议） |
| EMQX WebSocket | 8083 | WebSocket MQTT（备用） |
| EMQX 管理后台 | 18083 | Web 控制台 |
| Nacos | 8848 | 配置中心 |
| Nacos gRPC | 9848 / 9849 | Nacos 2.x 客户端通信 |
| **TDengine 原生连接** | **6030** | **JDBC 原生（需客户端）** |
| **TDengine REST** | **6041** | **taosAdapter，REST 连接** |
| Prometheus | 9090 | 监控采集 |
| Grafana | 3000 | 监控可视化 |

---

## 四、容器化环境（Docker Compose）

- Docker Desktop 29.8.0
- WSL 2.7.14（Windows Subsystem for Linux）
- Docker 国内镜像加速已配置

Docker Compose 一键启动，**9 个**容器：

| 容器名 | 镜像 | 宿主机端口 | 容器端口 |
|--------|------|------------|----------|
| smart-office-mysql | mysql:8.0 | 3307 | 3306 |
| smart-office-redis | redis:latest | 6380 | 6379 |
| smart-office-rabbitmq | rabbitmq:3.13-management | 5673 / 15673 | 5672 / 15672 |
| smart-office-emqx | emqx/emqx:latest | 1884 / 18084 / 8084 | 1883 / 18083 / 8083 |
| smart-office-nacos | nacos/nacos-server:v2.3.2 | 8848 / 9848 / 9849 | 8848 / 9848 / 9849 |
| **tdengine** | **tdengine/tdengine:3.3.0.0** | **6030 / 6041** | **6030 / 6041** |
| smart-office-app | smart-office-smart-office | 8081 | 8080 |
| smart-office-prometheus | prom/prometheus:latest | 9090 | 9090 |
| smart-office-grafana | grafana/grafana:latest | 3000 | 3000 |

**注意**：

- 开发环境：本地和容器同时跑，容器端口映射到宿主机别的端口，避免冲突。
- 生产环境：服务器上只有 Docker，容器端口直接映射到标准端口，不冲突。
- 本地开发：IDEA 跑 SpringBoot，通过 Nacos 拉配置，连本地 MySQL、Redis、RabbitMQ、EMQX、**TDengine**。
- **⚠️ 应用实例只能有一个**：本地 IDEA 和容器里的 `smart-office-app` 不要同时跑，会抢 clientId、抢端口。

---

## 五、常用 Docker 命令

| 命令 | 作用 |
|------|------|
| docker build -t 镜像名:版本 . | 构建镜像 |
| docker run -d -p 宿主机端口:容器端口 --name 容器名 镜像名 | 启动新容器 |
| docker ps | 查看运行中的容器 |
| docker ps -a | 查看所有容器（含已停止） |
| docker logs 容器名 | 查看容器日志 |
| docker rm -f 容器名 | 强制删除容器 |
| docker images | 查看本地镜像列表 |
| docker start 容器名 | 启动已有容器 |
| docker stop 容器名 | 停止容器 |
| docker restart 容器名 | 重启容器 |

### Docker Compose 命令

| 命令 | 作用 |
|------|------|
| docker compose up -d --build | 重新构建镜像，启动所有容器 |
| **docker compose stop <服务名>** | **只停某个服务（保留其他）** |
| docker compose down | 停掉并删除所有容器 |
| docker compose down -v | 停掉并删除所有容器 + 数据卷 |
| docker compose ps | 查看容器状态 |
| docker compose logs smart-office | 查看 SpringBoot 日志（**服务名**，不是容器名） |
| docker compose up -d prometheus | 只启动 Prometheus 容器 |

**⚠️ 服务名 vs 容器名**：`docker compose` 命令用**服务名**（如 `smart-office`），不是容器名（`smart-office-app`）。敲错报 `no such service`。

---

## 六、常用 MQTT 命令

| 操作 | 方法 |
|------|------|
| 连接 Broker | MQTTX 里选 `mqtt://宿主机IP:1884`，用户名密码为 EMQX 的 `admin` / `admin123456` |
| 发布数据 | Topic: `factory/device/001/temperature`，Payload: `{"deviceId":"001","temperature":75.5,"timestamp":1735689600000}` |
| 订阅数据 | Topic: `factory/device/+/temperature`，QoS 1 |
| 查看连接 | EMQX 管理后台 → 客户端 |
| 查看主题 | EMQX 管理后台 → 主题 |
| 查看丢弃 | EMQX 管理后台 → 丢弃分析（判断丢没丢的权威指标） |

**Payload 必须带 timestamp，用于幂等键（deviceId + timestamp）。**

---

## 七、EMQX 认证与参数配置

### 认证配置

EMQX 默认开了认证。本项目用的账号：

| 项 | 值 |
|----|----|
| 用户名 | admin |
| 密码 | admin123456 |

- SpringBoot 侧：配置从 Nacos 拉取，`mqtt.username` 和 `mqtt.password`。
- MQTTX 侧：连接时填同样的用户名密码。

**密码重置**（EMQX 5.x 命令已变更）：

```bash
docker exec -it smart-office-emqx emqx ctl admins passwd admin admin123456
⚠️ 注意：EMQX 5.x 的 Dashboard 用户管理命令是 admins（add / passwd / del / mfa），不是 dashboard。用旧命令会"看起来改了、实际没生效"。

关键参数调优
压测 50000 条时，如果 EMQX 报"无订阅者"丢弃，调大这两个参数：

参数	默认值	建议值	说明
max_mqueue_len	1000	100000	离线队列上限
max_inflight	32	1000	飞行窗口，QoS 1 在途消息上限
修改方式：EMQX Dashboard → 管理 → 配置 → MQTT。

八、常用 Redis 命令
命令	作用
redis-cli -h 宿主机IP -p 6380 -a 123456	连接容器 Redis（宿主机端口 6380）
redis-cli -h 宿主机IP -p 6379 -a 123456	连接本地 Redis（Windows 服务）
ping	检测 Redis 是否存活
KEYS user:permissions:*	查看权限缓存 Key
GET user:permissions:123	查看指定用户的权限
SCAN 0 MATCH mqtt:idempotent:* COUNT 100	查看 MQTT 幂等 key（比 KEYS 安全）
FLUSHDB	清空当前库（压测前清空幂等 key）
九、Nacos 常用操作
访问 Nacos
地址：http://localhost:8848/nacos

账号密码：nacos / nacos

常用 API
操作	命令
发布配置	curl.exe -X POST "http://localhost:8848/nacos/v1/cs/configs" -d "dataId=xxx&group=DEFAULT_GROUP&content=xxx&type=yaml"
查询配置	curl.exe "http://localhost:8848/nacos/v1/cs/configs?dataId=xxx&group=DEFAULT_GROUP"
删除配置	curl.exe -X DELETE "http://localhost:8848/nacos/v1/cs/configs?dataId=xxx&group=DEFAULT_GROUP"
配置 Data ID
smart-office-docker.yml（Docker 环境 / 本地 IDEA 通用）

Group：DEFAULT_GROUP

十、TDengine 常用操作
访问 TDengine
进容器：

bash
docker exec -it tdengine taos
常用 SQL
sql
-- 查总条数
SELECT COUNT(*) FROM smart_office.telemetry;

-- 查某台设备条数
SELECT COUNT(*) FROM smart_office.d_001;

-- 查最近数据
SELECT * FROM smart_office.telemetry ORDER BY ts DESC LIMIT 5;

-- 查子表数量
SHOW smart_office.tables;
连接方式
REST 连接（推荐，容器化场景）：jdbc:TAOS-RS://localhost:6041/smart_office

原生连接（需本地客户端）：jdbc:TAOS://localhost:6030/smart_office

十一、常用监控操作
Prometheus
地址：http://localhost:9090

查指标：Query 页面，输入 PromQL

看告警：Alerts 页面

看抓取：Status → Targets

Grafana
地址：http://localhost:3000

账号密码：admin / admin123

数据源：Prometheus（URL: http://smart-office-prometheus:9090）

SpringBoot 指标端点
Docker：http://localhost:8081/actuator/prometheus

本地：http://localhost:8080/actuator/prometheus

十二、压测前清理清单
每次压测前，按顺序全清，避免数据对不上：

数据库：TRUNCATE TABLE device_telemetry; 和 TRUNCATE TABLE mqtt_message_log;

TDengine：DROP DATABASE IF EXISTS smart_office; 然后重建（建库建表 SQL 见 DEPLOYMENT.md）

Redis 幂等 key：redis-cli -h 宿主机IP -p 6380 -a 123456 SCAN 0 MATCH mqtt:idempotent:* COUNT 100 | xargs redis-cli DEL

EMQX 会话：EMQX Dashboard 删除 smart-office-telemetry-subscriber，或 docker restart smart-office-emqx

SpringBoot：重启项目，清空内存队列和批量缓冲区

只清数据库不够，EMQX 的离线会话会补发消息，导致数据比预期多。TDengine 也要一起清。

十三、快速换机指南
安装 JDK 17 + Maven + Docker Desktop。

拉取项目代码，导入 IDEA。

执行 mvn clean package -DskipTests，打包。

执行 docker compose up -d --build，一键启动所有服务。

验证：docker compose ps，9 个容器都 Up。

访问 Nacos（http://localhost:8848/nacos），确认配置 smart-office-docker.yml 存在。

配置 EMQX 关键参数（max_mqueue_len、max_inflight）。

初始化 TDengine 库表（建库建表 SQL 见 DEPLOYMENT.md 第七节）。

访问 Prometheus（http://localhost:9090），确认 smart-office 目标 UP。

如需本地开发，改 application.yml 连 Nacos，通过 Nacos 拉配置。

如需测试 MQTT，用 MQTTX 连 mqtt://宿主机IP:1884，Payload 必须带 timestamp。