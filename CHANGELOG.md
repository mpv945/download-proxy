## v2025.0809.0313

### ⚙️ CI/CD 流程相关更改
- 🧹 构建过程或工具变更
  - 原因：github releases每个文件自己会算 sha256
  - 修复方式：给计算sha256的step添加不执行的条件
  - 影响范围：减少生成sha256文件
  - 回归验证：发布验证

## v2025.0809.0151

### ⚙️ CI/CD 流程相关更改
- 🧹 构建过程或工具变更
  - 目的：让推送tag和CHANGELOG.md的调整内容在一个tag版本的内容可以发布，先把CHANGELOG.md 内容添加好，提交成功在操作下面的
  - 步骤1: 添加新的 ## v202x.xx.xx 版本标记
  - 步骤2: git tag -a v2025.0809.0151 -m "新增一个tag，tag收到的提交内容是最后一次commit提交的信息，所以push tag最后一次的提交内容包含发布生产才会编译"
  - 步骤3: git push origin v2025.0809.0151
  - 删除tag：
  - 先删除远程 tag（避免别人拉取）：git push origin :refs/tags/v2025.0809.0151
  - 再删除本地 tag（清理本地引用）：git tag -d v2025.0809.0151

## v1.1.1

### Feature
- ✨ feat(auth): Add user login feature (#42)
    - ⚙ 技术细节：新功能
    - 🔁 兼容性：兼容 v1 HTTP 接口，需通过版本头切换
    - 📢 注意事项：WebSocket 超时时间默认为 60 秒
    - BREAKING CHANGE: /api/v1/login 响应结构发生变化

### Fixed
- 🐛 Fix crash on startup (#45)
    - 原因：修复 bug
    - 修复方式：延迟初始化日志模块直到配置加载完毕
    - 影响范围：首次启动阶段，尤其是在 Docker 环境下
    - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证

### Docs
- 📚 Doc edit RedMe.md
  - 原因：文档修改
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证

### Style
- 💅 Coding Style DESC
  - 原因：代码格式（空格、分号等）
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证

### Refactor
- 🔨 重构代码
  - 原因：重构代码（无新增功能或修复）
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证

### Perf
- ⚡️ 性能优化
  - 原因：重构代码（无新增功能或修复）
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证

### Test
- ✅ 增加/修改测试代码
  - 原因：重构代码（无新增功能或修复）
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证
  - Closes #123 自动关闭关联 issue
  - BREAKING CHANGE: 声明破坏性变更

### Chore
- 🧹 构建过程或工具变更
  - 原因：重构代码（无新增功能或修复）
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证

### ⚙️ CI/CD 流程相关更改
- 🧹 构建过程或工具变更
  - 原因：重构代码（无新增功能或修复）
  - 修复方式：延迟初始化日志模块直到配置加载完毕
  - 影响范围：首次启动阶段，尤其是在 Docker 环境下
  - 回归验证：已通过 Ubuntu / Alpine / MacOS 环境验证