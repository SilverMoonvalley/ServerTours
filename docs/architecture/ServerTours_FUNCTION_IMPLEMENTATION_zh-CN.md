# ServerTours 功能实现说明

更新时间：2026-04-30

本文记录当前 ServerTours 插件的功能实现现状，便于后续维护、测试、升级 NMS 适配或继续补齐缺口。

## 1. 项目概况

ServerTours 是一个 Bukkit/Paper 服务器导览插件，当前版本为 `3.0.0`。项目采用 Maven 多模块结构：

- `modules/base`：插件主体、命令、路线编辑、播放逻辑和最终 shade 产物。
- `modules/nms`：跨版本 NMS 公共接口。
- `modules/v1_21_4`：Minecraft `1.21.4` 的 NMS 适配，对应 CraftBukkit 包 `v1_21_R3`。
- `modules/v1_21_5`：Minecraft `1.21.5` 的 NMS 适配，对应 CraftBukkit 包 `v1_21_R4`。

关键入口和配置：

- 主入口：`modules/base/src/main/java/com/melluh/servertours/ServerTours.java:70`
- 模块声明：`pom.xml:19`
- 版本号：`pom.xml:14`、`modules/base/src/main/resources/plugin.yml:2`
- 插件命令入口：`/tour`
- 强依赖：`ProtocolLib`
- 软依赖：`PlaceholderAPI`、`VentureChat`、`floodgate`

当前构建记录：配置团队 Nexus 后，执行 `mvn -DskipTests package` 已完成全部 Maven 模块构建，并产出 `modules/base/target/ServerTours-3.0.0.jar`。Maven 仍会报告动态项目版本和 Shade 重复类警告，但不影响本次构建成功。

## 2. 启动流程

插件启用时，`ServerTours.onEnable()` 负责完成以下初始化：

1. 设置 `ServerToursAPI` 实现。
2. 调用 `NmsAdapter.initialize()` 检测服务端类型和 Minecraft 版本，并实例化对应 NMS handler。
3. 加载 `config.yml` 和 `lang.yml`。
4. 初始化 ProtocolLib 包工具、路线管理器、编辑模式管理器、播放管理器。
5. 注册事件监听器。
6. 注册 `/tour` 下的子命令。
7. 检测并挂接 PlaceholderAPI、Floodgate、VentureChat。
8. 在主线程延迟加载磁盘上的路线数据。

命令注册位置在 `ServerTours.java:91` 开始。显式对外命令包括：

- `/tour create <name>`：创建路线。
- `/tour edit <name>`：进入路线编辑模式。
- `/tour play <name> [player/all]`：播放路线。
- `/tour playnear <name> <range> [include self?]`：给范围内玩家播放路线。
- `/tour stop <player/all>`：停止玩家导览。
- `/tour remove <name>`：删除路线。
- `/tour reload`：重载配置和语言文件。
- `/tour exit`：退出编辑模式。

另有内部/交互命令：`continue`、`deselect`、`pointaction`、`pointcommand`、`pointsetting`。当 `editMode.enableHotbarAltCommands` 为 `true` 时，还会注册 `createpoint`、`preview`、`selectpoint`、`toggleparticles`，用于替代热键栏物品操作。

## 3. 路线管理和持久化

路线由 `CraftRoute` 表示，管理器为 `CraftRouteManager`。

- 路线创建：`CraftRouteManager.createRoute()` 会将路线名转小写、放入内存 map，并触发 `RouteCreateEvent`。
- 路线删除：`CraftRouteManager.removeRoute()` 会移除内存路线、退出相关编辑状态、删除磁盘文件，并触发 `RouteRemoveEvent`。
- 路线点列表：`CraftRoute` 内部维护 `List<CraftRoutePoint>`，支持创建、插入、删除、交换顺序和按类型替换点。
- 索引刷新：每次点位变化后会重新计算插值样条、刷新编辑实体名称、刷新点位选择菜单和聊天菜单。

路线持久化由 `PersistenceManager` 负责：

- 加载入口：`PersistenceManager.load()`。
- 保存入口：`PersistenceManager.saveRoute()`。
- 删除入口：`PersistenceManager.removeRoute()`。
- 文件位置：`plugins/ServerTours/routes/<route>.yml`。
- 数据格式：每条路线保存 `name`、`usePlayerWorld`、`versions.plugin`、`versions.schema` 和 `points`。

每个路线点会保存：

- `type`
- `loc`
- `visibleTime`
- `confirmRequired`
- `confirmMode`
- `title`
- `description`
- `label`
- `titleTimings`
- `commands`
- `orbit` 专属参数，若点类型为 `ORBIT`

## 4. 编辑模式

编辑模式核心类是 `EditingPlayer`，由 `EditModeManager` 管理。

进入编辑模式时：

- 保存玩家原热键栏 0-8 格。
- 清空热键栏并放入编辑工具物品。
- 播放确认音效。
- 显示 action bar 提示。
- 为当前路线点发送客户端盔甲架实体，用于可视化编辑点。

热键栏工具对应功能：

- 创建路线点：在玩家当前位置创建默认 `STATIONARY` 点。
- 预览路线：临时进入播放流程预览当前路线。
- 选择路线点：打开分页点位选择菜单。
- 开关粒子：切换插值/环绕轨迹粒子显示。
- 退出编辑：恢复热键栏并保存路线。

点位选择和编辑方式：

- ProtocolLib 监听 `USE_ENTITY`，玩家点击客户端盔甲架时会选中或取消选中路线点。
- 选中点后构建聊天菜单，菜单入口在 `EditingPlayer.buildMenu()`。
- 聊天菜单可以配置点标签、点类型、停留时间、标题、标题淡入/停留/淡出、描述、是否确认继续、确认方式、触发命令，以及 ORBIT 点的半径、速度、起始角度、高度偏移。
- `pointaction` 支持移动到当前位置、传送到点、删除点、调整顺序、从当前点预览。
- `pointcommand` 支持添加/删除命令，设置执行者和触发时机。
- `pointsetting` 支持修改点位属性。

退出编辑模式时：

- 如正在预览，会结束预览播放。
- 恢复玩家热键栏。
- 清空 action bar。
- 移除客户端编辑实体。
- 关闭聊天菜单。
- 将路线保存到磁盘。

## 5. 路线点类型

路线点类型定义在 `RoutePointType`：

- `STATIONARY`：静止停留。播放位置始终为该点位置，不显示轨迹粒子。
- `INTERPOLATE`：从当前点平滑移动到下一点。使用 `CardinalSpline` 计算样条，并根据前后点和确认需求选择缓入/缓出模式。
- `ORBIT`：以点位为中心环绕旋转。可配置距离、速度、高度偏移、起始角度，并显示圆形轨迹粒子。

插值点风险提示：

- 如果没有下一个点，`INTERPOLATE` 无法计算移动目标。
- 如果当前点和下一点不在同一世界，插值点无效。
- 这些警告受 `editMode.enableWarnings` 控制。

## 6. 播放模式

播放由 `CraftPlaybackManager.showTour()` 创建 `CraftTouringPlayer`，随后每 tick 调用 `CraftTouringPlayer.tick()` 推进。

播放初始化时会：

- 如果玩家正在编辑同一路线，进入预览状态并隐藏编辑实体。
- 记录退出位置。
- 必要时传送到路线首点世界。
- 保存玩家状态。
- 清空背包、清空经验条、设置满血、关闭碰撞。
- 根据配置和玩家类型设置游戏模式：Java 玩家可用旁观模式，Bedrock 玩家使用冒险/飞行方案。
- 通过 ProtocolLib/NMS 设置玩家实体隐身。
- 如启用 `playMode.disableChat`，通过 VentureChat hook 暂停跨服聊天。
- 初始化移动处理器并进入第一个点。

每个路线点播放时会：

- 执行上一点的 `EXIT` 命令。
- 更新当前点和剩余 tick。
- 如果配置了标题，发送 title/subtitle，支持 `\n` 分割和 PlaceholderAPI。
- 如果配置了描述，发送聊天描述，支持 `\n` 分割和 PlaceholderAPI。
- 执行当前点的 `ENTER` 命令。
- 触发 `RoutePlaybackPointEvent`。

播放推进规则：

- `STATIONARY` 返回固定位置。
- `INTERPOLATE` 按样条和缓动函数移动到下一点。
- `ORBIT` 按角速度围绕中心点移动，并自动面向中心。
- 进度可通过经验条显示，配置项为 `playMode.xpBarProgress`。
- action bar 可显示观看状态和退出提示，配置项为 `playMode.actionBarEnabled`。

确认继续机制：

- 点位可设置 `confirmRequired`。
- 确认方式为 `MOUSE`、`CHAT`、`KEYBOARD`。
- `MOUSE`：左键确认。
- `CHAT`：聊天按钮执行 `/tour continue`。
- `KEYBOARD`：交换副手键确认。
- `INTERPOLATE` 的 `confirmUponEnter` 为 `true`，确认时机和非插值点不同。

退出播放时会：

- 触发 `RoutePlaybackEndEvent`。
- 清理移动处理器。
- 恢复玩家状态。
- 传送回进入导览前的位置。
- 恢复 VentureChat 聊天状态。
- 延迟取消玩家隐身。
- 执行当前点的 `QUIT` 命令。
- 从播放管理器注销。

玩家也可以在允许退出时通过 SHIFT，或在启用移动退出后通过方向输入退出导览；退出权限受 `playMode.allowExit` 控制。

## 7. Java Display 相机实现

Java 玩家统一使用 `DisplayCameraMovementHandler`：

- 为当前玩家创建仅其可见的 packet-only `TextDisplay`。
- 将客户端相机目标切换到该 Display，并发送绝对目标位置和旋转。
- 连续播放利用客户端 Display 插值；暂停、跳转和不连续旋转会替换相机实体以硬重定位。
- 玩家实体只作为区块加载锚点，按帧数或距离周期性传送，不再挂载任何载具。
- 清理时先把客户端相机切回玩家，再销毁临时 Display。

播放相机面向现代 Java 客户端，不再提供盔甲架载具或 Bedrock 传送 fallback。

## 8. ProtocolLib 和 NMS

ProtocolLib 主要处理三类事情：

- 编辑模式中监听玩家点击客户端盔甲架实体。
- 播放模式中拦截移动/传送确认相关包，避免客户端状态冲突。
- 对需要隐藏的实体补发或修改 metadata，使其对客户端不可见。

NMS 主要用于：

- 构造仅对单个玩家可见的临时 `TextDisplay`。
- 发送 Display spawn、metadata、camera target、teleport 和 destroy 包。
- 按正确顺序把客户端相机切到 Display，并在清理时切回玩家。
- 在新版服务端中发送实体传送包。

当前 NMS 版本分发在 `NmsVersion`：

- `1.21.4` -> `com.melluh.servertours.nms.v1_21_4.NmsHandler`
- `1.21.5` -> `com.melluh.servertours.nms.v1_21_5.NmsHandler`

## 9. 配置能力

`config.yml` 当前主要分为两组：

编辑模式：

- `editMode.actionBarEnabled`
- `editMode.selectPlacedPoint`
- `editMode.enableWarnings`
- `editMode.particles.enabledByDefault`
- `editMode.particles.showSelectedOnly`
- `editMode.enableHotbarAltCommands`

播放模式：

- `playMode.actionBarEnabled`
- `playMode.allowExit`
- `playMode.useSpectator`
- `playMode.xpBarProgress`
- `playMode.disableCommands`
- `playMode.disableChat`
- `playMode.sendDescriptionDashes`

代码读取了 `editMode.forceEnter`，用于允许一个玩家强制接管其他玩家正在编辑的路线，但默认 `config.yml` 里没有该项。

## 10. API 和事件

插件暴露 `ServerToursAPI`，可获取路线管理器和播放管理器。当前实现中会触发这些事件：

- `RouteCreateEvent`
- `RouteRemoveEvent`
- `RoutesLoadEvent`
- `RoutePlaybackBeginEvent`
- `RoutePlaybackPointEvent`
- `RoutePlaybackEndEvent`

外部插件可以基于这些事件监听路线创建、删除、加载、播放开始、播放点切换和播放结束。

## 11. 已知风险和待处理点

1. `PacketUtil.createEntityMetadataPacket()` 当前有 TODO，且真正写入 `WrappedDataValue` 的代码被注释掉了。这会影响编辑点名称、盔甲架姿态、实体隐身等 metadata 包的实际显示效果。
2. `editMode.forceEnter` 被代码读取，但默认配置文件没有暴露，导致“强制接管别人正在编辑的路线”几乎不可配置。
3. 团队 Nexus 已解决当前 `spigot-spigot-remapped` 依赖获取问题；全项目构建成功并已产出 `modules/base/target/ServerTours-3.0.0.jar`。
4. `usePlayerWorld` 已在路线文件中持久化并在播放时读取，但当前代码中没有明显的命令或菜单入口供普通管理员编辑该值。
