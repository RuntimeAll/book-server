# 贝尔实验室 Spring 官方推荐镜像 JDK下载地址 https://bell-sw.com/pages/downloads/
FROM bellsoft/liberica-openjdk-rocky:17.0.16-cds
#FROM bellsoft/liberica-openjdk-rocky:21.0.8-cds
#FROM findepi/graalvm:java17-native

LABEL maintainer="Lion Li"

RUN mkdir -p /ruoyi/server/logs \
    /ruoyi/server/temp \
    /ruoyi/skywalking/agent

# chromium：ChromePdfRenderer/BookExportService 的 PDF 渲染引擎（探测 /usr/bin/chromium）。
# 中文字体不入镜像——prod 挂载 /opt/book-ai/fonts → /usr/share/fonts/chinese（simhei.ttf，
# openhtmltopdf 与 chromium 共用；.ttc 不可用见 ScheduleRenderUtil）。
RUN dnf install -y epel-release && \
    dnf install -y chromium && \
    dnf clean all && rm -rf /var/cache/dnf

WORKDIR /ruoyi/server

ENV SERVER_PORT=8080 SNAIL_PORT=28080 LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""

EXPOSE ${SERVER_PORT}
# 暴露 snail job 客户端端口 用于定时任务调度中心通信
EXPOSE ${SNAIL_PORT}

ADD ./target/ruoyi-admin.jar ./app.jar

SHELL ["/bin/bash", "-c"]

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} \
           -Dsnail-job.port=${SNAIL_PORT} \
           # 应用名称 如果想区分集群节点监控 改成不同的名称即可
           #-Dskywalking.agent.service_name=ruoyi-server \
           #-javaagent:/ruoyi/skywalking/agent/skywalking-agent.jar \
           -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} \
           -jar app.jar

