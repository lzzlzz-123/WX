# 生产部署清单

本文档是把本系统从开发机搬到正式环境的完整操作清单。按顺序执行，每步完成后再进行下一步。

## 0. 前置资质（周期最长，最先启动）

| 事项 | 办理入口 | 周期 |
|---|---|---|
| 域名购买 + ICP 备案 | 云服务商备案系统 | 2–4 周 |
| 小程序备案（工信部） | 微信公众平台 → 设置 → 备案 | 1–2 周 |
| 营业执照 / 经营资质 | 市场监管部门 | 视情况 |
| 微信支付商户号（如需在线收款） | pay.weixin.qq.com | 1 周左右 |
| 微信 AppSecret 重置（旧值已进 git 历史，视为泄露） | 微信公众平台 → 开发管理 → 开发设置 | 即时 |

## 1. 服务器准备

推荐配置：2 核 4G、CentOS/Ubuntu、安全组只放行 80/443/22。

需安装：JDK 17+、MySQL 8、Redis 6+（可选但建议）、Nginx。

## 2. 环境变量（生产必配）

`application-prod.yml` 对以下变量**无默认值**，缺失即启动失败：

| 变量 | 说明 | 示例 |
|---|---|---|
| `DB_URL` | 数据库连接 | `jdbc:mysql://127.0.0.1:3306/buildmart?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` | 数据库账号（不要使用 root，单独建只授 buildmart 库权限的账号） | `buildmart` |
| `DB_PASSWORD` | 数据库密码（强密码） | — |
| `REDIS_HOST` | Redis 地址 | `127.0.0.1` |
| `WECHAT_APP_ID` | 小程序 AppID | `wx...` |
| `WECHAT_APP_SECRET` | **重置后的新** AppSecret | — |
| `JWT_SECRET` | JWT 签名密钥（32 位以上随机串） | `openssl rand -base64 48` 生成 |

可选：`UPLOAD_DIR`（头像存储目录，默认 uploads）、`SMS_CODE_TTL_SECONDS` 等。

## 3. 数据库初始化

```bash
mysql -u root -p < db/init.sql
```

**不要导入 `db/seed-demo.sql`（演示数据）。** 然后立即修改管理员默认密码：

```sql
-- 生成新密码的 BCrypt 哈希后执行（可用后台注册接口或在线 BCrypt 工具生成）
UPDATE sys_admin SET password_hash = '<新哈希>' WHERE username = 'admin';
```

## 4. 启动后端

```bash
java -jar buildmart-backend-0.1.0.jar --spring.profiles.active=prod
```

prod profile 生效后自动获得以下安全行为：
- 缺少任何敏感环境变量直接启动失败
- Swagger UI / api-docs 关闭
- 短信验证码回显关闭（`test-echo=false`）
- openid 调试登录关闭

建议用 systemd 托管进程并配置开机自启与崩溃重启。

## 5. Nginx（HTTPS + 静态资源 + 反向代理）

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate     /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    # 管理后台静态文件
    root /opt/buildmart/admin-web;
    location /assets/ { expires 30d; add_header Cache-Control "public, immutable"; }
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 5m;
    }
    location / { try_files $uri $uri/ /index.html; add_header Cache-Control "no-cache"; }
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}
```

部署管理端：`cd admin-web && npm run build`，把 `dist/*` 拷到 `/opt/buildmart/admin-web`，`nginx -s reload`。**每次前端构建后都要重复这一步。**

## 6. 微信小程序后台配置

1. **合法域名**：开发管理 → 服务器域名 → request 合法域名添加 `https://your-domain.com`（必须 HTTPS + 已备案）。
2. **用户隐私保护指引**：按 `docs/privacy-policy.md` 配置收集的信息类型（昵称头像、手机号、收货地址、发票信息）。
3. **小程序代码**：把 `miniapp/common/wx-api.js` 中 `DEVICE_BASE_URL` / 默认地址改为 `https://your-domain.com/api/miniapp`，开发者工具上传 → 提交审核 → 发布。
4. **AppSecret**：确认使用的是重置后的新密钥（配置在服务器环境变量里）。

## 7. 上线后运维

- **数据库备份**：每日 `mysqldump` 定时任务，保留 7–30 天，定期恢复到测试库验证。
- **日志**：后端 stdout 重定向到文件并做 logrotate；关注 ERROR 级别。
- **监控**：至少配置磁盘、内存、进程存活告警（可用云厂商基础监控）。
- **头像上传目录**：`UPLOAD_DIR` 纳入备份。

## 8. 上线前最后一遍检查

- [ ] 环境变量全部配置，prod profile 启动成功
- [ ] 微信 AppSecret 已重置且与服务器配置一致
- [ ] 管理员默认密码已修改
- [ ] 数据库无演示数据
- [ ] 合法域名配置完成，小程序真机全流程走通（登录→加购→下单→询价→地址→发票）
- [ ] Swagger 访问返回 404（`https://your-domain.com/api/swagger-ui.html`）
- [ ] 未登录请求受保护接口返回未登录错误（验证 X-User-Id 头部回退已移除）
- [ ] HTTPS 证书有效，HTTP 自动跳转 HTTPS
- [ ] 数据库备份任务已跑通一次
