# Smart-Office 开发环境清单

文档版本：v1.8
最后更新：2026-10-17
对应代码版本：Smart-Office v1.8

本文档记录开发 Smart-Office 项目所需的所有软件、工具、端口配置和快速换机指南。

---

## 一、开发工具

| 工具 | 用途 |
|------|------|
| IDEA 2025.3 | Java 开发主 IDE |
| DBeaver | 数据库可视化工具 |
| Apifox | HTTP 接口测试 |
| MQTTX | MQTT 客户端，模拟设备发送数据（可选，优先用仿真器） |
| Git + Gitee/GitHub | 代码版本控制 |
| Docker Desktop | 容器化运行环境 |
| 浏览器 | 访问 Nacos / Prometheus / Grafana / EMQX Dashboard |
| DeepSeek API | AI Agent 的 LLM 调用（已接入真 API） |
| JUnit 5 | AI Agent 单元测试（spring-boot-starter-test 已含） |

---

## 二、IDEA Maven 配置

- Maven home path: Bundled (Maven 3)
- User settings file: D:\MavenRepo\...\conf\settings.xml（勾选 Override）
- Local repository: D:\IdeaProjects\.m（勾选 Override）
- settings.xml 已配置阿里云镜像：https://maven.aliyun.com/repository/public

父 pom 必须加 UTF-8 编码（v1.5 新增）：

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
```
否则 YAML 中文注释会乱码，导致配置加载失败。

三、多模块工程结构
text
smart-office (父 pom)
├── smart-office-app          业务模块
├── smart-office-simulation   仿真模块（生产不部署）
└── deploy                    部署编排
打包命令：
mvn clean package -DskipTests

生成两个 jar：

smart-office-app/target/smart-office-0.0.1-SNAPSHOT.jar

smart-office-simulation/target/smart-office-simulation-0.0.1-SNAPSHOT.jar

四、本地服务端口（默认配置）
服务	端口	说明
后端 SpringBoot（IDEA）	8080	本地开发
后端 SpringBoot（Docker）	8081	容器映射到宿主机
MySQL	3306	数据库
Redis	6379	缓存
RabbitMQ 通信	5672	消息队列
RabbitMQ 管理后台	15672	Web 控制台
EMQX MQTT	1883	设备通信（原生 MQTT 协议）
EMQX WebSocket	8083	WebSocket MQTT
EMQX 管理后台	18083	Web 控制台
Nacos	8848	配置中心
Nacos gRPC	9848 / 9849	Nacos 2.x 客户端通信
TDengine 原生连接	6030	JDBC 原生（需客户端）
TDengine REST	6041	taosAdapter，REST 连接
Prometheus	9090	监控采集
Grafana	3000	监控可视化
五、容器化环境（Docker Compose）
Docker Compose 一键启动，8 个中间件容器 + 1 个可选 app 容器。
注意：为了防宿主机端口冲突，映射到了自定义端口。

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

生产环境：服务器上只有 Docker，容器端口直接映射到标准端口。

本地开发：IDEA 跑 SpringBoot，通过 Nacos 拉配置，连本地中间件。

应用实例只能有一个：本地 IDEA 和容器里的 smart-office-app 不要同时跑，会抢 clientId、抢端口。

仿真器只在本地跑。

六、常用 Docker 命令
基础命令
docker build -t 镜像名:版本 .

docker run -d -p 宿主机端口:容器端口 --name 容器名 镜像名

docker ps (查看运行中的容器)

docker ps -a (查看所有容器)

docker logs 容器名

docker rm -f 容器名

docker images

docker start 容器名

docker stop 容器名

docker restart 容器名

Docker Compose 命令
docker compose up -d --build (重新构建镜像，启动所有)

docker compose stop 服务名 (只停某个服务)

docker compose down (停掉并删除所有)

docker compose down -v (停掉并删除所有 + 数据卷)

docker compose ps

docker compose logs smart-office

docker compose up -d prometheus (只启动 Prometheus)

注意：docker compose 命令用服务名（如 smart-office），不是容器名（smart-office-app）。

七、MQTT（EMQX）常用操作
7.1 连接 Broker
协议：mqtt://

Host：宿主机真实 IP

Port：1884

用户名 / 密码：admin / admin123456 (参考 EMQX 实际配置)

7.2 发布数据（MQTTX，可选）
Topic：factory/device/001/telemetry

Payload 格式：

json
{
  "deviceId": "001",
  "temperature": 75.5,
  "vibration": 2.0,
  "speed": 1000,
  "timestamp": 1735689600000
}
注意：Payload 必须带 timestamp，用于幂等键（deviceId + timestamp）。

7.3 订阅数据
Topic：factory/device/+/telemetry

QoS：1

cleanSession=false

7.4 查看连接状态（EMQX 5.x）
bash
docker exec smart-office-emqx emqx ctl clients list
docker exec smart-office-emqx emqx ctl clients show smart-office-backend
关键字段：

clean_start=false (持久会话)

session_expiry_interval=7200 (会话保留 2 小时)

enqueued_msgs=N (离线队列积压 N 条)

connected=true (app 在线)

7.5 EMQX 认证与密码
密码重置（EMQX 5.x 命令已变更）：

bash
docker exec -it smart-office-emqx emqx ctl admins passwd admin admin123456
注意：EMQX 5.x 命令是 admins，不是 dashboard。

7.6 EMQX 关键参数调优
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

max_mqueue_len：默认 1000，建议 100000（离线队列上限，超出丢弃）

max_inflight：默认 32，建议 1000（飞行窗口，QoS 1 在途消息上限）

八、常用 Redis 命令
连接容器 Redis：redis-cli -h 宿主机IP -p 6380 -a 123456

连接本地 Redis：redis-cli -h 宿主机IP -p 6379 -a 123456

ping (检测存活)

KEYS user:permissions:* (查看权限缓存)

GET user:permissions:123

SCAN 0 MATCH mqtt:idempotent:* COUNT 100 (查看幂等 key，比 KEYS 安全)

FLUSHDB (清空当前库)

九、Nacos 常用操作
9.1 访问 Nacos
地址：http://localhost:8848/nacos

账号密码：nacos / nacos

9.2 配置 Data ID
smart-office-docker.yml（通用）

Group：DEFAULT_GROUP

十、TDengine 常用操作
10.1 访问 TDengine
docker exec -it tdengine taos

10.2 常用 SQL
sql
-- 查总条数
SELECT COUNT(*) FROM smart_office.telemetry;

-- 查某台设备条数
SELECT COUNT(*) FROM smart_office.d_press_01_01;

-- 查最近数据
SELECT * FROM smart_office.telemetry ORDER BY ts DESC LIMIT 5;

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
注意：app 启动时自动建超表，清库后不用手动建（但 DB 本身要手动建）。

十一、常用监控操作
11.1 Prometheus
地址：http://localhost:9090

看抓取：Status -> Targets

11.2 Grafana
地址：http://localhost:3000

账号密码：admin / admin123

十二、Git 版本管理
12.1 配置（首次）
bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
12.2 日常提交
bash
git add .
git commit -m "说明"
git push (Gitee) / git push github (GitHub)
12.3 .gitignore 内容
text
target/
*.jar
*.log
.idea/
data/
deploy/data/
*.bak
.env
十三、AI Agent 配置与验证
13.1 环境变量配置（v1.7 接入 DeepSeek）
IDEA 本地运行：Run -> Edit Configurations -> Environment variables：

text
AI_LLM_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-你的真实Key
Docker 容器运行：在 docker-compose.yml 中注入。
注意：API Key 绝对不要硬编码进代码或 Nacos 配置，用环境变量读取。

13.2 githooks 部署（防泄露）
新 clone 项目后，必须执行：

bash
git config core.hooksPath githooks
13.3 AI Agent 验证（跑通检查）
单元测试：跑 RuleEngineTest，期望 5 tests passed。

端到端：仿真层注入 inject WELD-02-01 OVERHEAT，查 ai_analysis_log 表。

数据对账：ai_analysis_log 总数 - alert_record 总数 = WARNING 数量。

十四、压测前清理清单
每次压测前，按顺序全清，避免数据对不上：

停服务：停 IDEA app 和仿真器。

清 MySQL：TRUNCATE TABLE device_telemetry; TRUNCATE TABLE ai_analysis_log; TRUNCATE TABLE alert_record; TRUNCATE TABLE mqtt_message_log;

清 TDengine：DROP DATABASE IF EXISTS smart_office; 然后重建。

清 Redis：FLUSHDB (清空幂等 key 和去重 key)。

清 EMQX：EMQX Dashboard 删除僵尸会话，或 docker restart smart-office-emqx。

十五、快速换机指南
安装 JDK 17 + Maven + Docker Desktop + Git。

配置 Git 身份 + SSH key。

拉取代码，导入 IDEA。

mvn clean package -DskipTests，打包。

cd deploy && docker compose up -d，启动中间件。

配置 githooks：git config core.hooksPath githooks

访问 Nacos，确认配置 smart-office-docker.yml 存在。

配置 IDEA 环境变量 (DEEPSEEK_API_KEY)。

本地跑 app 和仿真器。