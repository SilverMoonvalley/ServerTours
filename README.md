# ServerTours

ServerTours 是一个面向 Paper/Spigot 服务端的导览插件，支持路线编辑、镜头播放和多 Minecraft 版本的 NMS 适配。项目使用 Java 17 与 Maven 多模块构建。

## 项目结构

```text
ServerTours/
├─ modules/              所有 Java 源码模块
│  ├─ base/              插件主体与最终打包模块
│  │  ├─ src/main/java/com/melluh/servertours/
│  │  │  ├─ api/         对外 API、事件与播放扩展接口
│  │  │  ├─ playback/    播放核心及 camera/event/timeline/track 子包
│  │  │  ├─ route/       路线及路线点实现
│  │  │  ├─ cmd/         命令与编辑命令
│  │  │  ├─ editmode/    路线编辑会话
│  │  │  └─ util/        通用、数学与协议工具
│  │  ├─ src/main/resources/ 配置、语言与插件描述文件
│  │  └─ src/test/java/  与源码包结构镜像的 Java 单元测试
│  ├─ nms/               跨版本 NMS 接口
│  ├─ v1_21_4/           Minecraft 1.21.4 NMS 实现
│  └─ v1_21_5/           Minecraft 1.21.5 NMS 实现
├─ docs/                 架构、维护与测试文档
├─ tests/mineflayer/     需要本地 Paper 服务端的端到端测试
├─ test-server/          本地测试服（不纳入版本控制）
├─ pom.xml               Maven 聚合项目
└─ package.json          Mineflayer 测试入口
```

`target/`、`node_modules/`、`test-server/` 和 `output/` 等本地构建或运行目录不属于项目源码，并由 Git 忽略。

## 构建与测试

运行 Java 单元测试：

```bash
mvn test
```

构建插件：

```bash
mvn -DskipTests package
```

默认产物位于 `modules/base/target/ServerTours-2.1.7.jar`。

Mineflayer 测试需要先启动测试服务器，再安装 Node.js 依赖并运行对应场景：

```bash
npm ci
npm run test:mineflayer
npm run test:smoothness
npm run test:display-camera
npm run test:playback-kernel
```

测试默认连接 `127.0.0.1:25566`；可通过各测试文件中列出的环境变量覆盖连接信息和路线名称。

## 文档

文档入口见 [docs/README.md](docs/README.md)。
