# Runtime image uses the Maven artifact built on the host.
# Run: mvn -q -DskipTests package
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_OPTS=""
COPY target/xiaorong-teacher-assistant-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8088
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
