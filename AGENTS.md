# Java 百词斩 —— 项目上下文文档

> 本文档供任何 AI agent / 开发者接手本项目使用。**每次对话/修改后请更新本文档的「变更记录」。**

## 一、项目是什么

桌面刷题软件「Java 百词斩」: 仿百词斩/多邻国的间隔重复刷卡学习工具, 自包含 exe 交付。
- 用户: 一名学生(Java 初学者, 正备考算法考试), Windows 11, 高分屏(2880×1800, 200%缩放), Intel Arc 核显
- 交付方式: 桌面快捷方式(OneDrive\Desktop\Java百词斩.lnk) → F:\ZCodeProjects\java-shan\dist\JavaShan\JavaShan.exe
- 用户全局规则(必须遵守): 项目放 F:\ZCodeProjects\; 桌面(C:\Users\33406\OneDrive\Desktop)必须有可双击入口; exe 自包含; 交付时告知完整路径

## 二、当前功能清单(v1.5+)

- 三门课程: ☕ Java 基础(8章209卡) / 📐 算法设计(王晓东教材第1-6章, 120卡) / 🖧 计算机组成原理(6章108卡)
- 每课程独立: 章节顺序解锁(学满上章80%解锁下章)、错题本(答错自动进/答对出)、温故模式(已学卡乱序重刷)、会话续做(中途退出恢复断点)
- 刷题机制: 简化SM-2(连对1/2/4/7/15/30天递增, 答错清零)、当场重做(答错排队尾直到答对)、⚔斩卡(永不再现)、👀看答案(按答错处理, K键)
- 键盘流: A-D/1-4作答(中文输入法下也可), 回车/空格继续, K看答案; 选项每次出现重新洗牌
- 概念词典: 术语自动高亮(每卡每词只亮一次, 超基础词不亮, 最长词优先), 点击弹解释气泡; 答完题后选项内术语仍可点
- 代码演示: 答题后在解析面板显示深色代码块(demos-*.jsonl 按id关联)
- 反馈系统: 答对CSS弹跳/答错抖动+「你选的/正确答案」标签、连击横幅、分色toast(红=错/橙=斩/绿=喜/靛=信息, 原地淡入淡出)、程序合成音效(🔊可静音)
- 手机访问: 软件启动自动开 HTTP 服务(8765起), 同 WiFi 手机浏览器可用, 进度与电脑同步, 页面自适应小屏, 📱按钮显示当前地址

## 三、架构

```
java-shan/
├── src/                 Java 源码(默认包)
│   ├── App.java         JavaFX 窗口: WebView 加载 app.html, 注入 Bridge, 启动手机服务; 支持 --auto-close/--keytest 自测参数
│   ├── Bridge.java      JS↔Java 桥(所有方法第一个参数是课程 'java'/'algo'/'co'); 音效设置全局
│   ├── Engine.java      学习引擎(章节解锁/SM-2/错题本/温故/会话JSON); JAVA/ALGO/CO_CHAPTERS 三套章节表; main()=自测入口
│   ├── CardStore.java   读卡片库 + 合并 demos-*.jsonl(按id挂演示)
│   ├── SrsStore.java    进度存储(iv/st/due/z/w/wb字段)
│   ├── Card.java / SoundFX.java / WebServer.java(HTTP服务, 端点见源码 /api/*)
│   └── Main.java        旧版控制台程序(保留供学习对照, 不再维护)
├── resources/
│   ├── app.html         全部界面(单文件 HTML+CSS+JS, 必须离线自包含, 无外部依赖)
│   └── icon.ico/png     程序图标(MakeIcon 生成)
├── cards-java/algo/co.jsonl + demos-java/algo/co.jsonl   各课程内容(随包部署到 exe 旁)
├── jfx/                 JavaFX SDK 21.0.5(打包依赖, 勿删)
├── tools/               MakeIcon(图标生成)等工具
├── dist/JavaShan/       jpackage 产物(JavaShan.exe + runtime + app/javashan.jar + 数据文件), 快捷方式指向这里
└── dist-input/          jar 打包中转
```

数据文件位置:
- 内容(卡片/演示): exe 旁(cards-*.jsonl / demos-*.jsonl), 可直接用记事本改, 下次启动生效, 无需重打包
- 个人数据: C:\Users\33406\.javashan\ (progress-{course}.jsonl 进度 / session-{course}.txt 续做 / settings.txt 音效) —— 与程序分离, 升级不丢

## 四、内容文件格式(卡片宪法)

cards-*.jsonl 每行一卡:
`{"id":1,"ch":1,"ord":1,"topic":"主题","tag":"概念|输出|辨析|找错|填空","q":"题面","o":"A;;B;;C;;D","a":0,"e":"解析"}`
- 填空题不写 o, a 是答案文本(忽略大小写); 选择题 a 是正确选项下标(0起), 选项用 ;; 分隔
- 章节号 ch + 章内顺序 ord 决定学习次序; id 课程内唯一
- **铁律: 题面/选项/解析里禁止英文双引号和反斜杠**(简单解析器按此假设), 引用字面量用「」
- 单考点、15秒可答、答错有解释; 选项渲染时会洗牌, 出题时不必随机化 a

demos-*.jsonl: `{"id":卡片id,"demo":"代码"}`; 代码中 **§=双引号, ¶=换行**(前端渲染时还原); 代码会做HTML转义, 可含 < >

## 五、构建与打包(完整命令)

```bash
cd /f/ZCodeProjects/java-shan
JDK="/c/Program Files/Java/jdk-21/bin"
"$JDK/javac" -encoding UTF-8 --module-path jfx/lib --add-modules javafx.controls,javafx.web,jdk.httpserver -d out src/*.java
"$JDK/java" -Dfile.encoding=UTF-8 -cp out Engine          # 引擎自测(三课程加载/解锁/错题本逻辑)
"$JDK/jar" --create --file dist-input/javashan.jar -C out . -C resources .
rm -rf dist && "$JDK/jpackage" --type app-image --name JavaShan --input dist-input \
  --module-path jfx/lib --add-modules javafx.controls,javafx.web,jdk.httpserver \
  --main-jar javashan.jar --main-class App --java-options "-Dprism.order=d3d" \
  --icon resources/icon.ico --dest dist
cp jfx/bin/*.dll dist/JavaShan/runtime/bin/        # 关键! 见坑#1
cp cards-java.jsonl demos-java.jsonl cards-algo.jsonl demos-algo.jsonl cards-co.jsonl demos-co.jsonl dist/JavaShan/
```
只改了 cards/demos 时无需重打包, cp 数据文件到 dist/JavaShan/ 即可。
桌面快捷方式: PowerShell WScript.Shell 创建 .lnk → target=dist\JavaShan\JavaShan.exe, WorkDir=dist\JavaShan; 脚本文件需 UTF-8 BOM 否则中文乱码。

## 六、踩过的坑(血泪经验, 勿重蹈)

1. **JavaFX 原生库**: jpackage 只打包 jar 内容; 渲染 dll(prism_d3d.dll/jfxwebkit.dll等)在 SDK 的 jfx/bin/, 必须 cp 到 dist/JavaShan/runtime/bin/, 否则报 no suitable pipeline found
2. **LiveConnect 重载派发不稳定**: JS 调 Java 方法严禁同名重载(曾导致 session(int) vs session(int,boolean) 随机失效→按钮无反应); 桥接方法全部唯一命名
3. **键盘事件**: JavaFX WebView 真实按键的 keydown e.key 为空(U+0000); `'abcd'.indexOf('')===0` 会误选A! 字母走 keypress 的 charCode + 隐形输入框(#keycap)的 input 事件; 回车用"空按键+80ms内无字符跟随"启发式(必须被任何 input 事件取消, 否则中文输入法打字会被误判成回车提前提交)
4. **性能**: 该机器 WebView 内容光栅化上限约25fps且无GPU合成(实测), box-shadow 全部禁用(用边框/底色), 无粒子特效(用纯CSS transform动画), **页面设计避免滚动**(主页2列网格一屏放下); `-Dprism.order=d3d` 开GPU合成; confetti canvas 已废弃
5. **disabled 元素吞点击**: 答完题选项用 .locked 类+pointer-events:none(术语 .term 单独放行 auto), 不能用 disabled
6. **CSS 优先级**: #study{display:flex} 压过 .screen.hidden{display:none}, 隐藏必须 !important
7. **图标缓存**: 换 exe 图标后 Windows 可能仍显示旧图标, 需删 IconCache.db + 重建快捷方式(甚至重启)
8. **windows 编码**: Git Bash 中文 grep 可能失配(用 ASCII 关键词过滤); heredoc python 会挂(Store占位程序), 用 sed/printf; jdk.httpserver 模块必须显式 --add-modules

## 七、自测手段

- `java -cp out Engine`: 引擎逻辑自测(章节/解锁/错题本/三课程加载)
- `JavaShan.exe --auto-close`: 启动4秒自动退出, 打印 bridge ready/页面自检(章节卡数/bridge存活)/帧率探针
- `java ... App --keytest`: Robot 模拟真实按键测试键盘答题(需窗口前台)
- 手机服务: 启动后 `curl http://127.0.0.1:8765/api/stats?course=co`

## 八、给后续 agent 的约定

1. 修改后必须: 编译+自测通过 → 按第五节重打包 → cp 数据文件 → 确认桌面快捷方式有效 → 更新本文档「变更记录」
2. 界面改动保持在 app.html 单文件内, 离线自包含, 遵守第六节性能纪律
3. 加课程步骤: 写 cards-X.jsonl/demos-X.jsonl → Engine 加 X_CHAPTERS → Bridge 加实例+of()映射 → WebServer.course() 加映射 → app.html 加标签和 switchCourse 分支 → 自测/打包
4. 用户沟通用中文; 交付说明路径与验证方法; 测试要拿数据说话(本项目的传统)
5. **Git 纪律**: 本项目已用 Git 管理(仓库在项目根目录, 主分支 main)。每完成一项修改必须提交: `git add -A && git commit -m "简述改了什么"`; 修复 bug 时先确认能回到上一版本(`git log --oneline` 查历史, `git checkout -- 文件` 回退单文件)。禁止提交 out/dist/dist-input/jfx(见 .gitignore)。改动较大或拿不准时, 先提交一次"保存点"再动手。
6. 历史上可能有多个不同 agent 参与开发(本项目已发生过): 发现不认识的代码先读注释和 git log, 不要假设是错误而删除。

## 九、变更记录(每次对话后追加)

- 2026-11-04: v1 初版(控制台50卡) → 图形版(JavaFX WebView, 209卡8章)
- 2026-11-05: 错题本/当场重做/音效/会话续做/键盘答题(含IME)/选项洗牌/看答案(K)/概念词典/代码演示/新图标/分色toast淡入淡出; 三课程(Java/算法/计组); 手机访问服务; 一系列 JavaFX 深坑修复(详见第六节)
- 2026-11-05(晚): 项目纳入 Git 管理(main 分支, .gitignore 排除构建产物与 jfx SDK); 发现并确认外部新增的「每日打卡」功能(CheckinStore.java, 答满10张打卡/连续统计, 已接线并随包部署, 来源为另一 agent 或用户本人)
