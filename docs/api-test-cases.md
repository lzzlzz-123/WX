# 接口测试案例

后端启动后，默认地址为 `http://localhost:8080/api`。以下案例使用 `curl`，Windows PowerShell 可直接执行；Linux/macOS 将反斜杠换行符改为反斜杠续行即可。

## 1. 健康检查

```powershell
curl.exe -i http://localhost:8080/api/health
```

预期：HTTP 200，响应中的 `code` 为 `0`，`data.status` 为 `UP`。

## 2. 管理员登录

```powershell
$login = curl.exe -s -X POST http://localhost:8080/api/admin/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"password"}' | ConvertFrom-Json
$token = $login.data.token
$token
```

预期：`code=0` 且返回非空 JWT。初始化数据库中的默认账号为 `admin / password`，生产环境应立即修改。

## 3. 管理端查询商品

```powershell
curl.exe -i "http://localhost:8080/api/admin/products?page=1&size=20&keyword=水泥" `
  -H "Authorization: Bearer $token"
```

预期：`code=0`，`data.records` 为商品数组，`data.total` 为总数。

## 4. 小程序查询目录

```powershell
curl.exe -i "http://localhost:8080/api/miniapp/catalog/products?page=1&size=20&keyword=水泥"
curl.exe -i http://localhost:8080/api/miniapp/catalog/categories
```

预期：`code=0`；目录商品接口只返回 `status=1` 的商品。

## 5. 小程序登录与购物车

真实小程序登录必须提交 `wx.login` 返回的一次性 `code`，不能把 `openid` 当作正式登录凭证：

```powershell
$wxCode = Read-Host '粘贴本次 wx.login 返回的 code'
$payload = @{code=$wxCode; nickname='接口测试用户'; phone='13800000000'} | ConvertTo-Json -Compress
$userLogin = curl.exe -s -X POST http://localhost:8080/api/miniapp/auth/login `
  -H "Content-Type: application/json" `
  -d $payload | ConvertFrom-Json
$userId = $userLogin.data.userId
$userLogin

curl.exe -i http://localhost:8080/api/miniapp/cart -H "X-User-Id: $userId"
curl.exe -i -X POST http://localhost:8080/api/miniapp/cart `
  -H "Content-Type: application/json" -H "X-User-Id: $userId" `
  -d '{"productId":1,"quantity":2}'
```

预期：登录返回用户 ID；购物车查询返回数组；商品存在且库存足够时，添加接口返回购物车项。

本地没有真实微信 code 时，可在启动后端前显式开启调试旁路：

```powershell
$env:WECHAT_DEBUG_OPENID_ENABLED = 'true'
mvn -s ..\maven-settings.xml spring-boot:run
```

然后才可以提交 `{"openid":"local-test-openid"}`。该旁路只用于本地联调，生产环境不要开启。

## 6. 自动化接口测试

这些测试不连接 MySQL 或 Redis，使用 Spring MVC 切片和 Mockito 验证 HTTP 契约：

```powershell
cd backend
mvn -s ..\maven-settings.xml test
```

覆盖案例：

- `HealthControllerTest`：健康检查返回 `UP`。
- `AdminAuthControllerTest`：正确账号密码返回 JWT，空账号密码返回 400。
- `CatalogControllerTest`：目录查询固定以在售状态 `1` 调用服务，并验证分页响应结构。
- `MiniappAuthControllerTest`：code 换 openid、缺少 code、生产环境拒绝 openid 旁路。
- `WechatAuthServiceTest`：兼容微信非标准 Content-Type、错误码映射、并发 code 合并和异常响应。
- `UserServiceTest`：首次建用户、并发唯一键冲突回查、资料更新和禁用用户拦截。

## 7. OpenAPI 文档

- Swagger UI：`http://localhost:8080/api/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/api/v3/api-docs`
