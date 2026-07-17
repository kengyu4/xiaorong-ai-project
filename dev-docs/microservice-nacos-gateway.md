# 小绒老师助教：Nacos + Gateway 微服务接入说明

## 当前拆分方式

这次先做的是第一阶段微服务化，结构如下：

1. `xiaorong-teacher-assistant`
   当前业务服务，服务名：`xiaorong-service-study`
   负责：
   - 登录 / 注册 / 当前用户
   - 课程 / 课堂 / 作业 / 复盘
   - AI Provider / Runtime 状态

2. `xiaorong-teacher-assistant/xiaorong-gateway`
   网关服务，服务名：`xiaorong-gateway`
   负责：
   - 统一入口
   - 路由转发
   - 首层 token 校验
   - 白名单放行

当前还没有把 auth 单独拆成第三个服务，而是先把网关和服务注册体系建起来。这样改动可控，不会一下把现有业务打散。

## Nacos 配置文件

目录：

```text
xiaorong-teacher-assistant/nacos-config/
```

需要导入 3 个 Data ID：

1. `common-config.yaml`
2. `xiaorong-service-study-dev.yaml`
3. `xiaorong-gateway-dev.yaml`

Group 都用：

```text
DEFAULT_GROUP
```

格式：

```text
YAML
```

## 启动 Nacos

如果你本机已经有 Nacos，直接用现成的即可。

如果没有，最简单的 Docker 方式：

```bash
docker run -d ^
  --name nacos-standalone ^
  -p 8848:8848 ^
  -e MODE=standalone ^
  nacos/nacos-server:v2.3.2
```

启动后打开：

```text
http://localhost:8848/nacos
```

默认账号密码通常是：

```text
nacos / nacos
```

## 在 Nacos 里导入配置

进入：

```text
配置管理 -> 配置列表 -> 新建配置
```

分别新建：

### 1. common-config.yaml

内容直接取：

```text
xiaorong-teacher-assistant/nacos-config/common-config.yaml
```

### 2. xiaorong-service-study-dev.yaml

内容直接取：

```text
xiaorong-teacher-assistant/nacos-config/xiaorong-service-study-dev.yaml
```

这里你要重点改：

- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.rabbitmq.host`
- `spring.rabbitmq.port`
- `xiaorong.persistence.username`
- `xiaorong.persistence.password`
- `xiaorong.persistence.jdbc-url`
- `xiaorong.rabbitmq.lesson-material-exchange`
- `xiaorong.rabbitmq.lesson-material-queue`
- `xiaorong.rabbitmq.lesson-material-routing-key`
- `xiaorong.ai.providers[*].api-key`

### 3. xiaorong-gateway-dev.yaml

内容直接取：

```text
xiaorong-teacher-assistant/nacos-config/xiaorong-gateway-dev.yaml
```

如果你后面把 auth 真拆成独立服务，再把：

```yaml
uri: lb://xiaorong-service-study
```

改成新的 auth 服务名即可。

## 启动顺序

### 1. 启动业务服务

先确保 MySQL、Redis、RabbitMQ 可访问。课程材料后台生成使用 RabbitMQ：`POST /api/admin/ai/course/{courseId}/generate` 投递 `GENERATE_LESSON_MATERIAL`，业务服务内的监听器消费后写 `ai_lesson_material` 并刷新 Redis。

```bash
cd C:\Users\27695\Desktop\文件\测试2\xiaorong-teacher-assistant
mvn spring-boot:run
```

它会注册成：

```text
xiaorong-service-study
```

### 2. 启动网关

```bash
cd C:\Users\27695\Desktop\文件\测试2\xiaorong-teacher-assistant\xiaorong-gateway
mvn spring-boot:run
```

它会注册成：

```text
xiaorong-gateway
```

### 3. 启动前端

```bash
cd C:\Users\27695\Desktop\文件\测试2\frontend\Forhaed
npm run dev -- --host 127.0.0.1 --port 5173
```

前端现在默认代理到：

```text
http://localhost:8090
```

也就是网关端口。

## 网关当前路由

`xiaorong-gateway-dev.yaml` 当前配置：

- `/api/auth/**` -> `lb://xiaorong-service-study`
- `/api/study/**` -> `lb://xiaorong-service-study`
- `/api/admin/**` -> `lb://xiaorong-service-study`
- `/internal/**` -> `lb://xiaorong-service-study`

这一步的目标是先把“服务注册 + Nacos 配置中心 + 网关统一入口”跑起来。

下一阶段如果你要继续拆：

1. 把 auth 从业务服务里拆成 `xiaorong-service-auth`
2. 网关把 `/api/auth/**` 指到 `lb://xiaorong-service-auth`
3. study 服务只保留学习与 AI 相关接口

## 当前认证流

1. 前端调用网关 `/api/auth/login` 或 `/api/auth/register`
2. 网关对白名单放行，转发给 `xiaorong-service-study`
3. 业务服务校验账号密码 / 注册信息
4. 密码用 BCrypt 哈希保存到 MySQL `ai_user`
5. 登录成功生成随机 opaque token
6. Redis 保存：

```text
xiaorong:auth:token:{token}
```

值是当前用户会话 JSON

7. 前端后续请求带：

```http
Authorization: Bearer xr_xxx
```

8. 网关先查 Redis 过滤一次
9. 业务服务本地拦截器再查 Redis 一次
10. 学习接口再校验 session 是否属于当前 userId

## 为什么不是只靠网关

因为只靠网关有两个现实问题：

1. 直接访问业务服务端口时会绕过网关
2. 即使走了网关，如果业务层不做资源归属校验，拿别人的 `sessionId` 仍可能访问别人的学习记录

所以现在是“双层保护”：

- 网关做首层拦截
- 业务服务做二层校验

## 为什么没有照搬原项目的 JWT 方案

原项目有几处不适合直接搬：

1. JWT 密钥太弱，而且硬编码
2. 网关和服务里会打印 token
3. token 主要靠网关校验，服务本身保护不够

当前实现改成了：

1. 不用弱 JWT，先用 Redis opaque token
   这样登出、踢会话、续期都更直接
2. 不打印 token、验证码、密码
3. 服务自己也校验 token，不把安全边界只压在网关上

如果你后面确定要换成真正 JWT，我建议走：

1. 强随机密钥，放环境变量
2. JWT 只放最小 claims
3. Redis 仍保留会话黑名单 / jti / 踢出控制
4. 服务和网关都不要打印 token 原文
