SET NAMES utf8mb4;
-- MySQL dump 10.13  Distrib 8.0.19, for Win64 (x86_64)
--
-- Host: localhost    Database: smart_office
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `device_telemetry`
--

DROP TABLE IF EXISTS `device_telemetry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_telemetry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `temperature` decimal(5,2) NOT NULL COMMENT '温度',
  `report_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_time` (`device_id`,`report_time`)
) ENGINE=InnoDB AUTO_INCREMENT=181 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备遥测数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_telemetry`
--

LOCK TABLES `device_telemetry` WRITE;
/*!40000 ALTER TABLE `device_telemetry` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_telemetry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mqtt_message_log`
--

DROP TABLE IF EXISTS `mqtt_message_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mqtt_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` varchar(128) NOT NULL COMMENT '幂等键',
  `topic` varchar(255) NOT NULL,
  `payload` text,
  `status` varchar(32) NOT NULL COMMENT 'RECEIVED/PROCESSED/FAILED/DEAD',
  `reason` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mqtt_message_log`
--

LOCK TABLES `mqtt_message_log` WRITE;
/*!40000 ALTER TABLE `mqtt_message_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `mqtt_message_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_approval`
--

DROP TABLE IF EXISTS `sys_approval`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(100) NOT NULL COMMENT '审批标题',
  `type` varchar(50) NOT NULL COMMENT '审批类型（如：请假、报销、出差）',
  `content` varchar(500) DEFAULT NULL COMMENT '审批内容',
  `applicant_id` bigint NOT NULL COMMENT '申请人ID',
  `approver_id` bigint NOT NULL COMMENT '当前审批人ID',
  `status` tinyint DEFAULT '0' COMMENT '状态（0待审批 1已通过 2已驳回）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_approval`
--

LOCK TABLES `sys_approval` WRITE;
/*!40000 ALTER TABLE `sys_approval` DISABLE KEYS */;
INSERT INTO `sys_approval` VALUES (7,'并发测试','请假','测试分布式锁',123,1,1,NULL,NULL,0),(8,'并发测试2','请假','测试分布式锁',123,1,1,NULL,NULL,0),(9,'1','1','1',123,1,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_approval` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_approval_record`
--

DROP TABLE IF EXISTS `sys_approval_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_id` bigint NOT NULL COMMENT '审批单ID',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `action` varchar(20) NOT NULL COMMENT '动作（SUBMIT/APPROVE/REJECT）',
  `comment` varchar(200) DEFAULT NULL COMMENT '审批意见',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_approval_record`
--

LOCK TABLES `sys_approval_record` WRITE;
/*!40000 ALTER TABLE `sys_approval_record` DISABLE KEYS */;
INSERT INTO `sys_approval_record` VALUES (7,7,123,'SUBMIT','发起审批',NULL),(8,7,1,'APPROVE','领导A：通过',NULL),(9,8,123,'SUBMIT','发起审批',NULL),(10,8,1,'APPROVE','A',NULL),(11,9,123,'SUBMIT','发起审批',NULL);
/*!40000 ALTER TABLE `sys_approval_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_name` varchar(50) NOT NULL COMMENT '权限名称',
  `permission_code` varchar(50) NOT NULL COMMENT '权限编码，如 user:delete',
  `type` varchar(20) DEFAULT 'API' COMMENT '类型：MENU菜单 / BUTTON按钮 / API接口',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` VALUES (1,'查看用户列表','user:list','API','2026-09-29 01:49:15','2026-09-29 01:49:15',0),(2,'删除用户','user:delete','API','2026-09-29 01:49:15','2026-09-29 01:49:15',0),(3,'提交审批','approval:submit','API','2026-09-29 03:04:26','2026-09-29 03:04:26',0),(4,'审批处理','approval:handle','API','2026-10-01 20:47:59','2026-10-01 20:47:59',0),(5,'分配角色','user:assignRole','API','2026-10-01 21:44:38','2026-10-01 21:44:38',0),(6,'查看修改个人信息','user:profile','API','2026-10-02 00:28:06','2026-10-02 00:28:06',0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码(如 ADMIN/USER)',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除(0未删除 1已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'管理员','ADMIN','拥有所有权限','2026-09-29 01:46:39','2026-09-29 01:46:39',0),(2,'副管理员','SUB_ADMIN','比管理员少一个权限','2026-10-01 22:40:14','2026-10-01 23:54:58',0),(3,'超级管理员','SUPER_ADMIN','拥有所有权限，不可被修改','2026-10-01 22:55:12','2026-10-01 22:55:12',0),(8,'普通用户','USER','基础权限，不能管理','2026-10-02 00:26:34','2026-10-02 00:26:34',0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` VALUES (2,1,2,'2026-09-29 01:49:28'),(3,1,1,'2026-09-29 02:18:52'),(4,1,3,'2026-09-29 03:04:40'),(5,1,4,'2026-10-01 20:48:11'),(6,1,5,'2026-10-01 21:45:10'),(7,2,4,'2026-10-01 22:40:31'),(8,2,3,'2026-10-01 22:40:31'),(9,2,2,'2026-10-01 22:40:31'),(10,2,1,'2026-10-01 22:40:31'),(14,3,4,'2026-10-01 22:55:21'),(15,3,3,'2026-10-01 22:55:21'),(16,3,5,'2026-10-01 22:55:21'),(17,3,2,'2026-10-01 22:55:21'),(18,3,1,'2026-10-01 22:55:21'),(22,8,3,'2026-10-02 00:27:43'),(23,2,6,'2026-10-02 00:28:20'),(24,1,6,'2026-10-02 00:28:43'),(25,3,6,'2026-10-02 00:29:01'),(26,8,6,'2026-10-02 00:30:38'),(27,2,5,'2026-10-02 00:54:52');
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码(加密后)',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint DEFAULT '1' COMMENT '状态(1正常 0禁用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除(0未删除 1已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=134 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (126,'Admin','$2a$12$CDgaO5S8eGBYQoyDI7r0SOHSdVQJdyakaODV7fW7eYbu7gK.QtNV6','','',1,NULL,NULL,0),(127,'admin1','$2a$12$Zo2OG2.yjPd57CCnrnVqX.F.1OJl4vbEbYx1Uo7Rgfce.jEtDRJ2G','','',1,NULL,NULL,0),(128,'admin2','$2a$12$4MrGdDsod.r5OJhIEX4GUO3hsp3IQkfMbWJ8r3Nhp5wx2PzygatP.','','',1,NULL,NULL,0),(129,'admin3','$2a$12$XtvBUev5sPmkjEMCUUwhD.Gq3EfMPVpyvSEZi5HLXQnriVAQsZ1Fm','','',1,NULL,NULL,0),(130,'Boss','$2a$12$Zxox/6g0tickK9OtgDE5u.PWLiY7ZURs9OYi7nEpu0v7Wc3rfwuva','','',1,NULL,NULL,0),(131,'xh','$2a$12$d6c/Du2dTeiCwiuW15SgYu0lj4n9Sa0Yzmj5qKLHfCu4ndR7t3Hhi','','',1,NULL,NULL,0),(132,'xy','$2a$12$fLT2F6RYa5yHpXFNLpJ3s.WfPZZdGYNY4cD7bmRpN6QExE.8gkfyu','','',1,NULL,NULL,0),(133,'hcg','$2a$12$FP2.wKGgwgdLAtrBpjOezeXOXMgbtbLFF9ZswHWXnF2CEqiC.freS','','',1,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`) COMMENT '用户和角色唯一索引'
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (10,130,3,'2026-10-01 23:33:29'),(42,126,1,'2026-10-02 00:44:17'),(43,127,2,'2026-10-02 00:44:46'),(62,129,2,'2026-10-02 01:03:16'),(63,128,2,'2026-10-02 01:03:21'),(72,131,8,'2026-10-02 01:12:53'),(73,133,8,'2026-10-02 01:16:35'),(74,132,8,'2026-10-02 01:18:42');
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'smart_office'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-04  1:01:23
