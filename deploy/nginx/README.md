# Nginx 部署管理端

## 构建

在项目根目录执行：

```bash
cd admin-web
npm ci
npm run build
```

默认生产 API 地址是同源的 `/api`，因此不需要把后端地址打包进浏览器代码。若 Nginx 和后端不使用同一域名，也可以在构建时覆盖：

```bash
VITE_API_BASE_URL=https://api.example.com/api npm run build
```

## 发布文件

将 `admin-web/dist` 目录内容复制到 Nginx 配置中的 `/var/www/buildmart`：

```bash
sudo mkdir -p /var/www/buildmart
sudo cp -r admin-web/dist/. /var/www/buildmart/
```

Linux Nginx 可将 [buildmart.conf](./buildmart.conf) 复制到 Nginx 配置目录（或放入 `http {}` 内的 `conf.d`）：

```bash
sudo cp deploy/nginx/buildmart.conf /etc/nginx/conf.d/buildmart.conf
sudo nginx -t
sudo systemctl reload nginx
```

配置默认反向代理到 `127.0.0.1:8080`。如果后端运行在其他主机或容器，请修改 `location /api/` 中的 `proxy_pass`，保持后端请求路径中的 `/api` 不变。

## Windows Nginx

Windows 发行版通常没有自动加载 `conf.d`，不能把只有 `server {}` 的 `buildmart.conf` 直接覆盖 `conf/nginx.conf`。请使用完整配置 [nginx.windows.conf](./nginx.windows.conf)：

```powershell
# 假设 Nginx 安装目录为 D:\nginx-1.26.2 - wx
Copy-Item .\deploy\nginx\nginx.windows.conf 'D:\nginx-1.26.2 - wx\conf\nginx.conf' -Force
Copy-Item .\admin-web\dist\* 'D:\nginx-1.26.2 - wx\html' -Recurse -Force

Set-Location 'D:\nginx-1.26.2 - wx'
.\nginx.exe -t
.\nginx.exe -s reload
```

如果 `nginx -t` 报 `server directive is not allowed here`，说明把 server-only 片段放到了 `nginx.conf` 顶层；改用上面的完整配置。若后端使用 8081 端口，将两个配置中的 `proxy_pass` 改为 `http://127.0.0.1:8081`。

## 验证

```bash
curl -I http://your-domain/
curl -I http://your-domain/api/health
```

浏览器刷新 `/orders`、`/products` 等前端路由时应仍返回管理端页面，而 `/api/*` 请求应转发到 Spring Boot 后端。
