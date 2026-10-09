# 数据库迁移包

`web_scada_mvp.sql.gz` 是首个 MVP 的 MySQL 压缩快照，包含 `web_scada` 数据库的表结构、索引和当前数据。它是标准 SQL 的 gzip 压缩文件，可在 macOS、Linux 和 Windows 上使用。它不包含 Redis 会话与实时缓存；这些数据会在系统启动后自动重建。

数据库快照可能包含用户、审计和业务记录，因此默认被 Git 忽略。请通过受控文件传输方式把本机 `database/web_scada_mvp.sql.gz` 和校验文件复制到目标 Mac 的同一目录，不要上传到公开仓库。

## 在 macOS 导入

安装并启动 MySQL：

```bash
brew install mysql
brew services start mysql
```

进入项目目录并执行：

```bash
chmod +x database/import-macos.sh
./database/import-macos.sh
```

脚本会提示输入本机 MySQL `root` 密码，并自动解压、创建 `web_scada` 数据库、建立表结构和写入全部快照数据。Apple Silicon 和 Intel Mac 都通过 `PATH` 自动查找 Homebrew 的 `mysql` 命令，不依赖固定安装目录。

用户名不是 `root` 时：

```bash
MYSQL_USER=your_admin ./database/import-macos.sh
```

## 在 Windows 导入

先解压 `web_scada_mvp.sql.gz`，再在项目根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\database\import.ps1
```

脚本默认使用 `D:\mysql8\bin\mysql.exe` 和 `root` 用户，并安全提示输入密码。路径或用户名不同时可以传入 `-MySql` 和 `-Username`。

导入会创建或覆盖快照中同名表的数据。导入前如目标机已经存在重要的 `web_scada` 数据库，请先单独备份。

导入后还需为应用准备数据库账号，并在 `.local/config/application-local.properties` 配置连接信息。Redis 只保存可重建的实时值与登录会话，不需要迁移。

## 重新导出

Windows：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\database\export.ps1
```

macOS：

```bash
chmod +x database/export-macos.sh
./database/export-macos.sh
```

脚本使用临时 MySQL 客户端配置，不会把密码写入导出内容。压缩快照旁的 `.sha256` 文件用于校验传输完整性。
