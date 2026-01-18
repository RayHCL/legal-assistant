# 法律咨询助手系统

基于Spring Boot 3.x和AI的法律咨询助手系统，提供智能化的法律咨询服务。

## 技术栈

- **后端框架**: Spring Boot 3.2.0
- **数据库**: MySQL 8.x
- **缓存**: Redis 7
- **对象存储**: MinIO
- **向量数据库**: Milvus 2.3.4
- **ORM**: MyBatis Plus 3.5.5
- **认证**: JWT

## 快速开始

### 1. 启动外部服务

使用Docker Compose启动所有外部服务：

```bash
docker-compose up -d
```

服务包括：
- MySQL (端口: 3306)
- Redis (端口: 6379)
- MinIO (端口: 9000/9001)
- Milvus (端口: 19530)
- etcd (内部使用)

### 2. 初始化数据库

数据库会在首次启动时自动创建。如果需要手动初始化，可以执行：

```bash
mysql -u root -proot123456 < init-sql/init.sql
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`，确保配置正确：

- MySQL连接信息
- Redis连接信息
- MinIO连接信息
- Milvus连接信息
- JWT密钥

### 4. 启动应用

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package
java -jar target/legal-assistant-1.0.0.jar
```

### 5. 访问应用

应用启动后，默认运行在 `http://localhost:8080`

## API接口

### 用户认证

#### 1. 发送验证码
```
POST /api/auth/send-code
Content-Type: application/json

{
  "phone": "13800138000"
}
```

#### 2. 登录/注册
```
POST /api/auth/login
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456"
}
```

响应：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "nickname": "用户8000",
    "avatar": null,
    "expiresIn": 604800
  }
}
```

#### 3. 退出登录
```
POST /api/auth/logout
Authorization: Bearer {token}
```

#### 4. 刷新Token
```
POST /api/auth/refresh
Authorization: Bearer {refreshToken}
```

### 用户信息

#### 1. 获取用户信息
```
GET /api/user/info
Authorization: Bearer {token}
```

#### 2. 更新用户信息
```
PUT /api/user/info
Authorization: Bearer {token}
Content-Type: application/json

{
  "nickname": "新昵称",
  "avatar": "http://example.com/avatar.jpg",
  "bio": "个人简介"
}
```

#### 3. 注销账号
```
POST /api/user/deactivate?code=123456
Authorization: Bearer {token}
```

### 会话管理

#### 1. 创建会话
```
POST /api/conversation/create
Authorization: Bearer {token}
Content-Type: application/json

{
  "title": "我的法律咨询",
  "agentType": "legal_consultation",
  "modelType": "deepseek-chat"
}
```

#### 2. 获取会话列表
```
GET /api/conversation/list?page=1&size=20
Authorization: Bearer {token}
```

#### 3. 获取会话详情
```
GET /api/conversation/{conversationId}
Authorization: Bearer {token}
```

#### 4. 重命名会话
```
PUT /api/conversation/{conversationId}/rename?newTitle=新标题
Authorization: Bearer {token}
```

#### 5. 置顶/取消置顶会话
```
PUT /api/conversation/{conversationId}/pin?pinned=true
Authorization: Bearer {token}
```

#### 6. 删除会话
```
DELETE /api/conversation/{conversationId}
Authorization: Bearer {token}
```

#### 7. 获取会话消息历史
```
GET /api/conversation/{conversationId}/messages
Authorization: Bearer {token}
```

### 统一问答接口

#### 问答接口
```
POST /api/chat/ask
Authorization: Bearer {token}
Content-Type: application/json

{
  "question": "什么是合同纠纷？",
  "fileIds": [1, 2],
  "deepThinking": false,
  "modelType": "DEEPSEEK_CHAT",
  "temperature": 0.7,
  "agentType": "LEGAL_CONSULTATION",
  "knowledgeBaseId": 1,
  "conversationId": 1,
  "autoGenerateTitle": true
}
```

### 文件管理

#### 1. 上传文件
```
POST /api/file/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: (文件)
knowledgeBaseId: (可选)
description: (可选)
```

#### 2. 获取文件信息
```
GET /api/file/{fileId}
Authorization: Bearer {token}
```

#### 3. 删除文件
```
DELETE /api/file/{fileId}
Authorization: Bearer {token}
```

### 分享功能

#### 1. 创建分享
```
POST /api/share/{conversationId}?expirationDays=7&password=123456
Authorization: Bearer {token}
```

响应：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "shareId": "abc123...",
    "shareUrl": "/api/share/abc123...",
    "expirationTime": "2024-12-31T23:59:59",
    "hasPassword": true
  }
}
```

#### 2. 访问分享（无需Token）
```
GET /api/share/{shareId}?password=123456
```

### 知识库管理

#### 1. 创建知识库
```
POST /api/knowledge-base/create?name=我的知识库&description=描述
Authorization: Bearer {token}
```

#### 2. 获取知识库列表
```
GET /api/knowledge-base/list
Authorization: Bearer {token}
```

#### 3. 删除知识库
```
DELETE /api/knowledge-base/{id}
Authorization: Bearer {token}
```

## 开发说明

### 项目结构

```
src/main/java/com/legal/assistant/
├── LegalAssistantApplication.java    # 主启动类
├── config/                           # 配置类
│   ├── RedisConfig.java
│   ├── WebMvcConfig.java
│   └── MyBatisPlusConfig.java
├── controller/                       # 控制器
│   ├── AuthController.java
│   └── UserController.java
├── service/                          # 服务层
│   ├── AuthService.java
│   ├── UserService.java
│   └── SmsService.java
├── mapper/                           # MyBatis映射器
│   └── UserMapper.java
├── entity/                           # 实体类
│   └── User.java
├── dto/                              # 数据传输对象
│   ├── request/
│   └── response/
├── enums/                            # 枚举类
├── exception/                        # 异常处理
├── interceptor/                      # 拦截器
│   └── AuthInterceptor.java
├── annotation/                       # 注解
│   └── NoAuth.java
├── utils/                            # 工具类
│   └── JwtUtils.java
└── common/                           # 通用类
    └── Result.java
```

### Token认证

大部分接口需要Token认证，在请求头中携带：

```
Authorization: Bearer {token}
```

标记为 `@NoAuth` 的接口无需Token认证。

### 验证码说明

开发环境下，验证码会直接打印到控制台，格式如下：

```
========================================
验证码: 123456
手机号: 13800138000
========================================
```

生产环境需要配置真实的短信服务。

## 已实现功能

- [x] 用户认证和授权（手机号验证码登录/注册、JWT Token）
- [x] 用户信息管理（查看、编辑、注销）
- [x] 统一问答接口（支持多种Agent类型和模型）
- [x] 文件上传和解析（支持多种文件格式，存储到MinIO）
- [x] 会话管理（创建、列表、重命名、置顶、删除）
- [x] 分享功能（创建分享链接、密码保护、过期时间）
- [x] 知识库管理（创建、列表、删除）

## 待优化功能

- [ ] 文件内容提取和向量化（PDF、Word等文档解析）
- [ ] 流式输出（SSE）
- [ ] 深度思考模式
- [ ] OCR图片识别
- [ ] 集成真实的AI模型API（DeepSeek等）
- [ ] 向量检索功能

## 注意事项

1. **JWT密钥**: 生产环境必须修改 `application.yml` 中的 `jwt.secret`
2. **数据库密码**: 确保Docker Compose中的MySQL密码与配置文件一致
3. **短信服务**: 生产环境需要配置真实的短信服务商API
4. **MinIO Bucket**: 首次启动需要确保MinIO已创建 `legal-assistant` bucket

## 许可证

MIT License
