# Smart-Office 开发环境清单

**文档版本**：v1.5
**最后更新**：2026-10-09
**对应代码版本**：Smart-Office v1.5

本文档记录开发 Smart-Office 项目所需的所有软件、工具、端口配置和快速换机指南。

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 开发工具、端口 |
| v1.1 | 2026-09-29 | Docker 命令、MQTT |
| v1.2 | 2026-10-04 | Redis/Nacos 命令 |
| v1.3 | 2026-10-05 | TDengine、Prometheus |
| v1.5 | 2026-10-09 | 多模块 + 仿真器 + Git/Gitee + EMQX 参数 + 持久会话 |

---

## 一、开发工具

| 工具 | 用途 |
|------|------|
| IDEA 2025.3 | Java 开发主 IDE |
| DBeaver | 数据库可视化工具 |
| Apifox | HTTP 接口测试 |
| MQTTX | MQTT 客户端，模拟设备发送数据（可选，优先用仿真器） |
| **Git + Gitee** | **代码版本控制（Gitee 用户名 `xiao-hongh`）** |
| Docker Desktop | 容器化运行环境 |
| 浏览器 | 访问 Nacos / Prometheus / Grafana / EMQX Dashboard |

---

## 二、IDEA Maven 配置

- Maven home path: Bundled (Maven 3)
- User settings file: `D:\MavenRepo\...\conf\settings.xml`（勾选 Override）
- Local repository: `D:\IdeaProjects\.m`（勾选 Override）
- settings.xml 已配置阿里云镜像：`https://maven.aliyun.com/repository/public`

**⚠️ 父 pom 必须加 UTF-8 编码（v1.5）**：
```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
否则 YAML 中文注释会乱码，导致配置加载失败。

三、多模块工程结构（v1.5）
text
smart-office (父 pom)
├── smart-office-app          业务模块
├── smart-office-simulation   仿真模块（生产不部署）
└── deploy                    部署编排
打包：

bash
mvn clean package -DskipTests
生成两个 jar：

smart-office-app/target/smart-office-0.0.1-SNAPSHOT.jar

smart-office-simulation/target/smart-office-simulation-0.0.1-SNAPSHOT.jar

四、本地服务端口
服务	端口	说明
后端 SpringBoot（IDEA）	8080	本地开发
后端 SpringBoot（Docker）	8081	容器映射到宿主机
MySQL	3306	数据库
Redis	6379	缓存
RabbitMQ 通信	5672	消息队列
RabbitMQ 管理后台	15672	Web 控制台
EMQX MQTT	1883	设备通信（原生 MQTT 协议）
EMQX WebSocket	8083	WebSocket MQTT（备用）
EMQX 管理后台	18083	Web 控制台
Nacos	8848	配置中心
Nacos gRPC	9848 / 9849	Nacos 2.x 客户端通信
TDengine 原生连接	6030	JDBC 原生（需客户端）
TDengine REST	6041	taosAdapter，REST 连接
Prometheus	9090	监控采集
Grafana	3000	监控可视化
五、容器化环境（Docker Compose）
Docker Desktop 29.8.0

WSL 2.7.14（Windows Subsystem for Linux）

Docker 国内镜像加速已配置

Docker Compose 一键启动，8 个中间件容器 + 1 个可选 app 容器：

容器名	镜像	宿主机端口	容器端口
smart-office-mysql	mysql:8.0	3307	3306
smart-office-redis	redis:latest	6380	6379
smart-office-rabbitmq	rabbitmq:3.13-management	5673 / 15673	5672 / 15672
smart-office-emqx	emqx/emqx:latest	1884 / 18084 / 8084	1883 / 18083 / 8083
smart-office-nacos	nacos/nacos-server:v2.3.2	8848 / 9848 / 9849	8848 / 9848 / 9849
tdengine	tdengine/tdengine:3.3.0.0	6030 / 6041	6030 / 6041
smart-office-app	smart-office-smart-office	8081	8080
smart-office-prometheus	prom/prometheus:latest	9090	9090
smart-office-grafana	grafana/grafana:latest	3000	3000
注意：

开发环境：本地和容器同时跑，容器端口映射到宿主机别的端口，避免冲突。

生产环境：服务器上只有 Docker，容器端口直接映射到标准端口，不冲突。

本地开发：IDEA 跑 SpringBoot，通过 Nacos 拉配置，连本地 MySQL、Redis、RabbitMQ、EMQX、TDengine。

⚠️ 应用实例只能有一个：本地 IDEA 和容器里的 smart-office-app 不要同时跑，会抢 clientId、抢端口。

⚠️ 仿真器只在本地跑：smart-office-simulation 独立部署，生产环境不启动。

六、常用 Docker 命令
基础命令
命令	作用
docker build -t 镜像名:版本 .	构建镜像
docker run -d -p 宿主机端口:容器端口 --name 容器名 镜像名	启动新容器
docker ps	查看运行中的容器
docker ps -a	查看所有容器（含已停止）
docker logs 容器名	查看容器日志
docker rm -f 容器名	强制删除容器
docker images	查看本地镜像列表
docker start 容器名	启动已有容器
docker stop 容器名	停止容器
docker restart 容器名	重启容器
Docker Compose 命令
命令	作用
docker compose up -d --build	重新构建镜像，启动所有容器
docker compose stop <服务名>	只停某个服务（保留其他）
docker compose down	停掉并删除所有容器
docker compose down -v	停掉并删除所有容器 + 数据卷
docker compose ps	查看容器状态
docker compose logs smart-office	查看 SpringBoot 日志（服务名，不是容器名）
docker compose up -d prometheus	只启动 Prometheus 容器
⚠️ 服务名 vs 容器名：docker compose 命令用服务名（如 smart-office），不是容器名（smart-office-app）。

七、MQTT（EMQX）常用操作
7.1 连接 Broker
协议：mqtt://

Host：宿主机真实 IP

Port：1884

用户名 / 密码：admin / admin123456

7.2 发布数据（MQTTX，可选）
Topic：factory/device/001/telemetry

Payload：

json
{
  "deviceId": "001",
  "temperature": 75.5,
  "vibration": 2.0,
  "speed": 1000,
  "timestamp": 1735689600000
}
⚠️ Payload 必须带 timestamp，用于幂等键（deviceId + timestamp）。

7.3 订阅数据
Topic：factory/device/+/telemetry

QoS：1

cleanSession=false

7.4 查看连接状态（EMQX 5.x）
bash
docker exec smart-office-emqx emqx ctl clients list
docker exec smart-office-emqx emqx ctl clients show smart-office-backend
关键字段：

clean_start=false → 持久会话

session_expiry_interval=7200 → 会话保留 2 小时

enqueued_msgs=N → 离线队列积压 N 条

connected=true → app 在线

7.5 EMQX 认证与密码
用户名：admin

密码：admin123456

密码重置（EMQX 5.x 命令已变更）：

bash
docker exec -it smart-office-emqx emqx ctl admins passwd admin admin123456
⚠️ EMQX 5.x 命令是 admins，不是 dashboard。用旧命令会"看起来改了、实际没生效"。

7.6 EMQX 关键参数调优（v1.5）
docker-compose.yml 的 emqx 服务：

yaml
emqx:
  image: emqx/emqx:latest
  container_name: smart-office-emqx
  environment:
    - EMQX_MQTT__MAX_MQUEUE_LEN=100000    # 离线队列 10 万条
  ports:
    - "1884:1883"
    - "18084:18083"
    - "8084:8083"
参数说明：

参数	默认值	建议值	说明
max_mqueue_len	1000	100000	离线队列上限，超出丢弃
max_inflight	32	1000	飞行窗口，QoS 1 在途消息上限
验证配置生效：

bash
docker exec smart-office-emqx env | Select-String "MAX_MQUEUE"
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
9.1 访问 Nacos
地址：http://localhost:8848/nacos

账号密码：nacos / nacos

9.2 常用 API
操作	命令
发布配置	curl.exe -X POST "http://localhost:8848/nacos/v1/cs/configs" -d "dataId=xxx&group=DEFAULT_GROUP&content=xxx&type=yaml"
查询配置	curl.exe "http://localhost:8848/nacos/v1/cs/configs?dataId=xxx&group=DEFAULT_GROUP"
删除配置	curl.exe -X DELETE "http://localhost:8848/nacos/v1/cs/configs?dataId=xxx&group=DEFAULT_GROUP"
9.3 配置 Data ID
smart-office-docker.yml（Docker 环境 / 本地 IDEA 通用）

Group：DEFAULT_GROUP

十、TDengine 常用操作
10.1 访问 TDengine
bash
docker exec -it tdengine taos
10.2 常用 SQL
sql
-- 查总条数
SELECT COUNT(*) FROM smart_office.telemetry;

-- 查某台设备条数
SELECT COUNT(*) FROM smart_office.d_press_01_01;

-- 查最近数据
SELECT * FROM smart_office.telemetry ORDER BY ts DESC LIMIT 5;

-- 查子表列表
USE smart_office;
SHOW TABLES;

-- 查超表
SHOW STABLES;

-- 建超表（清库后必须）
CREATE STABLE IF NOT EXISTS telemetry (
    ts TIMESTAMP, temperature DOUBLE
) TAGS (device_id NCHAR(32));
10.3 连接方式
REST 连接（推荐）：jdbc:TAOS-RS://localhost:6041/smart_office

原生连接（需客户端）：jdbc:TAOS://localhost:6030/smart_office

10.4 清库（重跑前）
bash
docker exec -it tdengine taos -s "DROP DATABASE IF EXISTS smart_office;"
docker exec -it tdengine taos -s "CREATE DATABASE IF NOT EXISTS smart_office KEEP 30;"
docker exec -it tdengine taos -s "USE smart_office; CREATE STABLE IF NOT EXISTS telemetry (ts TIMESTAMP, temperature DOUBLE) TAGS (device_id NCHAR(32));"
⚠️ v1.5 起，app 启动时自动建超表，清库后不用手动建（但 DB 本身要手动建）。

十一、常用监控操作
11.1 Prometheus
地址：http://localhost:9090

查指标：Query 页面，输入 PromQL

看告警：Alerts 页面

看抓取：Status → Targets

11.2 Grafana
地址：http://localhost:3000

账号密码：admin / admin123

数据源：Prometheus（URL: http://smart-office-prometheus:9090）

11.3 SpringBoot 指标端点
Docker：http://localhost:8081/actuator/prometheus

本地：http://localhost:8080/actuator/prometheus

十二、Git 版本管理（v1.5）
12.1 配置（首次）
bash
git config --global user.name "xiao-hongh"
git config --global user.email "2434255475@qq.com"
12.2 SSH key
bash
# 生成
ssh-keygen -t rsa -b 4096 -C "2434255475@qq.com"
# 看公钥
cat ~/.ssh/id_rsa.pub
# 测试
ssh -T git@gitee.com
公钥加到 Gitee： https://gitee.com/profile/sshkeys

12.3 仓库地址
text
git@gitee.com:xiao-hongh/smart-office.git
12.4 日常提交三连
bash
git add .
git commit -m "说明"
git push
12.5 .gitignore 内容
text
target/
*.jar
*.log
.idea/
data/
deploy/data/
*.bak
.env
十三、压测前清理清单
每次压测前，按顺序全清，避免数据对不上：

MySQL：TRUNCATE TABLE device_telemetry; 和 TRUNCATE TABLE mqtt_message_log;

TDengine：DROP DATABASE IF EXISTS smart_office; 然后重建（建库 SQL 见 DEPLOYMENT.md）

Redis 幂等 key：SCAN 0 MATCH mqtt:idempotent:* COUNT 100 然后 DEL

EMQX 会话：EMQX Dashboard 删除 smart-office-backend，或 docker restart smart-office-emqx

SpringBoot：重启项目，清空内存队列和批量缓冲区

只清数据库不够，EMQX 的离线会话会补发消息，导致数据比预期多。TDengine 也要一起清。

十四、快速换机指南
安装 JDK 17 + Maven + Docker Desktop + Git。

配置 Git 身份 + SSH key。

拉取项目代码（git clone git@gitee.com:xiao-hongh/smart-office.git），导入 IDEA。

mvn clean package -DskipTests，打包。

cd deploy && docker compose up -d，启动中间件（8 个容器）。

验证：docker compose ps，8 个中间件都 Up。

访问 Nacos（http://localhost:8848/nacos），确认配置 smart-office-docker.yml 存在。

配置 EMQX 关键参数（max_mqueue_len=100000）。

初始化 TDengine 库表（建库建表 SQL 见 DEPLOYMENT.md）。

访问 Prometheus（http://localhost:9090），确认 smart-office 目标 UP。

本地跑 app：java -jar smart-office-app\target\smart-office-0.0.1-SNAPSHOT.jar

本地跑仿真器（100 台）：java -jar smart-office-simulation\target\smart-office-simulation-0.0.1-SNAPSHOT.jar

如需测试 MQTT，用 MQTTX 连 mqtt://宿主机IP:1884，Payload 必须带 timestamp。