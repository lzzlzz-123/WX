# BuildMart 数据字典

> 数据库：`buildmart`（MySQL，utf8mb4 / utf8mb4_unicode_ci，引擎 InnoDB）
> 建库脚本：`db/init.sql`
> 通用约定：金额一律 `DECIMAL(14,2)`，数量 `DECIMAL(14,3)`；`status` 字段 `1=正常/在售/启用`，`0=下架/禁用`；时间均为 `DATETIME`，时区 Asia/Shanghai。

## 1. sys_user — 小程序用户（客户）

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| openid | VARCHAR(64) | 否 | - | 微信 openid，唯一索引 |
| unionid | VARCHAR(64) | 否 | - | 微信 unionid |
| nickname | VARCHAR(80) | 是 | - | 昵称，新用户默认「微信用户」 |
| phone | VARCHAR(32) | 否 | - | 手机号 |
| company_name | VARCHAR(120) | 否 | - | 公司/项目部名称 |
| avatar_url | VARCHAR(500) | 否 | - | 头像地址（/api/files/** 可访问） |
| credit_limit | DECIMAL(14,2) | 否 | 0 | 赊销信用额度（元），管理端可调整 |
| status | TINYINT | 否 | 1 | 1 正常，0 禁用（禁用后无法登录小程序） |
| created_at | DATETIME | 否 | 当前时间 | 注册时间 |
| updated_at | DATETIME | 否 | 当前时间 | 更新时间（自动刷新） |

## 2. product_category — 商品分类

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| parent_id | BIGINT | 否 | 0 | 父分类，0 为顶级 |
| name | VARCHAR(80) | 是 | - | 分类名称，唯一索引 uk_category_name |
| icon_url | VARCHAR(500) | 否 | - | 分类图标 |
| sort_no | INT | 否 | 0 | 排序号，升序展示 |
| status | TINYINT | 否 | 1 | 1 启用，0 停用 |
| created_at | DATETIME | 否 | 当前时间 | 创建时间 |

## 3. product — 商品

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| category_id | BIGINT | 是 | - | 所属分类 id（索引 idx_product_category） |
| sku | VARCHAR(64) | 是 | - | SKU 编码，唯一 |
| name | VARCHAR(160) | 是 | - | 商品名称 |
| brand | VARCHAR(80) | 否 | - | 品牌/厂商 |
| grade | VARCHAR(80) | 否 | - | 等级/标号（如 P.O 42.5、HRB400E） |
| specification | VARCHAR(160) | 否 | - | 规格描述 |
| unit | VARCHAR(24) | 是 | - | 计量单位（吨/立方米/袋…） |
| base_price | DECIMAL(14,2) | 是 | - | 基准单价（元） |
| stock | DECIMAL(14,3) | 否 | 0 | 库存量 |
| min_order_qty | DECIMAL(14,3) | 否 | 1 | 起订量 |
| sold_qty | DECIMAL(14,3) | 否 | 0 | 累计销量 |
| delivery_modes | VARCHAR(120) | 否 | - | 支持的配送方式，逗号分隔枚举：CITY_DELIVERY 同城配送 / LOGISTICS 物流 / PICKUP 自提 / SUPPLIER_DIRECT 厂家直发 |
| cover_url | VARCHAR(500) | 否 | - | 封面图 |
| detail | TEXT | 否 | - | 图文详情 |
| status | TINYINT | 否 | 1 | 1 在售，0 下架（索引 idx_product_status） |
| created_at | DATETIME | 否 | 当前时间 | 创建时间 |
| updated_at | DATETIME | 否 | 当前时间 | 更新时间（自动刷新） |

## 4. product_tier_price — 商品阶梯价

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| product_id | BIGINT | 是 | - | 商品 id |
| min_qty | DECIMAL(14,3) | 是 | - | 适用起始数量 |
| price | DECIMAL(14,2) | 是 | - | 该档单价（元） |

唯一索引 `uk_product_qty(product_id, min_qty)`。计价规则：取 `min_qty <= 下单数量` 的最高档；无匹配档则使用 product.base_price。

## 5. cart_item — 购物车

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| user_id | BIGINT | 是 | - | 用户 id |
| product_id | BIGINT | 是 | - | 商品 id |
| quantity | DECIMAL(14,3) | 是 | - | 采购数量 |
| selected | TINYINT | 否 | 1 | 1 选中（参与结算），0 未选中 |
| created_at | DATETIME | 否 | 当前时间 | 加入时间 |
| updated_at | DATETIME | 否 | 当前时间 | 更新时间（自动刷新） |

唯一索引 `uk_cart_user_product(user_id, product_id)`：同一用户同一商品仅一行，重复加购累加数量。

## 6. purchase_order — 采购订单

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| order_no | VARCHAR(40) | 是 | - | 订单号，唯一；规则 `BM` + yyyyMMddHHmmss + 3 位随机数 |
| user_id | BIGINT | 是 | - | 下单用户 id（联合索引 idx_order_user_status） |
| total_amount | DECIMAL(14,2) | 是 | - | 商品总额（元） |
| freight_amount | DECIMAL(14,2) | 否 | 0 | 运费（元） |
| payable_amount | DECIMAL(14,2) | 是 | - | 应付金额 = 商品总额 + 运费 |
| status | VARCHAR(24) | 是 | PENDING_PAYMENT | 订单状态机：PENDING_PAYMENT 待支付 → PENDING_SHIPMENT 待发货 → SHIPPED 已发货 → COMPLETED 已完成；CANCELLED 已取消 |
| payment_method | VARCHAR(24) | 否 | - | 支付方式，当前为 WECHAT |
| delivery_mode | VARCHAR(24) | 否 | - | 配送方式（同 product.delivery_modes 枚举） |
| receiver_name | VARCHAR(80) | 否 | - | 收货人 |
| receiver_phone | VARCHAR(32) | 否 | - | 收货电话 |
| receiver_address | VARCHAR(300) | 否 | - | 收货地址快照 |
| remark | VARCHAR(500) | 否 | - | 订单备注 |
| paid_at | DATETIME | 否 | - | 支付时间 |
| shipped_at | DATETIME | 否 | - | 发货时间 |
| completed_at | DATETIME | 否 | - | 完成时间 |
| created_at | DATETIME | 否 | 当前时间 | 下单时间 |
| updated_at | DATETIME | 否 | 当前时间 | 更新时间（自动刷新） |

## 7. order_item — 订单明细

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| order_id | BIGINT | 是 | - | 订单 id |
| product_id | BIGINT | 是 | - | 商品 id |
| product_name | VARCHAR(160) | 是 | - | 商品名快照 |
| specification | VARCHAR(160) | 否 | - | 规格快照 |
| unit | VARCHAR(24) | 否 | - | 单位快照 |
| quantity | DECIMAL(14,3) | 是 | - | 数量 |
| unit_price | DECIMAL(14,2) | 是 | - | 成交单价（阶梯价计算结果） |
| amount | DECIMAL(14,2) | 是 | - | 行金额 = 单价 × 数量 |

## 8. user_address — 收货地址

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| user_id | BIGINT | 是 | - | 用户 id |
| receiver_name | VARCHAR(80) | 是 | - | 收货人 |
| receiver_phone | VARCHAR(32) | 是 | - | 收货电话 |
| province | VARCHAR(40) | 否 | - | 省 |
| city | VARCHAR(40) | 否 | - | 市 |
| district | VARCHAR(40) | 否 | - | 区/县 |
| detail_address | VARCHAR(240) | 是 | - | 详细地址 |
| is_default | TINYINT | 否 | 0 | 1 默认地址，0 普通 |
| created_at | DATETIME | 否 | 当前时间 | 创建时间 |

## 9. inquiry — 询价单

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| inquiry_no | VARCHAR(40) | 是 | - | 询价单号，唯一；规则 `IN` + yyyyMMddHHmmss + 3 位随机数 |
| user_id | BIGINT | 是 | - | 用户 id |
| product_name | VARCHAR(160) | 是 | - | 询价商品名称 |
| specification | VARCHAR(160) | 否 | - | 规格要求 |
| quantity | DECIMAL(14,3) | 是 | - | 需求数量 |
| unit | VARCHAR(24) | 否 | - | 单位 |
| expected_price | DECIMAL(14,2) | 否 | - | 期望单价（元） |
| delivery_address | VARCHAR(300) | 否 | - | 交付地址 |
| deadline | DATE | 否 | - | 要求交付日期 |
| status | VARCHAR(24) | 否 | PENDING_QUOTE | PENDING_QUOTE 待报价 / QUOTED 已报价 / CLOSED 已关闭 |
| remark | VARCHAR(500) | 否 | - | 备注 |
| created_at | DATETIME | 否 | 当前时间 | 提交时间 |

## 10. inquiry_quote — 询价报价

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| inquiry_id | BIGINT | 是 | - | 询价单 id |
| admin_id | BIGINT | 是 | - | 报价管理员 id |
| quote_price | DECIMAL(14,2) | 是 | - | 报价单价（元） |
| valid_until | DATE | 否 | - | 报价有效期至 |
| delivery_fee | DECIMAL(14,2) | 否 | 0 | 配送费（元） |
| remark | VARCHAR(500) | 否 | - | 报价说明 |
| status | VARCHAR(24) | 否 | DRAFT | DRAFT 草稿 / SENT 已发送 / ACCEPTED 已接受 / EXPIRED 已失效 |
| created_at | DATETIME | 否 | 当前时间 | 报价时间 |

## 11. sys_admin — 管理员

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| username | VARCHAR(60) | 是 | - | 登录账号，唯一 |
| password_hash | VARCHAR(120) | 是 | - | BCrypt 密码哈希 |
| real_name | VARCHAR(80) | 否 | - | 姓名 |
| role_code | VARCHAR(40) | 是 | OPERATOR | 角色编码：SUPER_ADMIN 超管 / OPERATOR 运营 |
| status | TINYINT | 否 | 1 | 1 启用，0 禁用 |
| last_login_at | DATETIME | 否 | - | 最近登录时间 |
| created_at | DATETIME | 否 | 当前时间 | 创建时间 |

初始账号：`admin`（SUPER_ADMIN，密码见部署文档，BCrypt 存储）。

## 12. sys_role_permission — 角色权限

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| role_code | VARCHAR(40) | 是 | - | 角色编码 |
| permission_code | VARCHAR(100) | 是 | - | 权限编码 |

唯一索引 `uk_role_permission(role_code, permission_code)`。

## 13. sys_setting — 系统设置（新增）

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | 是 | 自增 | 主键 |
| setting_key | VARCHAR(100) | 是 | - | 配置键，唯一索引 uk_setting_key |
| setting_value | VARCHAR(2000) | 否 | - | 配置值（字符串承载，数值/布尔由使用方解析） |
| description | VARCHAR(300) | 否 | - | 配置说明 |
| updated_at | DATETIME | 否 | 当前时间 | 更新时间（自动刷新） |

内置配置项：

| setting_key | 默认值 | 说明 |
| --- | --- | --- |
| shop.name | BuildMart 建材商城 | 商城名称 |
| shop.contact_phone | 400-000-0000 | 客服电话 |
| order.auto_cancel_minutes | 30 | 待支付订单自动取消时间（分钟） |
| order.default_freight | 0.00 | 默认运费（元） |
| inquiry.quote_valid_days | 7 | 询价单报价默认有效天数 |
| stock.low_warning_enabled | true | 是否开启低库存预警 |

## 附：Redis 缓存键

详见 `docs/redis-design.md`。核心键：`cache:product:detail:{id}`、`cache:product:empty:{id}`、`cache:product:page:v{version}:...`、`cache:product:catalog:version`、`cache:cart:{userId}`、`cache:settings:all`、`auth:token:{jti}`、`idempotency:{scene}:{requestId}`、`lock:{scene}:{businessId}`。

---

## 附录：invoice_title（发票抬头表，2026-10-11 新增）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | 是 | 主键，自增 |
| user_id | BIGINT | 是 | 所属用户 |
| title_type | VARCHAR(16) | 是 | `COMPANY` 企业 / `PERSONAL` 个人，默认 COMPANY |
| title | VARCHAR(160) | 是 | 发票抬头（企业名称或个人姓名） |
| tax_no | VARCHAR(40) | 企业必填 | 统一社会信用代码 |
| bank_name | VARCHAR(120) | 否 | 开户银行 |
| bank_account | VARCHAR(60) | 否 | 银行账号 |
| register_address | VARCHAR(300) | 否 | 注册地址 |
| register_phone | VARCHAR(40) | 否 | 注册电话 |
| email | VARCHAR(120) | 否 | 电子发票接收邮箱 |
| is_default | TINYINT | 是 | 1 默认 / 0 否，每用户至多一条默认 |
| created_at | DATETIME | 是 | 创建时间 |

索引：`idx_invoice_user (user_id)`
另：`inquiry_quote` 表补充索引 `idx_quote_inquiry (inquiry_id, status)`；报价单 `status=SENT` 时对小程序用户可见。
