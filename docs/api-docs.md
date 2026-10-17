# BuildMart 接口文档

> 服务：`buildmart-backend`，上下文路径 `/api`（本地默认 `http://localhost:8080/api`）。
> 在线 Swagger UI：`/api/swagger-ui.html`；OpenAPI JSON：`/api/v3/api-docs`。
>
> **统一响应结构**
> ```json
> { "code": 0, "message": "ok", "data": {} }
> ```
> `code=0` 成功；`-1` 业务失败；`401` 未登录/令牌失效（HTTP 状态码同为 401）。
>
> **统一分页结构**（data 部分）
> ```json
> { "records": [], "total": 100, "page": 1, "size": 20 }
> ```
>
> **认证方式**
> - 管理端：`Authorization: Bearer <admin JWT>`，保护 `/admin/**`（除 `/admin/auth/login`）。
> - 小程序端：`Authorization: Bearer <user JWT>`，保护 `/miniapp/**`（除登录与商品目录）。
>   身份只认 JWT；旧的 `X-User-Id` 调试头已于 2026-10-11 移除（防止越权），未携带有效 JWT 一律返回未登录。

---

## 一、服务检查

### GET /health
健康检查，无需登录。

**响应 data**
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| status | string | 固定 `UP` |
| service | string | 服务名 `buildmart-backend` |
| time | string | 服务器时间（ISO-8601） |

---

## 二、小程序端接口（/miniapp）

### 2.1 认证

#### POST /miniapp/auth/login — 微信静默登录
无需登录。`code` 经 code2Session 换 openid；新用户自动建档，老用户续期。

**请求体**
| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | string | 是 | `wx.login` 返回的临时 code |
| nickname | string | 否 | 昵称 |
| phone | string | 否 | 手机号 |
| openid | string | 否 | 仅服务端调试（`WECHAT_DEBUG_OPENID_ENABLED=true` 才可用） |

**响应 data**
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userId | long | 用户 id |
| nickname | string | 昵称 |
| phone | string | 手机号 |
| avatarUrl | string | 头像地址 |
| token | string | 用户 JWT（默认 24 小时） |

#### POST /miniapp/auth/login-phone — 手机号一键登录/注册
**请求体**：`loginCode`（wx.login code）、`phoneCode`（getPhoneNumber 动态令牌）、`nickname`（可选）。
**响应**：同 login。

#### POST /miniapp/auth/sms-code — 发送短信验证码（测试短信服务）
无需登录。测试服务不真实发送短信：验证码打印到服务端日志，并在响应 `testCode` 字段回显（生产环境设 `SMS_TEST_ECHO=false` 后接入真实短信网关）。

**请求体**：`phone`（必填，大陆 11 位手机号）。
**响应 data**
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| ttlSeconds | int | 验证码有效期（默认 300 秒） |
| resendSeconds | int | 重发间隔（默认 60 秒，限流） |
| testCode | string | 测试模式回显的 6 位验证码 |

限流：同一手机号 60 秒内只能发一次；验证码一次性使用，验证成功即销毁。

#### POST /miniapp/auth/login-sms — 短信验证码登录/注册
无需登录。手机号首次使用自动注册（openid 留空，后续可绑定微信）；老手机号直接登录复用原账号。

**请求体**：`phone`（必填）、`code`（必填，6 位数字）、`nickname`（可选）。
**响应**：同 login（token 为用户 JWT）。
常见错误：`验证码不正确` / `验证码已过期，请重新获取` / `手机号格式不正确`。

#### GET /miniapp/auth/profile — 查询当前用户资料
需登录。响应 data：`userId / nickname / phone / avatarUrl`。

#### PUT /miniapp/auth/profile — 更新当前用户资料
需登录。请求体：`nickname`、`avatarUrl`（均可选，未提供字段保持原值）。响应同 profile。

#### POST /miniapp/auth/avatar — 上传头像
需登录。`multipart/form-data`，字段名 `file`（wx.chooseAvatar 临时文件，≤5MB）。
响应 data 同 profile，`avatarUrl` 为新地址（通过 `/api/files/**` 访问）。

### 2.2 商品目录（无需登录）

#### GET /miniapp/catalog/categories — 商品分类
响应 data：分类数组，按 sortNo 升序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 分类 id |
| parentId | long | 父分类 id |
| name | string | 分类名 |
| iconUrl | string | 图标 |
| sortNo | int | 排序号 |

#### GET /miniapp/catalog/products — 分页查询在售商品
**Query**：`page`（默认 1）、`size`（默认 20）、`keyword`（可选，匹配名称/SKU）。仅返回在售商品。走 Redis 分页缓存（60s，版本号换 key）。

#### GET /miniapp/catalog/products/{id} — 商品详情
响应 data：Product 对象；商品不存在返回 `data=null`。走 Redis 详情缓存（10min）+ 负缓存（30s）。

**Product 主要字段**：`id / categoryId / sku / name / brand / grade / specification / unit / basePrice / stock / minOrderQty / soldQty / deliveryModes / coverUrl / detail / status / createdAt / updatedAt`

### 2.3 购物车（需登录）

> 服务端 Redis 缓存 `cache:cart:{userId}`（60s），写操作即失效；小程序端另有 30s 本地快照缓存，见 `docs/redis-design.md`。

#### GET /miniapp/cart — 查询购物车
响应 data：CartItem 数组，按更新时间倒序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 购物车行 id |
| productId | long | 商品 id |
| quantity | decimal | 数量 |
| selected | int | 1 选中 / 0 未选中 |

#### POST /miniapp/cart — 加入购物车
请求体：`productId`（long，必填）、`quantity`（decimal，必填，>0）。
同一商品重复加购自动累加数量。商品不存在/已下架返回业务错误。

#### PUT /miniapp/cart/{id} — 更新购物车行
请求体：`quantity`（>0）、`selected`（1/0），均为可选。

#### DELETE /miniapp/cart/{id} — 删除购物车行

### 2.4 订单（需登录）

#### POST /miniapp/orders — 创建采购订单
**请求体**
| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| items | array | 是 | 明细：`[{ "productId": 1, "quantity": 10 }]` |
| receiverName | string | 否 | 收货人 |
| receiverPhone | string | 否 | 收货电话 |
| receiverAddress | string | 否 | 收货地址 |
| deliveryMode | string | 否 | 配送方式，默认 CITY_DELIVERY |
| remark | string | 否 | 备注 |

服务端按阶梯价实时计价、事务内原子扣减库存（不足则整单失败），初始状态 `PENDING_PAYMENT`、支付方式 `WECHAT`。响应 data：完整订单对象。

#### GET /miniapp/orders — 分页查询我的订单
Query：`page / size / status`（status 可选：PENDING_PAYMENT / PENDING_SHIPMENT / SHIPPED / COMPLETED / CANCELLED）。

#### GET /miniapp/orders/{id} — 订单详情
响应 data：`{ "order": {...}, "items": [...] }`，items 含商品快照（productName / specification / unit / quantity / unitPrice / amount）。仅可查本人订单。

### 2.5 收货地址（需登录）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /miniapp/addresses | 地址列表 |
| POST | /miniapp/addresses | 保存（有 id 更新、无 id 新增），请求体为 UserAddress：`receiverName* / receiverPhone* / province / city / district / detailAddress* / isDefault` |
| DELETE | /miniapp/addresses/{id} | 删除地址 |

### 2.6 询价（需登录）

#### POST /miniapp/inquiries — 提交询价单
**请求体**
| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| productName | string | 是 | 商品名称 |
| specification | string | 否 | 规格 |
| quantity | decimal | 是 | 数量（>0） |
| unit | string | 否 | 单位 |
| expectedPrice | decimal | 否 | 期望单价 |
| deliveryAddress | string | 否 | 交付地址 |
| deadline | date | 否 | 交付截止日期（yyyy-MM-dd） |
| remark | string | 否 | 备注 |

服务端生成 `inquiryNo`，初始状态 `PENDING_QUOTE`。响应 data：完整询价单。

---

## 三、管理端接口（/admin）

> 除 `/admin/auth/login` 外均需 `Authorization: Bearer <admin JWT>`。

### 3.1 认证

#### POST /admin/auth/login — 管理员登录
**请求体**：`username`（必填）、`password`（必填）。
**响应 data**：`token`（JWT）、`realName`、`roleCode`（SUPER_ADMIN / OPERATOR）。
初始账号 `admin`。账号禁用或密码错误返回「用户名或密码错误」。

### 3.2 首页概览

#### GET /admin/dashboard/summary — 首页实时统计
**响应 data**
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| activeProductCount | long | 在售商品数 |
| lowStockCount | long | 低库存商品数（stock ≤ minOrderQty） |
| orderCount | long | 订单总数 |
| pendingShipmentCount | long | 待发货订单数 |
| pendingInquiryCount | long | 待报价询价数 |
| orderAmount | decimal | 订单应付总额 |

### 3.3 数据统计与趋势（新增）

#### GET /admin/statistics/overview — 经营数据总览
全部指标实时查库，不经过缓存；销售额统计剔除已取消订单。

**响应 data**
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| totalOrderCount | long | 累计订单数 |
| todayOrderCount | long | 今日订单数 |
| totalSalesAmount | decimal | 累计销售额（应付总额，剔除 CANCELLED） |
| todaySalesAmount | decimal | 今日销售额 |
| pendingShipmentCount | long | 待发货订单数 |
| pendingPaymentCount | long | 待支付订单数 |
| totalCustomerCount | long | 累计客户数 |
| todayNewCustomerCount | long | 今日新增客户数 |
| activeProductCount | long | 在售商品数 |
| lowStockCount | long | 低库存商品数 |
| pendingInquiryCount | long | 待报价询价数 |

#### GET /admin/statistics/trend — 经营趋势
**Query**
| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| dimension | string | day | 分桶维度：`day` / `week` / `month` |
| periods | int | 30 | 返回桶数（1-90，含当期，按时间升序） |

**响应 data**
```json
{
  "dimension": "day",
  "points": [
    { "bucket": "2026-10-11", "orderCount": 3, "salesAmount": 12800.00, "newCustomerCount": 1 }
  ]
}
```
- bucket 格式：day=`yyyy-MM-dd`，week=ISO 周 `xxxx-Www`（如 2026-41），month=`yyyy-MM`。
- 无数据的桶自动补零，保证图表连续。
- salesAmount 同样剔除已取消订单。

### 3.4 商品管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /admin/products | 分页查询。Query：`page / size / keyword / status` |
| GET | /admin/products/{id} | 商品详情 |
| POST | /admin/products | 新增/更新。请求体：`{ "product": {...}, "tiers": [{minQty, price}] }`；product 带 id 为更新。阶梯价整体替换 |
| PATCH | /admin/products/{id}/status?value=1 | 上/下架（1 上架，0 下架） |

商品写操作自动失效详情缓存并递增列表缓存版本号。

### 3.5 订单管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /admin/orders | 分页查询。Query：`page / size / status` |
| POST | /admin/orders/{id}/ship | 发货（PENDING_SHIPMENT → SHIPPED） |

### 3.6 询价管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /admin/inquiries | 分页查询询价单。Query：`page / size / status` |

### 3.7 客户管理（新增）

#### GET /admin/customers — 分页查询客户
Query：`page / size / keyword`（匹配昵称/手机号/公司名）` / status`（1 正常 / 0 禁用）。
响应：分页的 SysUser 列表。

#### GET /admin/customers/{id} — 客户详情
**响应 data**
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user | object | SysUser 完整资料 |
| orderCount | long | 该客户订单总数 |
| totalSpent | decimal | 累计消费（剔除已取消订单） |
| lastOrderAt | datetime | 最近下单时间（无订单为 null） |

#### PATCH /admin/customers/{id}/status?value=0 — 启用/禁用客户
`value`：1 启用，0 禁用。禁用后该客户登录小程序将被拒绝。

#### PATCH /admin/customers/{id}/credit?value=5000 — 调整信用额度
`value`：信用额度（元），不能为负数。

### 3.8 系统设置（新增）

#### GET /admin/settings — 读取全部设置
响应 data：`{ "shop.name": "BuildMart 建材商城", "order.default_freight": "0.00", ... }`
（key → value 的对象，值均为字符串）。走 Redis 缓存 `cache:settings:all`（10min）。

#### PUT /admin/settings — 保存设置
**请求体**：key → value 的 JSON 对象，按键 upsert（不存在则新增，存在则更新值）。
```json
{ "shop.contact_phone": "400-111-2222", "order.auto_cancel_minutes": "15" }
```
key 长度 ≤100，value 长度 ≤2000；保存后自动失效缓存。内置配置项见 `docs/data-dictionary.md` 第 13 节。

---

## 四、错误码说明

| code | HTTP | 场景 |
| --- | --- | --- |
| 0 | 200 | 成功 |
| -1 | 200/400 | 业务校验失败（message 含具体原因，如「商品库存不足：XX」） |
| -1 | 401 | 未登录、令牌过期或账号被禁用 |
| -1 | 500 | 服务端异常 |

## 五、缓存与一致性约定

1. 商品读链路：小程序 Storage（SWR）→ Redis → MySQL；商品写操作「先写库、后失效缓存」。
2. 购物车：Redis 60s + 小程序 30s 快照，任何写操作（加购/改量/勾选/删除）立即失效两侧缓存。
3. 订单金额、库存等强一致数据不依赖缓存：下单在事务内实时计价、原子扣减库存。
4. 统计接口（overview/trend）全部实时查库，保证经营数据准确。

---

## 附录：2026-10-11 新增接口

### 小程序端

| 接口 | 说明 |
|---|---|
| `POST /miniapp/orders/{id}/cancel` | 取消订单：仅 `PENDING_PAYMENT` 可取消，取消后回补库存；其他状态返回错误"当前状态不可取消" |
| `GET /miniapp/inquiries?page=&size=&status=` | 分页查询我的询价单。每条记录为 `{ inquiry, quote }`，`quote` 为最新一条已发送报价（无报价为 null），含 `quotePrice`、`deliveryFee`、`validUntil`、`remark` |
| `GET /miniapp/invoices` | 发票抬头列表（默认抬头在前） |
| `POST /miniapp/invoices` | 保存发票抬头（新增/更新）。`titleType`: `COMPANY`/`PERSONAL`；企业抬头 `taxNo` 必填；首个抬头自动设为默认 |
| `DELETE /miniapp/invoices/{id}` | 删除发票抬头 |
| `POST /miniapp/invoices/{id}/default` | 设为默认抬头 |

### 管理端

| 接口 | 说明 |
|---|---|
| `POST /admin/inquiries/{id}/quote` | 对询价单报价。请求体：`quotePrice`（必填>0）、`deliveryFee`、`validUntil`(YYYY-MM-DD)、`remark`。写入报价单（状态 SENT，对用户可见），询价单状态推进为 `QUOTED`；已关闭（CLOSED）的询价单不可报价 |
