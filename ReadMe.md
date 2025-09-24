GitHub Actions : https://docs.github.com/zh/actions/get-started/quickstart

1. 创建目录
    .github/workflows
2. 创建工作流文件: 
    github-actions.yml
3. 设置代理
   git config --global http.proxy 'http://127.0.0.1:7897'   
   git config --global https.proxy 'http://127.0.0.1:7897'
   # 取消代理
   git config --global --unset http.proxy
   git config --global --unset https.proxy

好的 👍
我帮你写一个**通用的 systemd Service 文件**，可以在 **CentOS / Debian / ArchLinux** 下使用，稍作修改即可。

---

### 1. 创建 systemd service 文件

编辑 `/etc/systemd/system/down-proxy.service`：

```ini
[Unit]
Description=Down Proxy Java Service
After=network.target

[Service]
# Java 运行环境路径
WorkingDirectory=/home/java/jdk21

# 环境变量
Environment=SERVER_PORT=15002

# 启动命令
ExecStart=/usr/bin/java -jar /home/app/down-proxy.jar

# 进程管理
Restart=on-failure
RestartSec=5

# 日志（写入 journald，可用 journalctl -u down-proxy 查看）
StandardOutput=journal
StandardError=journal

# 用户身份（根据实际情况修改）
User=appuser
Group=appuser

[Install]
WantedBy=multi-user.target
```

---

### 2. 设置执行用户

如果你现在是用 `root` 启动的，可以去掉 `User` 和 `Group`。
推荐新建一个用户运行，避免直接用 root：

```bash
useradd -r -s /bin/false appuser
chown -R appuser:appuser /home/app /home/java/jdk21
```

---

### 3. 重新加载 systemd 并启用服务

```bash
sudo systemctl daemon-reexec      # 或者 daemon-reload
sudo systemctl enable --now down-proxy.service
```

---

### 4. 常用命令

```bash
# 启动服务
systemctl start down-proxy

# 停止服务
systemctl stop down-proxy

# 查看状态
systemctl status down-proxy

# 查看日志
journalctl -u down-proxy -f
```

---

### ✅ 跨发行版注意事项

* **CentOS/RHEL** 默认 Java 路径可能在 `/usr/bin/java`（确保 `java -version` 正常）。
* **Debian/Ubuntu** 一样直接用 `/usr/bin/java`。
* **ArchLinux** 也一致。
  （所以 service 文件里写 `/usr/bin/java` 比 `/home/java/jdk21/bin/java` 更稳妥，除非你要强制用自带 JDK21。）
