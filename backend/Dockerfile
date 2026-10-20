# 使用 JDK 17 基础镜像
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# 复制所有文件
COPY . .
# 编译并跳过测试
RUN chmod +x mvnw && ./mvnw clean package -DskipTests
# 暴露 8080 端口
EXPOSE 8080
# 启动项目（注意：请把后面的 jar 包名字换成你们实际打出来的名字，比如 shop-backend-1.0.0.jar）
CMD ["java", "-jar", "target/online-shop-backend-1.0.0.jar"]
