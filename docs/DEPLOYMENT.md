markdown
# Smart-Office 部署与运维手册

**文档版本**：v1.5
**最后更新**：2026-10-09
**对应代码版本**：Smart-Office v1.5

本文档记录 Smart-Office 项目从本地运行到 Docker Compose 一键部署的完整操作流程和故障排查指南。

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 本地启动、Nacos 配置 |
| v1.1 | 2026-09-29 | Docker 部署、EMQX |
| v1.2 | 2026-10-04 | Nacos 部署细节、常见问题 |
| v1.3 | 2026-10-05 | TDengine、Prometheus |
| v1.4 | 2026-10-05 | 完整 Compose 配置 |
| v1.5 | 2026-10-09 | 多模块部署；本地起仿真器；TDengine 自动建超表；EMQX 参数调大 |

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
| TDengine | 3.3.0.0 | 6030 / 6041 |
| Prometheus | latest | 9090 |
| Grafana | latest | 3000 |
| Docker Desktop | 29.8.0 | - |
| Git | 2.4x+ | - |
| Gitee | - | - |

---

## 二、多模块工程结构

```text
smart-office (父 pom)
├── smart-office-app          业务模块（必部署）
├── smart-office-simulation   仿真模块（生产不部署）
└── deploy                    部署编排（Docker Compose + Prometheus + SQL）
打包：

bash
mvn clean package -DskipTests
生成两个 jar：

smart-office-app/target/smart-office-0.0.1-SNAPSHOT.jar

smart-office-simulation/target/smart-office-simulation-0.0.1-SNAPSHOT.jar

三、本地启动步骤
3.1 起中间件（Docker Compose）
bash
cd deploy
docker compose up -d mysql redis rabbitmq emqx nacos tdengine prometheus grafana
只起中间件，不起 smart-office 容器（本地用 IDEA 跑 app）。

3.2 起 app（本地 IDEA 或 jar）
bash
# 方式 A：IDEA 里 Run SmartOfficeApplication
# 方式 B：命令行
cd D:\IdeaProjects\smart-office
java -jar smart-office-app\target\smart-office-0.0.1-SNAPSHOT.jar
3.3 起仿真器（本地）
bash
java -jar smart-office-simulation\target\smart-office-simulation-0.0.1-SNAPSHOT.jar
仿真器控制台支持：

text
inject {deviceId} {faultType}   注入故障
reset {deviceId}                 复位
random {probability}             随机批量注入
status                           查看所有设备状态
help                             帮助
quit                             退出
3.4 启动顺序建议
先起中间件（MySQL、Redis、RabbitMQ、EMQX、Nacos、TDengine）

等 Nacos 就绪（日志出现 Nacos started successfully，约 30~60 秒）

再起 app（SpringBoot 依赖 Nacos 拉配置）

最后起仿真器

⚠️ 关键约束：不要同时启动容器里的 smart-office-app 和本地 jar/IDEA 的 SpringBoot，会抢 clientId、抢端口、抢资源。

四、配置文件说明（Nacos + application.yml）
本地 application.yml（最小配置）
yaml
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
  client-id: smart-office-backend
  username: admin
  password: admin123456
  qos: 1
  topic: factory/device/+/telemetry
  clean-session: false

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

mqtt.clean-session=false：持久会话，app 掉线期间 EMQX 缓存消息，重连补发。

mqtt.topic=factory/device/+/telemetry：统一遥测包，与仿真器发送的 Topic 对齐。

tdengine.url：REST 连接（jdbc:TAOS-RS://），不依赖本地 TDengine 客户端。

五、Docker Compose 一键部署
5.1 打包
bash
mvn clean package -DskipTests
5.2 启动
bash
cd deploy
docker compose up -d
这会：

启动 8 个中间件容器（MySQL、Redis、RabbitMQ、EMQX、Nacos、TDengine、Prometheus、Grafana）

可选：启动 smart-office-app 容器（生产环境用）

MySQL 第一次启动时，自动执行 deploy/sql/ 下的 SQL

5.3 验证容器状态
bash
docker compose ps
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
5.4 查看日志
bash
docker logs smart-office-app --tail 50
出现 Started SmartOfficeApplication 即为启动成功。

5.5 常用 Docker Compose 命令
命令	作用
docker compose up -d	启动所有容器
docker compose up -d mysql redis rabbitmq emqx nacos tdengine	只启动指定服务
docker compose stop smart-office	只停 smart-office 服务（保留中间件）
docker compose down	停掉并删除所有容器
docker compose down -v	停掉并删除所有容器 + 数据卷
docker compose ps	查看容器状态
docker compose logs smart-office	查看 SpringBoot 日志
⚠️ 服务名 vs 容器名：docker compose stop <服务名> 用服务名（如 smart-office），不是容器名（smart-office-app）。

六、Docker Compose 环境变量覆盖
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
    - TDENGINE_HOST=tdengine
    - TDENGINE_PORT=6041
    - SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos:8848
关键：环境变量的优先级，比 application.yml 高。SpringBoot 的"宽松绑定"：

MYSQL_HOST → ${MYSQL_HOST:localhost}

TDENGINE_HOST → ${TDENGINE_HOST:localhost}

注意：Nacos 配置本身也用了环境变量占位，所以环境变量会穿透 Nacos 配置，覆盖其中的占位符。

七、Nacos 部署
7.1 Nacos 容器
Docker Compose 启动的 Nacos，容器名 smart-office-nacos，端口映射：

宿主机 8848 → 容器 8848（HTTP）

宿主机 9848 → 容器 9848（gRPC）

宿主机 9849 → 容器 9849（gRPC）

⚠️ Nacos 启动慢（30~60 秒），日志出现 Nacos started successfully 才算就绪，之后再启动 SpringBoot。

7.2 Nacos 管理后台
浏览器访问 http://localhost:8848/nacos

账号：nacos

密码：nacos

7.3 Nacos 连 MySQL
Nacos 用 MySQL 存配置，数据库 nacos_config 由 deploy/sql/nacos.sql 初始化。

建表 SQL 必须用官方完整版（精简版会踩坑，见 BUG_LOG.md 问题 49-53）。

7.4 配置 Data ID
smart-office-docker.yml（本地和 Docker 通用）

Group：DEFAULT_GROUP

7.5 动态刷新验证
改 Nacos 里的配置，观察 SpringBoot 日志，应出现：

text
[Nacos Config] Listening config: dataId=smart-office-docker.yml, group=DEFAULT_GROUP
Refresh keys changed: [xxx]
八、TDengine 部署
8.1 TDengine 容器
Docker Compose 启动的 TDengine，容器名 tdengine，端口映射：

宿主机 6030 → 容器 6030（原生连接）

宿主机 6041 → 容器 6041（REST/taosAdapter）

8.2 建库建表
进容器：

bash
docker exec -it tdengine taos
建库建表：

sql
CREATE DATABASE IF NOT EXISTS smart_office
  KEEP 30
  DURATION 1
  PRECISION 'ms';

USE smart_office;

CREATE STABLE IF NOT EXISTS telemetry (
    ts          TIMESTAMP,
    temperature DOUBLE
) TAGS (
    device_id   NCHAR(64)
);
⚠️ v1.5 关键改进：app 启动时自动建超表。

TdengineWriteService.init() 里加了：

java
@PostConstruct
public void init() throws SQLException {
    conn = DriverManager.getConnection(url, username, password);
    try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate(
            "CREATE STABLE IF NOT EXISTS telemetry (" +
            "  ts TIMESTAMP, temperature DOUBLE" +
            ") TAGS (device_id NCHAR(32))"
        );
    }
}
这样清库（DROP DATABASE）后，app 重启会自动建超表，不用手动建。

8.3 验证
bash
docker exec -it tdengine taos -s "SELECT COUNT(*) FROM smart_office.telemetry;"
8.4 连接方式选择
本地 IDEA 跑：用 REST 连接 jdbc:TAOS-RS://localhost:6041/smart_office，不依赖本地客户端。

容器里跑：用 REST 连接 jdbc:TAOS-RS://tdengine:6041/smart_office。

原生连接（jdbc:TAOS://）需要本地装 TDengine 客户端（taos.dll/libtaos.so），容器化场景不推荐。

8.5 清库（重跑前）
bash
# 清 MySQL
docker exec -it smart-office-mysql mysql -uroot -p123456 -e "USE smart_office; TRUNCATE TABLE device_telemetry; TRUNCATE TABLE mqtt_message_log;"

# 清 TDengine（整库重建）
docker exec -it tdengine taos -s "DROP DATABASE IF EXISTS smart_office;"
docker exec -it tdengine taos -s "CREATE DATABASE IF NOT EXISTS smart_office KEEP 30;"
docker exec -it tdengine taos -s "USE smart_office; CREATE STABLE IF NOT EXISTS telemetry (ts TIMESTAMP, temperature DOUBLE) TAGS (device_id NCHAR(32));"
九、MQTT（EMQX）部署
9.1 EMQX 容器
Docker Compose 启动的 EMQX，容器名 smart-office-emqx，端口映射：

宿主机 1884 → 容器 1883

宿主机 18084 → 容器 18083

宿主机 8084 → 容器 8083

9.2 EMQX 管理后台
浏览器访问 http://localhost:18084

账号：admin

密码：admin123456

密码重置（EMQX 5.x 命令已变更）：

bash
docker exec -it smart-office-emqx emqx ctl admins passwd admin admin123456
⚠️ 注意：EMQX 5.x 的 Dashboard 用户管理命令是 admins（add / passwd / del / mfa），不是 dashboard。

9.3 EMQX 关键参数调优
v1.5 关键改进：max_mqueue_len 调大。

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
调大原因：app 掉线期间，EMQX 会为它缓存离线消息。默认 max_mqueue_len=1000，超过就丢。 工业级场景必须调大。

估算公式：max_mqueue_len ≥ 最长离线时间 × 消息速率（如 1000 台 × 每秒 1 条 × 离线 5 分钟 = 30 万条）。

验证配置生效：

bash
docker exec smart-office-emqx env | Select-String "MAX_MQUEUE"
9.4 用 MQTTX 模拟设备发送数据（可选）
现在优先用仿真器（smart-office-simulation），不再需要手动 MQTTX。

如果要手动测：

协议：mqtt://

Host：宿主机真实 IP

Port：1884

用户名 / 密码：EMQX 的 admin / admin123456

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
9.5 验证 MQTT 链路
bash
docker logs smart-office-app --tail 20
预期输出：

text
TDengine 写入 X 条（Y 个设备），耗时 Zms
累计：XXXX
验证持久会话：

bash
docker exec smart-office-emqx emqx ctl clients show smart-office-backend
关键字段：

clean_start=false → 持久会话

session_expiry_interval=7200 → 会话保留 2 小时

enqueued_msgs=N → 离线队列积压 N 条

十、监控体系部署
10.1 Prometheus
浏览器访问 http://localhost:9090

Query：查指标

Alerts：看告警规则

Status → Targets：看抓取目标

⚠️ 本地 IDEA 跑 SpringBoot 时，Prometheus 抓取目标要改。容器内 prometheus.yml 里写的是 smart-office:8080（服务名），本地跑时该服务名解析不了。改用 host.docker.internal:8080。

10.2 Grafana
浏览器访问 http://localhost:3000

账号：admin

密码：admin123

数据源 URL：http://smart-office-prometheus:9090

10.3 告警规则
告警规则文件 prometheus-alert.yml，包含 3 条：

HTTP 5xx 错误率 > 1%，持续 1 分钟

JVM 堆内存 > 85%，持续 5 分钟

服务下线，持续 1 分钟

十一、常见问题排查
问题	原因	解决方案
8080 端口被占用	宿主机 8080 被 IDEA 占用	改用 -p 8081:8080
端口冲突	本地和容器端口冲突	容器端口映射到宿主机别的端口
容器内连不上 MySQL	容器 localhost ≠ 宿主机	用服务名 mysql，或环境变量覆盖
Nacos 启动报表不存在	建表 SQL 用了精简版	用官方完整版 mysql-schema.sql
SpringBoot 配置拉不到	没加 spring-cloud-starter-bootstrap	pom.xml 加 bootstrap 依赖
Nacos 配置覆盖环境变量	Nacos 配置优先级高于环境变量	用环境变量占位 ${VAR:default}
本地和 Docker clientId 冲突	同一 clientId 互相挤掉	只跑一个实例，或 clientId 加后缀
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
TDengine 超表不存在	清库后超表被删，未重建	init() 里加 CREATE STABLE IF NOT EXISTS
Nacos 未就绪启动失败	Nacos 启动 30~60 秒，SpringBoot 抢跑	等 Nacos 日志就绪再启 SpringBoot
容器全停应用连不上	docker compose down 停了全部	docker compose stop <服务名> 只停单个
EMQX Dashboard 密码改了没生效	命令版本差异	用 emqx ctl admins passwd
MQTT app 掉线后收不到补发	cleanSession=true	改 setCleanSession(false)
MQTT 补发推一半卡住	回调里 log.info 阻塞	回调里只入队，日志移到消费端
多模块重构后 simulation 打包失败	空模块无主类	加启动类，或从父 pom modules 移除
多模块重构后 Jackson 缺失	spring-boot-starter 不带 Jackson	加 jackson-databind 依赖
Maven 打包中文乱码	缺 project.build.sourceEncoding	父 pom 加 UTF-8 属性
十二、安全配置清单
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

十三、生产环境上线路径
租一台云服务器（如阿里云、腾讯云）。

在服务器上安装 Docker、Docker Compose。

把镜像推到 Docker Hub 或阿里云镜像仓库。

在服务器上拉取镜像，docker compose up -d 启动。

配置 Nginx 反向代理，对外只暴露 80 / 443。

申请 Let's Encrypt 免费 SSL 证书，开启 HTTPS。

数据库、Redis、RabbitMQ、EMQX、Nacos、TDengine、Prometheus、Grafana 只在内网访问，不开放公网。

十四、Git 版本管理
Gitee 仓库： git@gitee.com:xiao-hongh/smart-office.git

首次推送：

bash
git init
git add .
git commit -m "init: Smart-Office 项目初始化"
git remote add origin git@gitee.com:xiao-hongh/smart-office.git
git branch -M main
git push -u origin main
日常提交三连：

bash
git add .
git commit -m "说明"
git push
.gitignore 排除：

target/、*.jar

.idea/、*.iml

*.log、logs/

data/、deploy/data/

十五、数据零丢失验证（v1.5）
场景：100 台设备，每秒 100 条遥测，app 掉线 30 秒后重连。

完整链路：

text
sim 发 3200 条 → EMQX → app 掉线 → EMQX 缓存到 offline 队列
    ↓
app 重连 → EMQX 自动补发 → app 消费 → MySQL + TDengine 双写
验证结果：

检查点	结果
EMQX clean_start	false
EMQX session_expiry_interval	7200
app 掉线时 enqueued_msgs	3200
app 重连后 enqueued_msgs	0
MySQL 主表	3262
TDengine 超表	3166
死信	0
补发入库耗时	< 1 秒
关键参数：

MQTT clientId：固定（smart-office-backend）

MQTT cleanSession：false

MQTT session_expiry_interval：7200 秒

EMQX max_mqueue_len：100000

Paho maxInflight：1000

详见 ARCHITECTURE.md 第二十五节。