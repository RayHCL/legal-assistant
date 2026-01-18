# Docker Compose 服务说明

本目录包含法律咨询助手系统所需的外部服务的Docker Compose配置。

## 包含的服务

1. **MySQL 8.0** - 主数据库
   - 端口: 3306
   - 默认数据库: legal_assistant
   - 默认root密码: root123456
   - 默认用户: legal_user / legal_pass123

2. **Redis 7** - 缓存服务
   - 端口: 6379
   - 默认无密码

3. **MinIO** - 对象存储服务
   - API端口: 9000
   - Console端口: 9001
   - 默认账号: minioadmin / minioadmin
   - 自动创建bucket: legal-assistant

4. **etcd** - Milvus依赖的元数据存储
   - 端口: 2379 (内部使用)

5. **Milvus 2.3.4** - 向量数据库
   - gRPC端口: 19530
   - HTTP端口: 9091

## 快速开始

### 1. 启动所有服务

```bash
docker-compose up -d
```

### 2. 查看服务状态

```bash
docker-compose ps
```

### 3. 查看服务日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f minio
docker-compose logs -f milvus
```

### 4. 停止所有服务

```bash
docker-compose down
```

### 5. 停止并删除数据卷(谨慎操作)

```bash
docker-compose down -v
```

## 服务访问地址

- **MySQL**: `localhost:3306`
- **Redis**: `localhost:6379`
- **MinIO API**: `http://localhost:9000`
- **MinIO Console**: `http://localhost:9001`
- **Milvus**: `localhost:19530`

## 初始化说明

### MySQL

首次启动后，MySQL会自动创建 `legal_assistant` 数据库。如果需要执行初始化SQL脚本，可以将SQL文件放在 `init-sql/` 目录下，容器启动时会自动执行。

### MinIO

首次启动后，MinIO会自动创建 `legal-assistant` bucket。可以通过Console界面(http://localhost:9001)进行管理。

### Milvus

Milvus启动需要一些时间，首次启动会进行初始化。可以通过健康检查接口验证: `http://localhost:9091/healthz`

## 数据持久化

所有服务的数据都存储在Docker volumes中，即使删除容器，数据也会保留。数据卷名称：

- `mysql_data` - MySQL数据
- `redis_data` - Redis数据
- `minio_data` - MinIO数据
- `etcd_data` - etcd数据
- `milvus_data` - Milvus数据

## 配置修改

### 修改MySQL密码

1. 编辑 `docker-compose.yaml` 中的 `MYSQL_ROOT_PASSWORD` 和 `MYSQL_PASSWORD`
2. 如果容器已启动，需要删除数据卷重新初始化：
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

### 修改Redis密码

1. 编辑 `docker-compose.yaml` 中redis的command，添加 `--requirepass your_password`
2. 重启服务：
   ```bash
   docker-compose restart redis
   ```

### 修改MinIO账号

1. 编辑 `docker-compose.yaml` 中的 `MINIO_ROOT_USER` 和 `MINIO_ROOT_PASSWORD`
2. 重启服务：
   ```bash
   docker-compose restart minio
   ```

## 健康检查

所有服务都配置了健康检查，可以通过以下命令查看：

```bash
docker-compose ps
```

状态显示为 `healthy` 表示服务正常运行。

## 常见问题

### 1. 端口冲突

如果本地已有服务占用相同端口，可以修改 `docker-compose.yaml` 中的端口映射，例如：
```yaml
ports:
  - "3307:3306"  # 将MySQL映射到3307端口
```

### 2. Milvus启动失败

Milvus依赖etcd和minio，确保这两个服务先启动。如果启动失败，检查日志：
```bash
docker-compose logs milvus
```

### 3. 数据备份

备份MySQL数据：
```bash
docker-compose exec mysql mysqldump -u root -proot123456 legal_assistant > backup.sql
```

恢复MySQL数据：
```bash
docker-compose exec -T mysql mysql -u root -proot123456 legal_assistant < backup.sql
```

## 生产环境建议

1. **修改默认密码**: 所有服务的默认密码都应该修改为强密码
2. **启用Redis密码**: 生产环境建议为Redis设置密码
3. **配置防火墙**: 只开放必要的端口
4. **定期备份**: 设置定期备份MySQL和MinIO数据
5. **监控告警**: 配置服务监控和告警
6. **资源限制**: 为容器设置适当的CPU和内存限制
