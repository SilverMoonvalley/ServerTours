# ServerTours NMS 版本更新适配文档

本文档用于 ServerTours 在 Minecraft / Paper / Spigot 小版本升级时，快速、可重复地新增 NMS 适配模块，并完成构建与运行验证。

## 1. 当前项目基线

- 项目版本：`3.0.0`
- Java 版本：`17`
- Maven 聚合模块：
  - `modules/nms`：跨版本 NMS 接口。
  - `modules/base`：插件主体与最终 shade 产物。
  - `modules/v1_21_4`：Minecraft `1.21.4`，CraftBukkit 包 `v1_21_R3`。
  - `modules/v1_21_5`：Minecraft `1.21.5`，CraftBukkit 包 `v1_21_R4`。
- 最终插件产物：
  - `modules/base/target/ServerTours-3.0.0.jar`
- 当前 NMS 分发入口：
  - `modules/base/src/main/java/com/melluh/servertours/util/nms/NmsVersion.java`
- 当前 NMS 公共接口：
  - `modules/nms/src/main/java/com/melluh/servertours/nms/NmsHandler.java`
  - `modules/nms/src/main/java/com/melluh/servertours/nms/ModernMovementNmsHandler.java`
  - `modules/nms/src/main/java/com/melluh/servertours/nms/TemporaryEntity.java`

## 2. 一次完整更新流程

1. 明确目标 Minecraft 版本、Paper/Spigot 版本、CraftBukkit NMS 包名，例如 `1.21.6 -> v1_21_R5`。
2. 确认目标版本的 NMS 编译依赖可用，可以来自团队 Nexus、远程 Maven 或本地 BuildTools 构建结果。
3. 复制上一版可工作的 NMS 模块，例如 `v1_21_5 -> v1_21_6`。
4. 修改新模块目录、`artifactId`、Java 包名和 CraftBukkit import。
5. 修改根 `pom.xml`，把新模块加入 `<modules>`。
6. 修改 `modules/base/pom.xml`，加入新模块依赖。
7. 修改 `NmsVersion.java`，将目标 Minecraft 版本映射到新 `NmsHandler`。
8. 编译新模块，修复 Mojang/Spigot NMS 改名、签名变化、构造器变化。
9. 如果本次生成了难获取的 remapped/NMS jar，将它发布到团队 Nexus，供其他插件复用。
10. 构建最终插件 jar。
11. 在目标 Paper 服务端做最小运行验证。

## 3. 判断目标版本映射

新增适配前先记录一行映射，后续排查会省很多时间：

```text
Minecraft 版本: 1.21.x
Paper API 版本: 1.21.x-R0.1-SNAPSHOT
CraftBukkit 包名: org.bukkit.craftbukkit.v1_21_Rx
NMS 模块名: v1_21_x
spigot-spigot-remapped 版本: v1_21_Rx
```

当前项目已有映射：

| Minecraft | 模块 | CraftBukkit 包 | remapped 依赖版本 |
| --- | --- | --- | --- |
| `1.21.4` | `v1_21_4` | `v1_21_R3` | `v1_21_R3` |
| `1.21.5` | `v1_21_5` | `v1_21_R4` | `v1_21_R4` |

## 4. 准备 NMS 编译依赖

当前 `modules/v1_21_4/pom.xml` 和 `modules/v1_21_5/pom.xml` 使用的是：

```xml
<dependency>
    <groupId>net.minecraft.server</groupId>
    <artifactId>spigot-spigot-remapped</artifactId>
    <version>v1_21_R4</version>
    <scope>provided</scope>
</dependency>
```

升级时建议优先走“团队 Nexus 复用”路线。只有 Nexus 里没有目标版本时，才本地构建一次并发布到 Nexus，避免每个插件重复跑 BuildTools。

### 4.1 优先从团队 Nexus 获取

在根 `pom.xml` 的 `<repositories>` 中加入团队 Maven 仓库。已有同类仓库时不要重复添加，按实际项目合并即可。

```xml
<repository>
    <id>nexus-releases</id>
    <url>https://www.4399mc.cn/nexus/repository/maven-releases/</url>
</repository>
<repository>
    <id>nexus-snapshots</id>
    <url>https://www.4399mc.cn/nexus/repository/maven-snapshots/</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

确认团队 Nexus 或本地 Maven 有目标版本：

```powershell
$v = "v1_21_R5"
Test-Path "$env:USERPROFILE\.m2\repository\net\minecraft\server\spigot-spigot-remapped\$v\spigot-spigot-remapped-$v.pom"
```

如果本地不存在，但 Nexus 已经有该 artifact，运行一次 Maven 构建会自动下载：

```powershell
mvn -pl modules/v1_21_6 -am -DskipTests package
```

### 4.2 本地生成后发布到 Nexus

如果 Nexus 里没有目标版本，可以由一名维护者用 BuildTools 生成一次目标版本 remapped 依赖：

```powershell
cd C:\Users\Administrator\Desktop\spigot-buildtools
java -jar .\BuildTools.jar --rev 1.21.6 --remapped
```

注意：BuildTools 默认生成的 Maven 坐标可能与当前 `net.minecraft.server:spigot-spigot-remapped` 不完全一致。如果要继续沿用当前项目坐标，可以把生成好的 jar 以当前坐标发布到 Nexus。

示例：将本地已有的 `spigot-spigot-remapped-v1_21_R5.jar` 发布到 releases 仓库：

```powershell
mvn deploy:deploy-file `
  -DrepositoryId=nexus-releases `
  -Durl=https://www.4399mc.cn/nexus/repository/maven-releases/ `
  -DgroupId=net.minecraft.server `
  -DartifactId=spigot-spigot-remapped `
  -Dversion=v1_21_R5 `
  -Dpackaging=jar `
  -Dfile="C:\path\to\spigot-spigot-remapped-v1_21_R5.jar" `
  -DgeneratePom=true
```

版本选择规则：

- `v1_21_R5` 这种不带 `-SNAPSHOT` 的版本发布到 `nexus-releases`。
- `1.21.6-R0.1-SNAPSHOT` 这种带 `-SNAPSHOT` 的版本发布到 `nexus-snapshots`。
- 同一个 release 版本不要反复覆盖；如果内容变了，应更换版本号或先清理 Nexus 上的错误 artifact。

### 4.3 配置发布目标和凭据

如果需要用 `mvn deploy` 发布本项目模块，根 `pom.xml` 可以加入：

```xml
<distributionManagement>
    <snapshotRepository>
        <id>nexus-snapshots</id>
        <url>https://www.4399mc.cn/nexus/repository/maven-snapshots/</url>
    </snapshotRepository>
    <repository>
        <id>nexus-releases</id>
        <url>https://www.4399mc.cn/nexus/repository/maven-releases/</url>
    </repository>
</distributionManagement>
```

发布凭据不要写进项目仓库，放到本机 Maven `settings.xml`：

```xml
<servers>
    <server>
        <id>nexus-releases</id>
        <username>你的账号</username>
        <password>你的密码或 Token</password>
    </server>
    <server>
        <id>nexus-snapshots</id>
        <username>你的账号</username>
        <password>你的密码或 Token</password>
    </server>
</servers>
```

Windows 默认路径：

```text
C:\Users\Administrator\.m2\settings.xml
```

### 4.4 给其他插件复用的依赖写法

其他插件只需要添加团队 Nexus 仓库，并引用同一坐标：

```xml
<dependency>
    <groupId>net.minecraft.server</groupId>
    <artifactId>spigot-spigot-remapped</artifactId>
    <version>v1_21_R5</version>
    <scope>provided</scope>
</dependency>
```

这样其他插件不需要重复执行 BuildTools，只要 Nexus 中已有该版本，Maven 会直接拉取。

只建议将这些 remapped/NMS jar 发布到团队内部 Nexus，用于内部构建复用；不要发布到公共仓库。

## 5. 新增 NMS 模块

以下以 `1.21.5 -> 1.21.6` 为例。

### 5.1 复制目录

```powershell
robocopy modules\v1_21_5 modules\v1_21_6 /E
```

### 5.2 修改新模块 POM

编辑 `modules/v1_21_6/pom.xml`：

```xml
<artifactId>v1_21_6</artifactId>
```

将 NMS 依赖版本改为目标 CraftBukkit 包对应版本：

```xml
<dependency>
    <groupId>net.minecraft.server</groupId>
    <artifactId>spigot-spigot-remapped</artifactId>
    <version>v1_21_R5</version>
    <scope>provided</scope>
</dependency>
```

`paper-api` 版本建议同步到目标版本，例如：

```xml
<version>1.21.6-R0.1-SNAPSHOT</version>
```

### 5.3 修改包名与 import

将新模块中的包名从：

```java
package com.melluh.servertours.nms.v1_21_5;
```

改为：

```java
package com.melluh.servertours.nms.v1_21_6;
```

将 CraftBukkit import 从：

```java
org.bukkit.craftbukkit.v1_21_R4
```

改为目标包，例如：

```java
org.bukkit.craftbukkit.v1_21_R5
```

建议先查再改：

```powershell
rg -n "v1_21_5|v1_21_R4|R4" modules/v1_21_6
```

改完后确认没有旧版本残留：

```powershell
rg -n "v1_21_5|v1_21_R4|R4" modules/v1_21_6
```

## 6. 接入聚合构建

### 6.1 修改根 `pom.xml`

在 `<modules>` 中新增：

```xml
<module>modules/v1_21_6</module>
```

所有 Java 模块统一位于仓库根目录的 `modules/` 下；新增版本模块也应保持同一层级，并在其父 POM 中使用 `<relativePath>../../pom.xml</relativePath>`。

### 6.2 修改 `modules/base/pom.xml`

在已有 `v1_21_4`、`v1_21_5` 依赖后新增：

```xml
<dependency>
    <groupId>com.melluh</groupId>
    <artifactId>v1_21_6</artifactId>
    <version>3.0.0</version>
    <scope>compile</scope>
</dependency>
```

如果后续把内部模块版本统一改为 `${project.version}` 或 `${env.project.version}`，所有内部依赖要一起调整，避免一处固定版本、一处变量版本导致构建不一致。

## 7. 接入运行时版本分发

编辑：

```text
modules/base/src/main/java/com/melluh/servertours/util/nms/NmsVersion.java
```

新增枚举项：

```java
v1_21_6("1.21.6", com.melluh.servertours.nms.v1_21_6.NmsHandler.class);
```

如果它不是最后一项，前一项用逗号；如果是最后一项，用分号。示例：

```java
public enum NmsVersion {
    v1_21_4("1.21.4", com.melluh.servertours.nms.v1_21_4.NmsHandler.class),
    v1_21_5("1.21.5", com.melluh.servertours.nms.v1_21_5.NmsHandler.class),
    v1_21_6("1.21.6", com.melluh.servertours.nms.v1_21_6.NmsHandler.class);
}
```

`NmsAdapter.initialize()` 会读取 `Bukkit.getBukkitVersion().split("-")[0]`，因此这里的字符串必须是服务端实际返回的 Minecraft 版本，例如 `1.21.6`。

## 8. 重点检查的 NMS 实现点

ServerTours 的 NMS 主要服务于“让玩家坐在临时实体上平滑移动”以及“发送移动/旋转相关 packet”。每次升级重点看以下文件。

### 8.1 `NmsHandler`

路径示例：

```text
modules/v1_21_6/src/main/java/com/melluh/servertours/nms/v1_21_6/NmsHandler.java
```

需要确认：

- `CraftWorld`、`CraftEntity`、`CraftPlayer` 包名已换成目标 `v1_21_Rx`。
- `WorldServer`、`PositionMoveRotation`、`Vec3D` 等类名是否仍存在。
- `ClientboundPlayerRotationPacket` 构造器是否变化。
- `PacketPlayOutVehicleMove.a(...)` 是否仍可用。
- `PacketPlayOutEntityTeleport` 构造器是否变化。
- 发送 packet 的链路 `getHandle().f.b(packet)` 是否仍可用。

### 8.2 `NmsTemporaryEntity`

路径示例：

```text
modules/v1_21_6/src/main/java/com/melluh/servertours/nms/v1_21_6/NmsTemporaryEntity.java
```

需要确认：

- `EntityArmorStand` 构造器是否变化。
- 设置隐身、无重力、无碰撞、marker、血量/尺寸相关方法名是否变化。
- `GenericAttributes.s` 是否仍代表目标属性。
- `nmsAddPassenger` 中 `getHandle().a(this, true)` 是否仍可用。
- `nmsMove`、`nmsSetLocation` 使用的位置设置方法是否仍正确。
- `RemovalReason.a` 是否仍可用。
- 覆盖的 tick、damage、interact、remove 方法签名是否仍匹配。

两个已有版本的差异可以作为参考：

```text
1.21.4: marker/尺寸相关方法为 u(true)、x(0.0f)，空 tick 方法名为 h()
1.21.5: marker/尺寸相关方法为 t(true)、d(0.0f)，空 tick 方法名为 g()
```

这类短方法名是 Mojang/Spigot 小版本升级最容易变的位置，编译错误时优先查这里。

## 9. 构建验证

### 9.1 先构建公共接口和新 NMS 模块

```powershell
mvn -pl modules/nms,modules/v1_21_6 -am -DskipTests clean package
```

如果只想验证新模块，也可以：

```powershell
mvn -pl modules/v1_21_6 -am -DskipTests package
```

### 9.2 构建最终插件

```powershell
mvn -pl modules/base -am -DskipTests clean package
```

或构建全项目：

```powershell
mvn -DskipTests clean package
```

成功后检查：

```powershell
Get-ChildItem modules\base\target\*.jar
```

目标产物应包含：

```text
modules/base/target/ServerTours-3.0.0.jar
```

## 10. 运行验证清单

在目标 Paper 服务端放入最终 jar，并安装必要依赖：

- `ProtocolLib`：必需。
- `PlaceholderAPI`：可选。
- `Floodgate`：可选，用于 Bedrock 玩家判断。
- `VentureChat`：可选，用于导览期间聊天联动。

最小验证步骤：

1. 启动服务端，确认控制台出现类似 `Initialized NMS support for 1.21.x`。
2. 执行 `/tour create nmstest`。
3. 执行 `/tour edit nmstest`。
4. 使用热键创建至少两个点。
5. 将其中一个点改为 `INTERPOLATE`，确认粒子路径显示正常。
6. 执行预览，确认玩家能被平滑移动，视角旋转正常。
7. 执行 `/tour play nmstest`，确认导览能开始、结束并恢复玩家背包/等级/游戏模式。
8. 检查控制台是否存在 NMS、packet、entity metadata、passenger、teleport 相关异常。

## 11. 常见问题

### 11.1 依赖无法解析

典型错误：

```text
Could not resolve net.minecraft.server:spigot-spigot-remapped
```

处理顺序：

1. 确认目标版本是否写对，例如 `v1_21_R5`。
2. 确认远程 Maven 仓库可访问。
3. 检查本地 `.m2` 是否已有对应 artifact。
4. 必要时用 BuildTools 生成 remapped 依赖，并同步调整 POM 坐标。

### 11.2 CraftBukkit 包不存在

典型错误：

```text
package org.bukkit.craftbukkit.v1_21_R5 does not exist
```

处理：

- 确认目标 NMS 包名是否真实存在。
- 确认 POM 中的 remapped 依赖版本与 import 包名匹配。
- 不要只改模块名 `v1_21_6`，还要改 CraftBukkit 的 `v1_21_Rx`。

### 11.3 NMS 方法名变化

常见位置：

- `WorldServer#b(entity)`
- `Entity#setPos` 类短方法。
- `EntityArmorStand` marker、invisible、gravity、size 方法。
- `ServerGamePacketListenerImpl` 发送 packet 字段。
- packet 静态工厂或构造器。

处理原则：

- 先对比上一版能工作的实现。
- 使用 IDE 进入目标 remapped 依赖查看实际方法签名。
- 优先保持公共接口不变，只在新版本模块内部适配。

### 11.4 插件启动提示版本不兼容

错误来自 `NmsAdapter.initialize()`，通常是 `NmsVersion.getCurrent()` 没匹配到当前服务端版本。

处理：

- 打印或查看 `Bukkit.getBukkitVersion()`。
- 确认 `NmsVersion` 中的版本字符串是 `1.21.x`，不是 `v1_21_Rx`。

## 12. 发布前维护建议

1. 每新增一个 NMS 模块，都在本文档第 3 节补一行映射。
2. 内部模块依赖版本建议统一使用变量，减少版本号漏改。
3. 保留每次 BuildTools 或 Maven 失败日志，尤其是 NMS 方法签名错误。
4. 新版本验证通过后，再考虑移除旧版本模块；不要在同一次提交里同时新增和删除多个 NMS 版本。
5. 每次升级至少验证 Java 玩家播放导览；如果安装 Floodgate，也验证 Bedrock 玩家 fallback teleport 移动逻辑。
