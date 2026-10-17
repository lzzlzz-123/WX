# Redis 缓存设计

Redis 只缓存可重建的读模型和短期状态，MySQL 是商品、订单、询价等业务事实的唯一来源。缓存故障时接口回源 MySQL，不把 Redis 当作唯一数据源。

## Key 约定

| Key | 内容 | TTL | 失效方式 |
| --- | --- | --- | --- |
| `cache:product:detail:{id}` | 商品详情 | 10 分钟 | 商品编辑、上下架时删除 |
| `cache:product:empty:{id}` | 商品不存在标记 | 30 秒 | 商品创建/编辑时删除 |
| `cache:product:page:v{version}:{page}:{size}:{status}:{keyword}` | 小程序/后台商品分页 | 60 秒 | 商品变化时递增版本号 |
| `cache:product:catalog:version` | 商品列表版本号 | 30 天 | 商品变化时 `INCR` |
| `cache:cart:{userId}` | 用户购物车条目列表 | 60 秒 | 加购/改数量/勾选/删除时删除 |
| `cache:settings:all` | 系统设置全量键值 | 10 分钟 | 管理端保存设置时删除 |
| `auth:token:{jti}` | JWT 会话吊销/踢下线标记 | 与 JWT 过期时间一致 | 登出或管理员禁用时写入 |
| `idempotency:{scene}:{requestId}` | 创建订单、支付回调幂等结果 | 24 小时 | TTL 到期自动清理 |
| `lock:{scene}:{businessId}` | 库存扣减、支付回调分布式锁 | 5-15 秒 | 完成后释放，过期兜底 |

## 读写策略

商品查询采用 cache-aside：先读 Redis，未命中时查 MySQL，再写入 Redis。详情缓存使用 10 分钟 TTL；商品列表使用 60 秒 TTL，并通过版本号换 key，避免扫描 Redis 删除整批分页缓存。不存在的商品写入 30 秒负缓存，降低恶意 ID 对数据库的穿透压力。

商品写入先提交 MySQL，再删除详情缓存并递增列表版本号。这里不使用“先删缓存再写库”，避免并发读把旧值重新写回缓存。对库存、订单金额、支付状态等强一致字段不直接使用普通缓存，必须通过数据库事务和行锁/业务状态机处理。

购物车采用短 TTL cache-aside：读列表先查 `cache:cart:{userId}`，未命中回源 MySQL 后写入 60 秒缓存；任何购物车写操作（加购、改数量、勾选、删除）先落库再删除该 key。购物车单价、库存等展示信息由小程序端再叠加商品详情缓存渲染，结算时一律以数据库实时价格与库存重新计算。

## 小程序端本地缓存

小程序在 `common/cache.js` 提供基于 Storage 的 TTL 缓存与 SWR（stale-while-revalidate）封装：命中未过期缓存直接返回；只有过期数据时先返回旧数据渲染、后台刷新成功后回调更新页面；无缓存时等待网络。写操作（加购、改数量、删除）成功后立即删除本地购物车缓存 key，登出时同步清理。

| 本地 key（`cache:` 前缀，存 Storage） | 内容 | TTL |
| --- | --- | --- |
| `catalog:categories` | 商品分类 | 10 分钟 |
| `catalog:products:{page}:{size}:{keyword}` | 商品列表页 | 60 秒 |
| `catalog:product:{id}` | 商品详情 | 5 分钟 |
| `cart:{userId}` | 购物车快照 | 30 秒，写操作后立即失效 |

## 防护边界

- Redis 连接超时设置为 1 秒，命令超时设置为 2 秒；缓存读写异常应降级到 MySQL，并记录监控指标。
- 热点商品可以增加本地 Caffeine 二级缓存，但只能缓存商品展示数据，TTL 控制在 10-30 秒，写操作仍以 Redis/MySQL 失效为准。
- 订单创建使用 `idempotency:order:{requestId}`，支付回调使用微信交易号作为 requestId，防止重复下单和重复记账。
- 库存扣减使用 `lock:stock:{productId}` 或数据库原子扣减，不能仅依靠缓存里的库存数。
- 生产环境开启 Redis ACL、密码和 TLS，禁止把 Redis 暴露到公网，并按 `cache`、`auth`、`lock` 前缀分别统计命中率、内存和过期情况。
