# 法律咨询后端 API 文档

## 目录

- [概述](#概述)
- [通用说明](#通用说明)
- [认证模块 (Auth)](#认证模块-auth)
- [用户模块 (User)](#用户模块-user)
- [消息分享模块 (Share)](#消息分享模块-share)
- [报告模块 (Report)](#报告模块-report)
- [咨询模板模块 (Templates)](#咨询模板模块-templates)
- [风险评估案件模块 (Risk Case)](#风险评估案件模块-risk-case)
- [风险争议类别模块 (Dispute Category)](#风险争议类别模块-dispute-category)
- [通用响应格式](#通用响应格式)
- [实体定义](#实体定义)

---

## 概述

本文档描述了法律咨询后端系统的所有 API 接口，包括认证授权、用户管理、AI对话、风险评估等功能模块。

**基础路径**: `/api`

**技术栈**: Spring Boot + MyBatis-Plus

---

## 通用说明

### 认证方式

除了登录、注册、发送验证码、获取分享详情等公开接口外，其他接口需要在请求头中携带访问令牌：

```
Authorization: Bearer {accessToken}
```

其中 `{accessToken}` 为登录接口返回的 `accessToken` 字段值。

### 通用响应格式

所有接口返回统一的 `Result<T>` 格式：

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": T
}
```

### 错误码说明

#### 通用状态码 (1xxx)

| 错误码 | 说明 |
|--------|------|
| 1000 | 操作成功 |
| 1001 | 系统错误 |
| 1002 | 参数错误 |

#### 代理异常 (2xxx)

| 错误码 | 说明 |
|--------|------|
| 2001 | 代理连接失败 |
| 2002 | 代理请求超时 |
| 2003 | 代理认证失败 |
| 2004 | 代理服务不可用 |
| 2005 | 代理请求失败 |

#### 认证异常 (3xxx)

| 错误码 | 说明 |
|--------|------|
| 3001 | 认证失败 |
| 3002 | 令牌已过期 |
| 3003 | 令牌无效 |
| 3004 | 权限不足 |
| 3005 | 用户不存在 |
| 3006 | 密码错误 |
| 3007 | 邮箱已被使用 |
| 3008 | 用户已被禁用 |
| 3009 | 用户名已被使用 |
| 3010 | 验证码错误 |
| 3011 | 验证码已过期 |
| 3012 | 验证码未找到 |

#### 业务异常 (4xxx)

| 错误码 | 说明 |
|--------|------|
| 4001 | 业务处理失败 |
| 4002 | 数据不存在 |
| 4003 | 数据重复 |
| 4004 | 状态异常 |
| 4005 | 操作失败 |

#### 文件异常 (5xxx)

| 错误码 | 说明 |
|--------|------|
| 5001 | 文件不存在 |
| 5002 | 文件上传失败 |
| 5003 | 文件下载失败 |
| 5004 | 文件大小超限 |
| 5005 | 文件类型不支持 |
| 5006 | 文件读取失败 |
| 5007 | 文件写入失败 |

---

## 认证模块 (Auth)

**基础路径**: `/api/auth`

### 1. 发送验证码

向指定手机号发送 6 位数字验证码，有效期 5 分钟。

**请求**

```
POST /api/auth/send-code
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| phoneNumber | String | 是 | @NotBlank, @Pattern(^1[3-9]\\d{9}$) | 手机号码 |

**请求示例**

```json
{
  "phoneNumber": "13812345678"
}
```

**响应参数**

| 字段 | 类型 | 说明 |
|------|------|------|
| data | String | 发送结果描述 |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": "验证码发送成功"
}
```

---

### 2. 用户登录

使用手机号和验证码登录，用户不存在则自动注册。

**请求**

```
POST /api/auth/login
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| phoneNumber | String | 是 | @NotBlank, @Pattern(^1[3-9]\\d{9}$) | 手机号码 |
| verificationCode | String | 是 | @NotBlank | 6位数字验证码 |

**请求示例**

```json
{
  "phoneNumber": "13812345678",
  "verificationCode": "123456"
}
```

**响应参数 - LoginResponse**

| 字段 | 类型 | 说明 |
|------|------|------|
| accessToken | String | 访问令牌 |
| refreshToken | String | 刷新令牌 |
| expiresIn | Long | 访问令牌过期时间（秒） |
| userId | Integer | 用户ID |
| username | String | 用户名 |
| isAdmin | Boolean | 是否管理员 |
| avatarUrl | String | 用户头像URL |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600,
    "userId": 1,
    "username": "user_138****5678",
    "isAdmin": false,
    "avatarUrl": "https://example.com/avatar.jpg"
  }
}
```

---

### 3. 刷新 Token

使用刷新令牌获取新的访问令牌。

**请求**

```
POST /api/auth/refresh
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| refreshToken | String | 是 | @NotBlank | 刷新令牌 |

**请求示例**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**响应参数**

同 [用户登录](#2-用户登录) 的 `LoginResponse`

---

### 4. 用户登出

用户登出，使当前 token 失效。

**请求**

```
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": "登出成功"
}
```

---

### 5. 注销账户

注销当前用户账户，此操作不可恢复。

**请求**

```
POST /api/auth/delete-account
Authorization: Bearer {accessToken}
```

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": "账户已注销"
}
```

---

## 用户模块 (User)

**基础路径**: `/api/user`

### 1. 获取用户信息

获取当前登录用户的详细信息。

**请求**

```
GET /api/user/profile
Authorization: Bearer {accessToken}
```

**响应参数 - UserProfileResponse**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Integer | 用户ID |
| phoneNumber | String | 手机号码（脱敏） |
| nickName | String | 昵称 |
| bio | String | 个人简介 |
| avatarUrl | String | 头像地址 |
| isAdmin | Boolean | 是否管理员 |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": {
    "id": 1,
    "phoneNumber": "138****5678",
    "nickName": "张三",
    "bio": "这是我的个人简介",
    "avatarUrl": "https://example.com/avatar.jpg",
    "isAdmin": false
  }
}
```

---

### 2. 更新用户信息

更新用户的昵称、个人简介、头像URL等信息。

**请求**

```
POST /api/user/profile
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| nickName | String | 否 | @Size(max=50) | 昵称 |
| bio | String | 否 | @Size(max=500) | 个人简介 |
| avatarUrl | String | 否 | @Size(max=500) | 头像URL |

**请求示例**

```json
{
  "nickName": "新昵称",
  "bio": "这是我的新简介",
  "avatarUrl": "https://example.com/new-avatar.jpg"
}
```

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": "更新成功"
}
```

---

### 3. 上传头像

上传用户头像图片，支持 jpg、png 等格式，最大 5MB。

**请求**

```
POST /api/user/avatar
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 头像图片文件 |

**响应参数**

| 字段 | 类型 | 说明 |
|------|------|------|
| data | String | 头像URL |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": "https://example.com/avatars/123456.jpg"
}
```

---

### 4. 修改手机号

修改用户绑定的手机号，需要先向新手机号发送验证码。

**请求**

```
POST /api/user/change-phone
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| newPhoneNumber | String | 是 | @NotBlank, @Pattern(^1[3-9]\\d{9}$) | 新手机号 |
| verificationCode | String | 是 | @NotBlank | 验证码 |

**请求示例**

```json
{
  "newPhoneNumber": "13987654321",
  "verificationCode": "654321"
}
```

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": "手机号修改成功"
}
```

---

## 消息分享模块 (Share)

**基础路径**: `/api/message/share`

### 1. 创建分享

选择要分享的消息ID列表，生成分享ID。

**请求**

```
POST /api/message/share/create
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| messageIds | List\<String\> | 是 | @NotNull | 要分享的消息ID列表 |

**请求示例**

```json
{
  "messageIds": ["msg_001", "msg_002", "msg_003"]
}
```

**响应参数 - ShareResponse**

| 字段 | 类型 | 说明 |
|------|------|------|
| shareId | String | 分享ID |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": {
    "shareId": "share_abc123def456"
  }
}
```

---

### 2. 获取分享详情

通过分享ID查询之前存储的消息集合。

**请求**

```
GET /api/message/share/get/{shareId}
```

**路径参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| shareId | String | 是 | 分享ID |

**响应参数 - ShareDetailResponse**

| 字段 | 类型 | 说明 |
|------|------|------|
| shareId | String | 分享ID |
| createdBy | String | 分享人 |
| createdAt | Date | 创建时间 |
| messages | List\<MessageDTO\> | 消息集合 |

**MessageDTO 结构**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 消息ID |
| query | String | 查询内容（用户问题） |
| answer | String | 回答内容（AI回复） |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": {
    "shareId": "share_abc123def456",
    "createdBy": "user_1",
    "createdAt": "2024-01-28T10:30:00",
    "messages": [
      {
        "id": "msg_001",
        "query": "什么是合同违约？",
        "answer": "合同违约是指合同当事人一方或双方不履行或不完全履行合同约定的义务..."
      }
    ]
  }
}
```

---

## 咨询模板模块 (Templates)

**基础路径**: `/api/templates`

### 1. 随机获取咨询模板

随机获取指定数量的启用状态的咨询模板，支持按 Agent 类型筛选。

**请求**

```
GET /api/templates/random
Authorization: Bearer {accessToken}
```

**请求参数（Query）**

| 字段 | 类型 | 必填 | 校验规则 | 默认值 | 说明 |
|------|------|------|----------|--------|------|
| limit | Integer | 否 | @Min(1) @Max(20) | - | 返回数量 |
| agentType | Integer | 否 | - | - | Agent类型 |

**响应参数**

| 字段 | 类型 | 说明 |
|------|------|------|
| data | List\<ConsultTemplates\> | 咨询模板列表 |

**ConsultTemplates 结构**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 模板ID |
| question | String | 问题标题 |
| agentType | String | Agent类型 |
| category | String | 分类 |
| status | Integer | 状态（0-禁用，1-启用） |
| sortOrder | Integer | 排序号 |
| createdAt | Long | 创建时间（毫秒时间戳） |
| updatedAt | Long | 更新时间（毫秒时间戳） |

**响应示例**

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": [
    {
      "id": "tpl_001",
      "question": "如何处理合同违约？",
      "agentType": "1",
      "category": "合同纠纷",
      "status": 1,
      "sortOrder": 1,
      "createdAt": 1706428800000,
      "updatedAt": 1706428800000
    }
  ]
}
```

---

## 通用响应格式

### Result\<T\> 结构

```json
{
  "code": 1000,
  "message": "操作成功",
  "data": T
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码 |
| message | String | 状态信息 |
| data | T | 响应数据 |

### 分页结构 Page\<T\>

```json
{
  "records": [],
  "total": 0,
  "size": 10,
  "current": 1,
  "pages": 0
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| records | List\<T\> | 数据列表 |
| total | Long | 总记录数 |
| size | Long | 每页大小 |
| current | Long | 当前页码 |
| pages | Long | 总页数 |

