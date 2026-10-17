# 建材集采平台

这是一个面向建筑材料采购场景的前后端分离基线工程，包含：

- `admin-web`：Vue 3 + Element Plus 运营管理后台
- `backend`：Spring Boot + MyBatis-Plus + JWT/Redis 配置骨架
- `miniapp`：uni-app 采购端页面骨架
- `db/init.sql`：MySQL 8.0 初始化脚本（12 张核心业务表）

## 本地启动

1. 启动依赖：`docker compose up -d mysql redis minio`
2. 启动后端：`cd backend && mvn -s ../maven-settings.xml spring-boot:run`
3. 启动管理端：`cd admin-web && npm install && npm run dev`
4. 推荐使用 HBuilderX 打开 `miniapp`，运行到微信小程序；如使用微信开发者工具，请导入 uni-app 编译生成的 `dist/build/mp-weixin` 目录。`miniapp` 内的 `.vue` 文件是 uni-app 源码，根目录同时提供了一套轻量原生页面兼容层，便于直接预览配置和页面流程。

管理端开发环境默认通过 Vite 代理访问 `http://localhost:8080/api`；生产构建默认使用同源 `/api`，由 Nginx 反向代理到后端，也可以通过 `VITE_API_BASE_URL` 覆盖。

管理端生产部署：执行 `cd admin-web && npm ci && npm run build`，将 `admin-web/dist` 内容复制到 Nginx 静态目录，并使用 `deploy/nginx/buildmart.conf`。该配置包含 Vue Router 刷新回退、静态资源缓存和 `/api` 反向代理，完整步骤见 `deploy/nginx/README.md`。

后端 API 文档：启动后访问 `http://localhost:8080/api/swagger-ui.html`，OpenAPI JSON 地址为 `http://localhost:8080/api/v3/api-docs`。后端接口自动化测试位于 `backend/src/test`，执行 `cd backend && mvn -s ../maven-settings.xml test`。

如果本机 8080 已被其他后端进程占用，可以使用 `mvn -s ../maven-settings.xml spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"` 启动临时实例，并将管理端的 `VITE_API_BASE_URL` 设为 `http://localhost:8081/api`。

初始化管理员为 `admin / password`，首次部署后请立即修改密码并替换 `JWT_SECRET`。

首次初始化已有数据库时，请使用 UTF-8 客户端执行：`mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -ubuildmart -pbuildmart buildmart < db/init.sql`。脚本会补充演示商品、分类和阶梯价格。

## 当前接口

管理端接口：`POST /api/admin/auth/login`、`GET/POST /api/admin/products`、`PATCH /api/admin/products/{id}/status`、`GET /api/admin/orders`、`POST /api/admin/orders/{id}/ship`、`GET /api/admin/inquiries`。

服务检查：`GET /api/health`。登录接口是 `POST`，不能直接在浏览器地址栏用 `GET` 打开。

小程序接口：`POST /api/miniapp/auth/login`、`GET /api/miniapp/catalog/categories`、`GET /api/miniapp/catalog/products`、`GET /api/miniapp/catalog/products/{id}`、`GET/POST /api/miniapp/cart`、`PUT/DELETE /api/miniapp/cart/{id}`、`GET/POST/DELETE /api/miniapp/addresses`、`POST/GET /api/miniapp/orders`、`GET /api/miniapp/orders/{id}`、`POST /api/miniapp/orders/{id}/cancel`、`POST /api/miniapp/orders/{id}/pay`（微信支付 JSAPI 下单）、`POST /api/miniapp/orders/{id}/confirm`（确认收货）、`POST /api/miniapp/inquiries`。登录请求体只使用微信 `wx.login/uni.login` 返回的一次性 `code`（以及可选昵称、手机号），后端通过微信 `code2Session` 换取 `openid/unionid`，再幂等创建或更新 `sys_user` 并签发 JWT。

微信支付闭环：小程序 `POST /api/miniapp/orders/{id}/pay` 返回 `wx.requestPayment` 参数；支付结果通知 `POST /api/pay/wechat/notify`、退款结果通知 `POST /api/pay/wechat/refund-notify`（公开路径，由微信支付 V3 回调验签保证安全；按微信交易号幂等，重复通知不会重复入账）；管理端退款 `POST /api/admin/orders/{id}/refund`。启用支付需配置环境变量 `WECHAT_PAY_ENABLED=true`、`WECHAT_PAY_MCHID`、`WECHAT_PAY_SERIAL_NO`、`WECHAT_PAY_PRIVATE_KEY_PATH`（商户 API 私钥 apiclient_key.pem）、`WECHAT_PAY_API_V3_KEY`、`WECHAT_PAY_NOTIFY_URL`（公网 HTTPS，指向 `/api/pay/wechat/notify`），退款通知地址自动派生为同路径的 `/refund-notify`。未配置时支付接口明确报错，不伪装成功。

订单状态机：`PENDING_PAYMENT`（待支付，超时由定时任务按 `order.auto_cancel_minutes` 设置自动关单并回补库存，默认 30 分钟）→ 支付成功 `PENDING_SHIPMENT`（待发货）→ 管理端发货 `PENDING_RECEIPT`（待收货，配送订单需录入物流公司/运单号，自提订单自动生成核销码）→ 用户确认收货 `COMPLETED`；待支付可用户取消 `CANCELLED`，待发货可管理端退款 `REFUNDED`。所有状态流转使用数据库 CAS 更新，防止并发重复流转和库存重复回补。

登录成功响应为 `{"code":0,"message":"ok","data":{"userId":1,"nickname":"微信用户","phone":null,"token":"..."}}`。登录失败时 `data` 为 `null`，`code` 使用稳定的负数错误码：`-1001`（缺少 code）、`-1002`（code 已提交）、`-1003`（code 无效或过期）、`-1101`（微信配置错误）、`-1102`（微信服务不可用）、`-1103`（微信响应格式异常）、`-1104`（微信未返回用户标识）、`-1201`（用户被禁用）、`-1202`（用户信息保存失败）。小程序应清理旧 token 后重新调用 `wx.login`，不要重复提交同一个 code。

小程序页面已通过 `miniapp/common/api.js`（uni-app）和 `miniapp/common/wx-api.js`（原生兼容层）接入上述接口。小程序启动时会调用微信登录并缓存 JWT；目录接口可匿名访问，购物车、地址、订单、询价和“我的”页面使用该登录会话。开发环境可以在小程序启动前写入 `apiBaseUrl` 覆盖默认地址，例如：

```js
uni.setStorageSync('apiBaseUrl', 'http://192.168.1.20:8080/api/miniapp')
```

真机调试时请使用电脑局域网 IP，并在微信开发者工具中关闭校验或为接口配置 HTTPS 合法域名；生产环境应替换为 HTTPS 域名。

启动后端前确认微信小程序凭证。项目内置的本地演示凭证对应 AppID `wxa01fc7d2199a6779`，也可以用环境变量覆盖：

```powershell
$env:WECHAT_APP_ID = '你的微信小程序 AppID'
$env:WECHAT_APP_SECRET = '你的微信小程序 AppSecret'
# 如果后端主机直连 api.weixin.qq.com 被网络策略阻断，可配置 HTTP 代理（示例为本机 Mihomo/Clash）
$env:WECHAT_PROXY_HOST = '127.0.0.1'
$env:WECHAT_PROXY_PORT = '7897'
```

设置或修改凭证后需要重启后端进程。

`WECHAT_APP_ID` 必须与生成 `wx.login` code 的小程序 AppID 一致；当前 `miniapp/manifest.json` 和 `miniapp/project.config.json` 已统一为 `wxa01fc7d2199a6779`。后端还支持通过 `WECHAT_API_BASE_URL`、`WECHAT_CONNECT_TIMEOUT`、`WECHAT_READ_TIMEOUT`、`WECHAT_PROXY_HOST` 和 `WECHAT_PROXY_PORT` 调整微信接口地址、超时和出网代理。修改凭证或代理后必须重启后端。生产环境请使用环境变量并轮换演示凭证。支付下单、退款、物流和 OSS/MinIO 上传仍需接入对应服务凭证，接口不会用演示数据伪装成已完成。

开发联调时使用真实微信登录；服务端显式传入 `openid` 的调试兼容路径默认关闭，只有启动前设置 `WECHAT_DEBUG_OPENID_ENABLED=true` 才会开放，生产环境必须保持关闭。小程序请求使用登录后缓存的 JWT 会话。

## 业务约定

金额统一使用分（`Long`），重量和数量统一使用 `BigDecimal`；商品的阶梯价按采购数量从小到大匹配；订单状态流转由服务层控制，控制器只接收业务动作。微信支付、企业账期和对象存储的密钥仅通过环境变量注入。
=======
# WX
小程序

