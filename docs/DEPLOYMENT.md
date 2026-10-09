# Smart-Office 部署与运维手册

本文档记录 Smart-Office 项目从本地运行到 Docker Compose 一键部署的完整操作流程和故障排查指南。

---

## 一、环境要求

| 软件 | 版本 | 端口 |
|------|------|------|
| JDK | 17 | - |
| Maven | 3.9+ | - |
| MySQL | 8.0 | 3306 |
| Redis | 5.0+ | 6379 |
| RabbitMQ | 3.13.7 | 5672 / 15672 |
| Erlang | 26.x | - |
| EMQX | latest | 1883 / 18083 / 8083 |
| Nacos | 2.3.2 | 8848 / 9848 / 9849 |
| **TDengine** | **3.3.0.0** | **6030 / 6041** |
| Prometheus | latest | 9090 |
| Grafana | latest | 3000 |
| Docker Desktop | 29.8.0 | - |

---

## 二、本地启动步骤

1. 启动 MySQL 服务，确保 `smart_office` 数据库已创建。
2. 启动 Redis 服务（`net start Redis`）。
3. 启动 RabbitMQ 服务（`net start RabbitMQ`）。
4. 启动 EMQX 容器（`docker start smart-office-emqx`）。
5. 启动 Nacos 容器（`docker start smart-office-nacos`）。
6. **启动 TDengine 容器（`docker start tdengine`）。**
7. 运行 `SmartOfficeApplication` 主类。

**启动顺序建议**：先启动所有依赖（MySQL、Redis、RabbitMQ、EMQX、Nacos、TDengine），再启动 SpringBoot。避免"项目启动 ≠ 依赖就绪"导致的连接失败。

**⚠️ 本地开发建议**：中间件全部用 Docker 起（`docker compose up -d`），IDEA 只跑 SpringBoot。**不要同时启动容器里的 smart-office-app 和 IDEA 里的 SpringBoot**，会抢 clientId、抢端口。

---

## 三、配置文件说明（Nacos + application.yml）

### 本地 application.yml（最小配置）

```yaml
spring:
  application:
    name: smart-office
  profiles:
    active: docker
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
        namespace: public
        group: DEFAULT_GROUP
Nacos 里的配置（smart-office-docker.yml）
用环境变量占位，本地和 Docker 通用：

yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3307}/smart_office?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&rewriteBatchedStatements=true
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6380}
      password: 123456

  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5673}
    username: admin
    password: 123456

jwt:
  secret: smart-office-jwt-secret-key-2026
  expiration: 1800000

mqtt:
  broker: tcp://${MQTT_HOST:localhost}:${MQTT_PORT:1884}
  client-id: ${MQTT_CLIENT_ID:smart-office-telemetry-subscriber-local}
  username: admin
  password: admin123456

tdengine:
  url: jdbc:TAOS-RS://${TDENGINE_HOST:localhost}:${TDENGINE_PORT:6041}/smart_office
  username: root
  password: taosdata
  pool:
    max-size: 10

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: smart-office
关键配置：

rewriteBatchedStatements=true：让 MySQL JDBC 把多条 INSERT 合并成一条，批量插入速度提升 5-10 倍。

环境变量占位 ${MYSQL_HOST:localhost}：本地跑用 localhost，Docker 跑用环境变量覆盖。

tdengine.url：REST 连接（jdbc:TAOS-RS://），不依赖本地 TDengine 客户端。

四、Docker Compose 一键部署（推荐）
1. 打包
bash
mvn clean package -DskipTests
2. 启动
bash
docker compose up -d --build
这会：

构建 smart-office 镜像。

启动 9 个容器：MySQL、Redis、RabbitMQ、EMQX、Nacos、TDengine、SpringBoot、Prometheus、Grafana。

MySQL 第一次启动时，自动执行 docs/sql/ 下的 SQL，导入数据。

3. 验证容器状态
bash
docker compose ps
9 个容器，都 Up：

容器名	镜像	宿主机端口
smart-office-mysql	mysql:8.0	3307
smart-office-redis	redis:latest	6380
smart-office-rabbitmq	rabbitmq:3.13-management	5673 / 15673
smart-office-emqx	emqx/emqx:latest	1884 / 18084 / 8084
smart-office-nacos	nacos/nacos-server:v2.3.2	8848 / 9848 / 9849
tdengine	tdengine/tdengine:3.3.0.0	6030 / 6041
smart-office-app	smart-office-smart-office	8081
smart-office-prometheus	prom/prometheus:latest	9090
smart-office-grafana	grafana/grafana:latest	3000
4. 查看日志
bash
docker logs smart-office-app --tail 50
出现 Started SmartOfficeApplication 即为启动成功。

5. 接口测试
text
POST http://localhost:8081/api/user/login
Body: {"username": "admin1", "password": "123456"}
返回 Token，说明接口正常。

6. 常用 Docker Compose 命令
命令	作用
docker compose up -d --build	重新构建镜像，启动所有容器
docker compose stop smart-office	只停 smart-office 服务（保留中间件）
docker compose down	停掉并删除所有容器
docker compose down -v	停掉并删除所有容器 + 数据卷
docker compose ps	查看容器状态
docker compose logs smart-office	查看 SpringBoot 日志
⚠️ 服务名 vs 容器名：docker compose stop <服务名> 用服务名（如 smart-office），不是容器名（smart-office-app）。敲错报 no such service。

五、Docker Compose 环境变量覆盖
在 docker-compose.yml 里，smart-office 服务，用 environment 覆盖配置：

yaml
smart-office:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - MYSQL_HOST=mysql
    - MYSQL_PORT=3306
    - REDIS_HOST=redis
    - REDIS_PORT=6379
    - RABBITMQ_HOST=rabbitmq
    - RABBITMQ_PORT=5672
    - MQTT_HOST=emqx
    - MQTT_PORT=1883
    - MQTT_CLIENT_ID=smart-office-telemetry-subscriber-docker
    - TDENGINE_HOST=tdengine
    - TDENGINE_PORT=6041
    - SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos:8848
关键：环境变量的优先级，比 application.yml 高。SpringBoot 的"宽松绑定"：

MYSQL_HOST → ${MYSQL_HOST:localhost}

REDIS_HOST → ${REDIS_HOST:localhost}

TDENGINE_HOST → ${TDENGINE_HOST:localhost}

SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR → Nacos 地址

注意：Nacos 配置本身也用了环境变量占位，所以环境变量会穿透 Nacos 配置，覆盖其中的占位符。

六、Nacos 部署
1. Nacos 容器
Docker Compose 启动的 Nacos，容器名 smart-office-nacos，端口映射：

宿主机 8848 → 容器 8848（HTTP）

宿主机 9848 → 容器 9848（gRPC）

宿主机 9849 → 容器 9849（gRPC）

⚠️ Nacos 启动慢（30~60 秒），日志出现 Nacos started successfully 才算就绪，之后再启动 SpringBoot。

2. Nacos 管理后台
浏览器访问 http://localhost:8848/nacos。

账号：nacos

密码：nacos

3. Nacos 连 MySQL
Nacos 用 MySQL 存配置，数据库 nacos_config 由 docs/sql/nacos.sql 初始化。

建表 SQL 必须用官方完整版（精简版会踩坑，见 BUG_LOG 问题 49-53）。

4. 配置 Data ID
smart-office-docker.yml（本地和 Docker 通用）

Group：DEFAULT_GROUP

5. 动态刷新验证
改 Nacos 里的配置，观察 SpringBoot 日志，应出现：

text
[Nacos Config] Listening config: dataId=smart-office-docker.yml, group=DEFAULT_GROUP
Refresh keys changed: [xxx]
七、TDengine 部署
1. TDengine 容器
Docker Compose 启动的 TDengine，容器名 tdengine，端口映射：

宿主机 6030 → 容器 6030（原生连接）

宿主机 6041 → 容器 6041（REST/taosAdapter）

2. 建库建表
进容器：

bash
docker exec -it tdengine taos
建库建表：

sql
CREATE DATABASE IF NOT EXISTS smart_office
  KEEP 7
  DURATION 1
  PRECISION 'ms';

USE smart_office;

CREATE STABLE IF NOT EXISTS telemetry (
    ts          TIMESTAMP,
    temperature DOUBLE
) TAGS (
    device_id   NCHAR(64)
);
3. 验证
bash
docker exec -it tdengine taos -s "SELECT COUNT(*) FROM smart_office.telemetry;"
4. 连接方式选择
本地 IDEA 跑：用 REST 连接 jdbc:TAOS-RS://localhost:6041/smart_office，不依赖本地客户端。

容器里跑：用 REST 连接 jdbc:TAOS-RS://tdengine:6041/smart_office。

原生连接（jdbc:TAOS://）需要本地装 TDengine 客户端（taos.dll/libtaos.so），容器化场景不推荐。

八、MQTT（EMQX）部署
1. EMQX 容器
Docker Compose 启动的 EMQX，容器名 smart-office-emqx，端口映射：

宿主机 1884 → 容器 1883

宿主机 18084 → 容器 18083

宿主机 8084 → 容器 8083

2. EMQX 管理后台
浏览器访问 http://localhost:18084。

账号：admin

密码：admin123456

密码重置（EMQX 5.x 命令已变更）：

bash
docker exec -it smart-office-emqx emqx ctl admins passwd admin admin123456
⚠️ 注意：EMQX 5.x 的 Dashboard 用户管理命令是 admins（add / passwd / del / mfa），不是 dashboard。用旧命令会"看起来改了、实际没生效"。

3. EMQX 关键参数调优
压测 50000 条时，如果 EMQX 报"无订阅者"丢弃，需要调大这两个参数：

参数	默认值	建议值	说明
max_mqueue_len	1000	100000	离线队列上限，超出丢弃
max_inflight	32	1000	飞行窗口，QoS 1 在途消息上限
修改方式：EMQX Dashboard → 管理 → 配置 → MQTT。

4. 用 MQTTX 模拟设备发送数据
协议：mqtt://

Host：宿主机真实IP

Port：1884

用户名 / 密码：EMQX 的 admin / admin123456

Topic：factory/device/001/temperature

Payload（必须带 timestamp）：

json
{"deviceId":"001","temperature":75.5,"timestamp":1735689600000}
5. 验证 MQTT 链路
观察 docker logs smart-office-app --tail 20，应打印：

text
批量插入 X 条，耗时 Yms
TDengine 实际写入 X 条（Y 个设备），耗时 Zms
去 DBeaver 查 device_telemetry 表，应有新记录，且 report_time 有值。
去 TDengine 查 SELECT * FROM smart_office.telemetry LIMIT 5;，应有对应数据。

九、监控体系部署
1. Prometheus
浏览器访问 http://localhost:9090。

Query：查指标

Alerts：看告警规则

Status → Targets：看抓取目标

⚠️ 本地 IDEA 跑 SpringBoot 时，Prometheus 抓取目标要改。容器内 prometheus.yml 里写的是 smart-office:8080（服务名），本地跑时该服务名解析不了。改用 host.docker.internal:8080。

2. Grafana
浏览器访问 http://localhost:3000。

账号：admin

密码：admin123

配置数据源：

URL：http://smart-office-prometheus:9090

类型：Prometheus

3. 告警规则
告警规则文件 prometheus-alert.yml，包含 3 条：

HTTP 5xx 错误率 > 1%，持续 1 分钟

JVM 堆内存 > 85%，持续 5 分钟

服务下线，持续 1 分钟

十、常见问题排查
问题	原因	解决方案
8080 端口被占用	宿主机 8080 被 IDEA 占用	改用 -p 8081:8080
端口冲突	本地和容器端口冲突	容器端口映射到宿主机别的端口
容器内连不上 MySQL	容器 localhost ≠ 宿主机	用服务名 mysql，或环境变量覆盖
Nacos 启动报表不存在	建表 SQL 用了精简版	用官方完整版 mysql-schema.sql
SpringBoot 配置拉不到	没加 spring-cloud-starter-bootstrap	pom.xml 加 bootstrap 依赖
Nacos 配置覆盖环境变量	Nacos 配置优先级高于环境变量	用环境变量占位 ${VAR:default}
本地和 Docker clientId 冲突	同一 clientId 互相挤掉	本地加 -local 后缀
动态刷新后旧连接不断	没配 @PreDestroy	加 @PreDestroy 方法
Prometheus 抓不到 SpringBoot	拦截器拦了 /actuator	WebConfig 放行 /actuator/**
Redisson 读旧 IP	spring.redis 和 spring.data.redis 不一致	两个都配
JDK 17 Cgroup 报错	openjdk:17-jdk-slim 不兼容	换 eclipse-temurin，或加 -XX:-UseContainerSupport
MySQL ASCII \0	客户端编码 latin1	mysqldump 加 --default-character-set=utf8mb4
MySQL 初始化不执行 SQL	数据卷已存在	docker compose down -v 删数据卷
YAML 中文乱码	文件编码 GBK	改成 UTF-8
RabbitMQ NOT_ALLOWED	新用户无 vhost 权限	管理后台 Admin → Users → Set permission
Redis Connection refused	bind 只监听 127.0.0.1	修改 bind 0.0.0.0，protected-mode no
MQTTX 报 socket hang up	协议前缀选错	用 mqtt://，不要用 ws://
EMQX "无订阅者"丢弃	订阅端掉线瞬间	调大 max_mqueue_len 和 max_inflight
report_time 全是 NULL	没配 MetaObjectHandler	新建 MyMetaObjectHandler
MQTT 压测少 499 条	脚本 disconnect 太早	脚本用 deliveryComplete 等 PUBACK
TDengine 报 no taos	用原生连接但本地无客户端	改 REST：jdbc:TAOS-RS://
TDengine 双写少数据	reportTime 为 null，静默丢	显式 setReportTime
TDengine 多子表 INSERT 报错	语法错误，每个子表都带 INSERT INTO	只有第一个带，后续直接写子表名
Nacos 未就绪启动失败	Nacos 启动 30~60 秒，SpringBoot 抢跑	等 Nacos 日志就绪再启 SpringBoot
容器全停应用连不上	docker compose down 停了全部	docker compose stop <服务名> 只停单个
EMQX Dashboard 密码改了没生效	命令版本差异	用 emqx ctl admins passwd
十一、安全配置清单
数据库
MySQL 8.0：使用 mysql_native_password 兼容旧驱动。

生产环境：不使用 root@%，创建最小权限用户。

端口 3306 不开放公网。

Redis
bind 0.0.0.0，protected-mode no（仅限开发环境）。

设置复杂密码（开发环境可用 123456，生产环境必须 ≥16 位）。

端口 6379 不开放公网。

RabbitMQ
创建独立用户，设置 vhost 权限。

端口 5672 / 15672 不开放公网。

EMQX（MQTT）
修改默认密码，不使用 admin / public。

生产环境使用强密码（≥16 位）。

端口 1883 / 18083 不开放公网。

Nacos
生产环境开启鉴权。

端口 8848 / 9848 / 9849 不开放公网。

TDengine
修改默认密码（root / taosdata）。

端口 6030 / 6041 不开放公网。

Prometheus / Grafana
Grafana 改默认密码。

端口 9090 / 3000 不开放公网。

防火墙
只放行 Docker 网段（172.31.0.0/16）。

不对外开放 3306、6379、5672、1883、8848、9090、3000、6030、6041。

十二、生产环境上线路径
租一台云服务器（如阿里云、腾讯云）。

在服务器上安装 Docker、Docker Compose。

把镜像推到 Docker Hub 或阿里云镜像仓库。

在服务器上拉取镜像，docker compose up -d 启动。

配置 Nginx 反向代理，对外只暴露 80 / 443。

申请 Let's Encrypt 免费 SSL 证书，开启 HTTPS。

数据库、Redis、RabbitMQ、EMQX、Nacos、TDengine、Prometheus、Grafana 只在内网访问，不开放公网。

十三、Docker Compose 完整配置
yaml
# Docker Compose 编排文件
# 用途：一键启动 Smart-Office 全部服务
# 开发环境：本地和容器同时跑，容器端口映射到宿主机别的端口，避免冲突
# 生产环境：服务器上只有 Docker，容器端口直接映射到标准端口，不冲突

services:

  tdengine:
    image: tdengine/tdengine:3.3.0.0
    container_name: tdengine
    hostname: tdengine
    ports:
      - "6030:6030"   # 原生连接端口
      - "6041:6041"   # REST/taosAdapter 端口
    environment:
      TAOS_FQDN: tdengine
    volumes:
      - ./data/tdengine:/var/lib/taos
    restart: unless-stopped

  nacos:
    image: nacos/nacos-server:v2.3.2
    container_name: smart-office-nacos
    environment:
      - MODE=standalone
      - SPRING_DATASOURCE_PLATFORM=mysql
      - MYSQL_SERVICE_HOST=mysql
      - MYSQL_SERVICE_DB_NAME=nacos_config
      - MYSQL_SERVICE_USER=root
      - MYSQL_SERVICE_PASSWORD=123456
    ports:
      - "8848:8848"
      - "9848:9848"
      - "9849:9849"
    depends_on:
      mysql:
        condition: service_healthy
    restart: always

  mysql:
    image: mysql:8.0
    container_name: smart-office-mysql
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: smart_office
    ports:
      - "3307:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./docs/sql:/docker-entrypoint-initdb.d
    command: --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p123456"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: always

  redis:
    image: redis:latest
    container_name: smart-office-redis
    command: redis-server --requirepass 123456
    ports:
      - "6380:6379"
    volumes:
      - redis-data:/data
    restart: always

  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: smart-office-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: 123456
    ports:
      - "5673:5672"
      - "15673:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    restart: always

  emqx:
    image: emqx/emqx:latest
    container_name: smart-office-emqx
    ports:
      - "1884:1883"
      - "18084:18083"
      - "8084:8083"
    volumes:
      - emqx-data:/opt/emqx/data
    restart: always

  smart-office:
    build: .
    container_name: smart-office-app
    ports:
      - "8081:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - MYSQL_HOST=mysql
      - MYSQL_PORT=3306
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - RABBITMQ_HOST=rabbitmq
      - RABBITMQ_PORT=5672
      - MQTT_HOST=emqx
      - MQTT_PORT=1883
      - MQTT_CLIENT_ID=smart-office-telemetry-subscriber-docker
      - TDENGINE_HOST=tdengine
      - TDENGINE_PORT=6041
      - SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos:8848
    depends_on:
      nacos:
        condition: service_started
      mysql:
        condition: service_healthy
      redis:
        condition: service_started
      rabbitmq:
        condition: service_started
      emqx:
        condition: service_started
      tdengine:
        condition: service_started
    restart: always

  prometheus:
    image: prom/prometheus:latest
    container_name: smart-office-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus-alert.yml:/etc/prometheus/prometheus-alert.yml
      - prometheus-data:/prometheus
    restart: always

  grafana:
    image: grafana/grafana:latest
    container_name: smart-office-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin123
    volumes:
      - grafana-data:/var/lib/grafana
    restart: always

volumes:
  mysql-data:
  redis-data:
  rabbitmq-data:
  emqx-data:
  prometheus-data:
  grafana-data:
核心思想：用 healthcheck 确保依赖就绪后，再启动 smart-office，避免"项目启动 ≠ 依赖就绪"的问题。
