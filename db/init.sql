CREATE DATABASE IF NOT EXISTS buildmart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE buildmart;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid VARCHAR(64) UNIQUE,
  unionid VARCHAR(64),
  nickname VARCHAR(80) NOT NULL,
  phone VARCHAR(32),
  company_name VARCHAR(120),
  avatar_url VARCHAR(500),
  credit_limit DECIMAL(14,2) DEFAULT 0,
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT DEFAULT 0,
  name VARCHAR(80) NOT NULL,
  icon_url VARCHAR(500),
  sort_no INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  sku VARCHAR(64) UNIQUE NOT NULL,
  name VARCHAR(160) NOT NULL,
  brand VARCHAR(80),
  grade VARCHAR(80),
  specification VARCHAR(160),
  unit VARCHAR(24) NOT NULL,
  base_price DECIMAL(14,2) NOT NULL,
  stock DECIMAL(14,3) DEFAULT 0,
  min_order_qty DECIMAL(14,3) DEFAULT 1,
  sold_qty DECIMAL(14,3) DEFAULT 0,
  delivery_modes VARCHAR(120),
  cover_url VARCHAR(500),
  detail TEXT,
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_product_category (category_id),
  INDEX idx_product_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_tier_price (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  min_qty DECIMAL(14,3) NOT NULL,
  price DECIMAL(14,2) NOT NULL,
  UNIQUE KEY uk_product_qty (product_id, min_qty)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cart_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity DECIMAL(14,3) NOT NULL,
  selected TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart_user_product (user_id, product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS purchase_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) UNIQUE NOT NULL,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(14,2) NOT NULL,
  freight_amount DECIMAL(14,2) DEFAULT 0,
  payable_amount DECIMAL(14,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  payment_method VARCHAR(24),
  delivery_mode VARCHAR(24),
  receiver_name VARCHAR(80),
  receiver_phone VARCHAR(32),
  receiver_address VARCHAR(300),
  remark VARCHAR(500),
  paid_at DATETIME,
  shipped_at DATETIME,
  completed_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_user_status (user_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(160) NOT NULL,
  specification VARCHAR(160),
  unit VARCHAR(24),
  quantity DECIMAL(14,3) NOT NULL,
  unit_price DECIMAL(14,2) NOT NULL,
  amount DECIMAL(14,2) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(80) NOT NULL,
  receiver_phone VARCHAR(32) NOT NULL,
  province VARCHAR(40),
  city VARCHAR(40),
  district VARCHAR(40),
  detail_address VARCHAR(240) NOT NULL,
  is_default TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inquiry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inquiry_no VARCHAR(40) UNIQUE NOT NULL,
  user_id BIGINT NOT NULL,
  product_name VARCHAR(160) NOT NULL,
  specification VARCHAR(160),
  quantity DECIMAL(14,3) NOT NULL,
  unit VARCHAR(24),
  expected_price DECIMAL(14,2),
  delivery_address VARCHAR(300),
  deadline DATE,
  status VARCHAR(24) DEFAULT 'PENDING_QUOTE',
  remark VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inquiry_quote (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inquiry_id BIGINT NOT NULL,
  admin_id BIGINT NOT NULL,
  quote_price DECIMAL(14,2) NOT NULL,
  valid_until DATE,
  delivery_fee DECIMAL(14,2) DEFAULT 0,
  remark VARCHAR(500),
  status VARCHAR(24) DEFAULT 'DRAFT',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_quote_inquiry (inquiry_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS invoice_title (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title_type VARCHAR(16) NOT NULL DEFAULT 'COMPANY' COMMENT 'PERSONAL 个人 / COMPANY 企业',
  title VARCHAR(160) NOT NULL COMMENT '发票抬头',
  tax_no VARCHAR(40) COMMENT '企业税号',
  bank_name VARCHAR(120),
  bank_account VARCHAR(60),
  register_address VARCHAR(300),
  register_phone VARCHAR(40),
  email VARCHAR(120) COMMENT '接收电子发票邮箱',
  is_default TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_invoice_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_admin (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(60) UNIQUE NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  real_name VARCHAR(80),
  role_code VARCHAR(40) NOT NULL DEFAULT 'OPERATOR',
  status TINYINT DEFAULT 1,
  last_login_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(40) NOT NULL,
  permission_code VARCHAR(100) NOT NULL,
  UNIQUE KEY uk_role_permission (role_code, permission_code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL,
  setting_value VARCHAR(2000),
  description VARCHAR(300),
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_setting_key (setting_key)
) ENGINE=InnoDB;

INSERT INTO sys_setting (setting_key, setting_value, description) VALUES
  ('shop.name', '筑云清松', '商城名称'),
  ('shop.contact_phone', '400-000-0000', '客服电话'),
  ('order.auto_cancel_minutes', '30', '待支付订单自动取消时间（分钟）'),
  ('order.default_freight', '0.00', '默认运费（元）'),
  ('inquiry.quote_valid_days', '7', '询价单报价默认有效天数'),
  ('stock.low_warning_enabled', 'true', '是否开启低库存预警')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO product_category (name, sort_no) VALUES
  ('水泥建材', 1), ('钢材', 2), ('砂石料', 3), ('装饰辅材', 4)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO sys_admin (username, password_hash, real_name, role_code)
VALUES ('admin', '$2a$10$BdmRe6PagKxp0EMeNmaNNuMp3kANxj1KzmN4bdHFaNnlS2umwDGiy', '系统管理员', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), real_name = VALUES(real_name), role_code = VALUES(role_code), status = 1;

INSERT INTO product (category_id, sku, name, brand, grade, specification, unit, base_price, stock, min_order_qty, sold_qty, delivery_modes, cover_url, detail, status)
VALUES
  (1, 'SN-PO425-50', 'P.O 42.5 普通硅酸盐水泥', '海螺', 'P.O 42.5', '50kg/袋', '吨', 318.00, 1280.000, 1.000, 4620.000, 'CITY_DELIVERY,LOGISTICS,PICKUP', NULL, '强度稳定，适用于房建和市政项目。', 1),
  (2, 'GC-HRB400E-12', 'HRB400E 螺纹钢', '沙钢', 'HRB400E', '12mm 国标', '吨', 3680.00, 86.000, 1.000, 1280.000, 'LOGISTICS,SUPPLIER_DIRECT', NULL, '国标抗震螺纹钢，支持按项目配货。', 1),
  (3, 'SS-ZCS-001', '中粗砂（河砂）', '本地砂场', '中粗砂', '含泥量不高于3%', '吨', 120.00, 420.000, 10.000, 1860.000, 'CITY_DELIVERY,SUPPLIER_DIRECT', NULL, '级配稳定，适合混凝土和砌筑工程。', 1),
  (1, 'SN-C30-001', 'C30 商品混凝土', '筑城砼', 'C30', '坍落度180±20mm', '立方米', 460.00, 240.000, 5.000, 760.000, 'CITY_DELIVERY,SUPPLIER_DIRECT', NULL, '支持按施工时段预约配送。', 1)
ON DUPLICATE KEY UPDATE base_price = VALUES(base_price), stock = VALUES(stock), status = VALUES(status);

INSERT INTO product_tier_price (product_id, min_qty, price)
SELECT id, 1, 318.00 FROM product WHERE sku = 'SN-PO425-50'
ON DUPLICATE KEY UPDATE price = VALUES(price);
INSERT INTO product_tier_price (product_id, min_qty, price)
SELECT id, 10, 305.00 FROM product WHERE sku = 'SN-PO425-50'
ON DUPLICATE KEY UPDATE price = VALUES(price);
INSERT INTO product_tier_price (product_id, min_qty, price)
SELECT id, 1, 3680.00 FROM product WHERE sku = 'GC-HRB400E-12'
ON DUPLICATE KEY UPDATE price = VALUES(price);
INSERT INTO product_tier_price (product_id, min_qty, price)
SELECT id, 20, 3590.00 FROM product WHERE sku = 'GC-HRB400E-12'
ON DUPLICATE KEY UPDATE price = VALUES(price);
INSERT INTO product_tier_price (product_id, min_qty, price)
SELECT id, 10, 120.00 FROM product WHERE sku = 'SS-ZCS-001'
ON DUPLICATE KEY UPDATE price = VALUES(price);
INSERT INTO product_tier_price (product_id, min_qty, price)
SELECT id, 50, 112.00 FROM product WHERE sku = 'SS-ZCS-001'
ON DUPLICATE KEY UPDATE price = VALUES(price);
