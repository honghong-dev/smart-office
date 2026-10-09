#构建 SpringBoot 镜像
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/smart-office-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:-UseContainerSupport", "-jar", "app.jar"]