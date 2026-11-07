# Java 百词斩 —— 项目上下文文档

> 本文档供任何 AI agent / 开发者接手本项目使用。**每次对话/修改后请更新本文档的「变更记录」。**

## 一、项目是什么

桌面刷题软件「Java 百词斩」: 仿百词斩/多邻国的间隔重复刷卡学习工具, 自包含 exe 交付。
- 用户: 一名学生(Java 初学者, 备考算法/计组/OS/四级), Windows 11, 高分屏(2880×1800, 200%缩放), Intel Arc 核显
- 交付方式: 桌面快捷方式(OneDrive\Desktop\Java百词斩.lnk) → F:\ZCodeProjects\java-shan\dist\JavaShan\JavaShan.exe
- 用户全局规则(必须遵守): 项目放 F:\ZCodeProjects\; 桌面(C:\Users\33406\OneDrive\Desktop)必须有可双击入口; exe 自包含; 交付时告知完整路径

## 二、当前功能清单

### 刷题核心
- 五门课程: ☕ Java(9章229卡, 含Spring) / 📐 算法(6章120卡, 王晓东教材) / 🖧 计组(6章108卡) / 🖥 操作系统(8章124卡, 黄红桃教材) / 📖 英语四级(7章130卡)
- 全部章节随时可学(无解锁门槛), 想学哪章点哪章
- 每课程独立: 错题本(答错进/答对出)、温故模式、会话续做(断点恢复)
- SM-2 复习(连对1/2/4/7/15/30天递增, 答错清零)、当场重做(答错排队尾直到答对)
- ⚔斩卡 / 👀看答案(K键, 按答错处理) / 选项每次洗牌
- 键盘流: A-D/1-4作答(中文输入法可), 回车/空格继续
- 概念词典: 术语高亮点击即查(每卡每词只亮一次, 最长词优先)
- 每日打卡(全局不分课程): 答满10张打卡, 月历面板, 连续/最长统计
- 卡片笔记📝(每张卡可写备注)
- 豆包并排🤖: 点按钮→百词斩左半+豆包右半, 关豆包自动回原位(250ms轮询, 最小化也触发)
- 豆包位置善后(v1.9): 并排前存豆包原始位置(%TEMP%\shan-doubao.txt, GetWindowPlacement读还原位置, 拒绝平铺位/最大化态), 会话结束守望进程等豆包下次**可见**时把它挪回原位/居中(最多10分钟) — 防止豆包记住右半屏导致独立打开默认靠右
- 反馈系统: CSS动效+分色toast+合成音效

### ✨ AI 实时演示(核心功能)
- 每张卡可点「✨ AI演示」现场生成知识点演示(5-15秒), 按课程缓存到 `~/.javashan/aidemos-{course}.jsonl`
- **双模型路由**: 英语课+OS/计组用 `qwen2.5:1.5b`(通用, 解释/语法好), Java/算法用 `qwen2.5-coder:1.5b`(代码好)
- **四路提示词分流**(详见 AiDemoStore.java):
  - Java: 生成 Java 代码(紧扣题目场景, 中文注释)
  - 算法: 生成 C++/伪代码(中文注释)
  - OS/计组: 生成中文知识点解释(是什么/为什么/怎么算)
  - 英语: 生成例句(卡片自带解析做知识点说明, AI只写例句+翻译)
- **1.5b 模型只跟英文指令**(中文指令会复述要求或输出无关内容), 代码注释和解释输出用中文
- 英语演示面板结构: 绿色区(卡片解析=知识点) + 黑底区(AI例句+翻译)
- **英语例句多样化机制**(v1.8, 防止四句抄题面同一个故事):
  - 不喂题面(题面会让1.5b锚定情境, 四句全抄), 只给考点+正确形式
  - 提示词强制: 四句不同故事/不同主语/不同动词 + 从20条场景池随机抽4条指定
  - temperature=0.5 + 固定格式示例 + Java侧输出校验(4句英文+4行中文翻译, 抄示例/丢考点则重掷, 最多3次)
  - 连词类(because/so, although/but不能连用): 走中译英路线(Java内置4句中文源句, 翻译是1.5b强项), 校验每句恰好1个连词且because须接从句
  - 时态类: 轻量标记校验(过去完成时→had+分词≥3句, 将来进行时→will be doing, 虚拟→should/wish)
  - 情境表达类(加油/welcome): 直接内置固定例句, 不走AI
  - 短语类(acquire knowledge等): MUST行强制每句包含短语任意形态

### 已删除的功能(勿恢复)
- ~~手机访问/WebServer~~ (v1.7 删除)
- ~~规则引擎动画 🎬~~ (已被 AI 演示替代)
- ~~预存演示 demos-*.jsonl~~ (已被 AI 实时生成替代, demos 文件已删除)

## 三、架构

```
java-shan/
├── src/
│   ├── App.java         JavaFX 窗口(WebView 加载 app.html, 注入 Bridge)
│   │                    自测参数: --auto-close(4秒退出+自检) / --uishow(五屏截图)
│   │                    单实例锁: ServerSocket(48765), 双开自动退出
│   ├── Bridge.java      JS↔Java 桥(方法首参=课程号; 方法名全唯一无重载; AI演示aiDemoStart/aiDemoState)
│   ├── Engine.java      学习引擎(SM-2/错题本/温故/会话; JAVA/ALGO/CO/OS/EN五套章节表; main()=自测)
│   ├── AiDemoStore.java AI实时演示(调用Ollama生成, 按课程缓存, 异步线程; main()=英语例句自测)
│   ├── CheckinStore.java 每日打卡(全局, synchronized多实例安全)
│   ├── NotesStore.java  卡片笔记
│   ├── AnimStore.java   动画合集(只记"生成过", 不存脚本)
│   ├── CardStore.java   读卡片库(demos合并已禁用)
│   ├── SrsStore.java    进度存储(iv/st/due/z/w/wb字段)
│   ├── Card.java / SoundFX.java
│   └── Main.java        旧版控制台(保留供学习)
├── resources/
│   ├── app.html         全部界面(单文件 HTML+CSS+JS, 离线自包含)
│   ├── layout-doubao.ps1    豆包并排布局脚本
│   ├── restore-doubao.ps1   关豆包回位守望脚本
│   └── icon.ico/png     程序图标
├── cards-java/algo/co/os/en.jsonl   各课程卡片(随包部署)
├── jfx/                 JavaFX SDK 21.0.5(勿删)
├── dist/JavaShan/       打包产物
└── dist-input/          jar 中转
```

个人数据(~/.javashan/):
- progress-{course}.jsonl / session-{course}.txt: 进度与续做
- settings.txt: 音效开关
- checkin.jsonl: 打卡(全局一份)
- notes-{course}.jsonl / anims-{course}.jsonl: 笔记与合集
- aidemos-{course}.jsonl: AI演示缓存
- ai.txt: AI配置(model=qwen2.5:1.5b, base=http://127.0.0.1:11434/v1/chat/completions)

## 四、内容文件格式

cards-*.jsonl 每行一卡:
`{"id":1,"ch":1,"ord":1,"topic":"主题","tag":"概念|输出|辨析|找错|填空","q":"题面","o":"A;;B;;C;;D","a":0,"e":"解析"}`
- 填空题不写 o, a 是答案文本; 选择题 a 是正确选项下标(0起), 选项用 ;; 分隔
- **铁律: 禁止英文双引号和反斜杠**(解析器按此假设), 引用字面量用「」
- 英语ch6中式英语矫正: 选项前缀 ✓=正确 / ✗=错误; topic 要写明"不能连用"而非"because...so"

## 五、构建与打包

```bash
cd /f/ZCodeProjects/java-shan
JDK="/c/Program Files/Java/jdk-21/bin"
"$JDK/javac" -encoding UTF-8 --module-path jfx/lib --add-modules javafx.controls,javafx.web -d out src/*.java
"$JDK/java" -Dfile.encoding=UTF-8 -cp out Engine    # 引擎自测
"$JDK/jar" --create --file dist-input/javashan.jar -C out . -C resources .
rm -rf dist && "$JDK/jpackage" --type app-image --name JavaShan --input dist-input \
  --module-path jfx/lib --add-modules javafx.controls,javafx.web,jdk.httpserver,java.net.http \
  --main-jar javashan.jar --main-class App --java-options "-Dprism.order=d3d" \
  --icon resources/icon.ico --dest dist
cp jfx/bin/*.dll dist/JavaShan/runtime/bin/        # 关键! 见坑#1
cp cards-*.jsonl dist/JavaShan/
```
只改 cards 时无需重打包, cp 数据文件即可。

## 六、踩过的坑

1. **JavaFX 原生库**: jpackage 只打包 jar; 渲染 dll 在 jfx/bin/, 必须 cp 到 runtime/bin/
2. **LiveConnect 重载不稳定**: 桥接方法严禁同名重载
3. **键盘事件**: WebView keydown e.key 为空(U+0000); 用 keypress charCode + 隐形输入框 #keycap
4. **性能**: 无GPU合成, 光栅化上限~25fps; 禁 box-shadow/粒子/大面板缩放动画; 避免滚动设计
5. **disabled 吞点击**: 用 .locked 类 + pointer-events:none, 不用 disabled
6. **CSS 优先级**: #study{display:flex} 压过 .hidden, 隐藏必须 !important
7. **图标缓存**: 换图标后需删 IconCache.db 或重启
8. **Git Bash 中文**: grep 中文可能失配, 用 ASCII 关键词; 不用 python heredoc(会挂)
9. **手机功能已删**: WebServer/HTTP桥/app.html手机适配 均于v1.7删除
10. **打卡全局不分课程**: checkinStatus/checkinMonth 无课程参数
11. **截图用 view.snapshot()**: 不用 java.awt.Robot(200%缩放坐标对不上)
12. **弹窗与键盘互斥**: 面板打开时 modalOpen()+typingHere() 拦截做题快捷键
13. **Bridge 字段名 java 遮蔽 java.* 包名**: 全限定名不可用, 必须 import 短名
14. **Ollama 校园网拉模型**: TUN模式劫持DNS致协议失败; 需关TUN(Mihomo Party设置)再ollama pull
15. **Ollama 核显加速**: 需 OLLAMA_VULKAN=1 + OLLAMA_IGPU_ENABLE=1(已setx持久化); 默认100% CPU
16. **1.5b 模型只跟英文指令**: 中文指令会复述要求或输出无关内容; 指令用英文, 输出要求中文
17. **改 app.html 后必须语法检查**: `node -e` 抠出 script 内容 new Function() 检查; 一行语法错误会整页白屏
18. **豆包(Electron)窗口位置**: 应用退出时记住坐标, 并排平铺后必须把它挪回去, 否则独立打开永远停在右半屏; 外部改位只在窗口**可见**时生效(MoveWindow), 最小化时 SetWindowPlacement/MoveWindow 都会被它内部状态顶回; 最大化态窗口需先 SW_SHOWNORMAL(1) 再摆位; 最小化窗口的真实几何用 GetWindowPlacement.rcNormal 读(此时 GetWindowRect 返回-32000)
19. **ps1 语法检查**: `PSParser::Tokenize` 脚本文件方式跑, 不要 bash 内联 -Command(bash 会吞 $ 变量)

## 七、自测手段

- `java -cp out Engine`: 引擎逻辑自测(五课程加载/错题本/温故)
- `JavaShan.exe --auto-close`: 4秒自检(bridge ready/章节卡数/温故调用)
- `node -e "..."`: 抠 app.html 内联 script 做 JS 语法检查(改界面后必跑)

## 八、给后续 agent 的约定

1. 修改后: 编译→自测→打包→cp数据→确认快捷方式→更新变更记录→git add -A && git commit && git push
2. 界面改动保持在 app.html 单文件内; **改后必须做 JS 语法检查**(坑#17)
3. 加课程: cards-X.jsonl → Engine 加 X_CHAPTERS → Bridge 加实例+映射 → app.html 加标签 → 自测/打包
4. 用户沟通用中文; 测试拿数据说话
5. Git 纪律: 每项修改必提交; GitHub 地址 https://github.com/ZLH131313/java-shan (ZLH131313 <3340654782@qq.com>)
6. 校园网 GitHub 间歇性被锐捷设备劫持证书(SEC_E_UNTRUSTED_ROOT); 解决: 开代理/换时段/手机热点

## 九、变更记录

- 2026-11-04: v1 初版(控制台50卡) → 图形版(JavaFX WebView)
- 2026-11-05~06: 功能迭代高峰 — 错题本/音效/续做/键盘/洗牌/看答案/词典/图标/toast/打卡/笔记; 三→五门课程; 手机功能加又删; 豆包并排+回位; Git管理
- 2026-11-07: **AI 实时演示上线** — 安装 Ollama(0.40.15)+两个模型(qwen2.5:1.5b + qwen2.5-coder:1.5b); 核显Vulkan加速(setx持久化); 四路提示词分流; 删除规则动画/预存演示/合集入口(CSS隐藏); 英语演示=卡片解析+AI例句; 修复多个AI相关问题(与题目无关/语法错误/缺翻译/标题误导)
- 2026-11-07(最新状态): 五门课711卡全部可用; AI演示按课程双模型路由; demos-*.jsonl已删除; 旧合集/动画按钮CSS隐藏; keep_alive=30m; 英语ch6选项有✓/✗标记
- 2026-11-07 v1.8: **英语AI演示多样化** — 修复四句例句全抄题面情境(如连着四个I finished my homework): 提示词去掉题面+随机场景池+不同主语/动词要求+输出校验自动重试; 连词类改中译英路线(内置中文源句); 加油/welcome内置例句; 时态/短语类加考点校验; 英语缓存aidemos-en.jsonl已清空重生成; 顺手修复Engine自测(删除对已废弃demos-*.jsonl的复制); 新增AiDemoStore.main英语例句自测
- 2026-11-07 v1.9: **修复豆包独立打开默认跑到屏幕右边** — 根因: 豆包(Electron)退出时记住窗口坐标, 并排平铺把它停在右半屏后一关, 之后每次独立打开都恢复右半屏。修复: layout存豆包原始位置(拒绝平铺位/最大化态)+豆包平铺前SW_SHOWNORMAL解最大化(之前最大化态铺不动); restore守望改两段: 先回位百词斩, 再等豆包下次可见时挪回原位/居中(10分钟上限, 新会话自动取消)。三场景实测通过(最小化/关闭重启/连续并排)
