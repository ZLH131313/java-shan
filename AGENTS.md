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

### ✨ AI 实时演示(仅☕ Java课程)
- Java课每张卡可点「✨ AI演示」现场生成实战代码演示(5-15秒), 缓存到 `~/.javashan/aidemos-java.jsonl`; 其余四课按钮隐藏(v1.10停用)
- 模型: `~/.javashan/ai.txt` 的 `model=`(当前 qwen2.5-coder:1.5b), 本地 Ollama
- **1.5b 模型只跟英文指令**(中文指令会复述要求或输出无关内容), 指令用英文, 代码注释要求中文

### 🌐 AI翻译(仅📖 英语四级)
- **答题后**的解析面板出现「🌐 AI翻译」按钮: 把本题正确答案句翻成地道中文, 点按钮→翻译(约3-8秒)→结果展示在按钮下方
- 只在英语课且答案含英文时出现; 填空题翻「题干挖空处填上答案后的完整句」, 选择题翻正确选项整句
- 单槽异步不缓存(AiTransStore, 每次现翻); 模型固定 qwen2.5:1.5b(翻译是小模型强项), Bridge方法 aiTransStart/aiTransState/aiTransWarm
- **进英语课自动预热**(v1.11.1): switchCourse('en')后台发迷你请求把模型拉进显存(keep_alive 30m), 否则隔半小时首次点击要等冷加载, 用户等不及按回车走了, 结果出来时面板已关=「点了不出结果」; 轮询30秒上限+日志 ~/.javashan/aitrans.log

### 已删除的功能(勿恢复)
- ~~手机访问/WebServer~~ (v1.7 删除)
- ~~规则引擎动画 🎬~~ (已被 AI 演示替代)
- ~~预存演示 demos-*.jsonl~~ (已被 AI 实时生成替代, demos 文件已删除)
- ~~算法/计组/OS/英语四课的 AI 演示~~ (v1.10 停用 — 1.5b 在非代码任务上质量不稳: 英语例句抄题面/丢翻译/跑题, OS解释平庸; 代码类才是 coder 模型强项。UI: 按钮仅java课显示+点击守卫; Bridge: ai()对非java返回null; AiDemoStore已瘦身为Java单路提示词; aidemos-{co,en,algo,os}.jsonl已删, 英语的提示词工程(场景池/校验重试/翻译修复/内置例句)全部移除)

## 三、架构

```
java-shan/
├── src/
│   ├── App.java         JavaFX 窗口(WebView 加载 app.html, 注入 Bridge)
│   │                    自测参数: --auto-close(4秒退出+自检) / --uishow(五屏截图)
│   │                    单实例锁: ServerSocket(48765), 双开自动退出
│   ├── Bridge.java      JS↔Java 桥(方法首参=课程号; 方法名全唯一无重载; AI演示aiDemoStart/aiDemoState)
│   ├── Engine.java      学习引擎(SM-2/错题本/温故/会话; JAVA/ALGO/CO/OS/EN五套章节表; main()=自测)
│   ├── AiDemoStore.java AI实时演示(仅Java课, 调Ollama生成coder代码, 按卡缓存; main()=Java演示自测)
│   ├── AiTransStore.java AI翻译(仅英语课, 单槽异步现翻不缓存; main()=翻译自测)
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
- aidemos-java.jsonl: Java课AI演示缓存
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
- `java -cp out AiDemoStore`: Java课AI演示生成自测(需Ollama)
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
- 2026-11-07 v1.9.1: **修复英语演示偶发无翻译** — 兜底路径把3次重掷都丢翻译的结果永久缓存了(实例: 卡41 look up)。修复: 重掷耗尽后句子够4句就单独调一次「只做中译」的修复调用, Java拼回英文+(中文)格式; 修复失败返回错误不缓存。已清英语缓存
- 2026-11-07 v1.9.2: **修复英语演示句子与题目无关** — 三个洞: ①修复路径绕过考点校验(卡41把抄的格式示例+3句场景句翻译入库); ②短语卡3/4阈值放进1句无关句; ③14张阅读技巧卡无考点可校验AI必跑题。修复: 考点正则统一(词干带\b/时态标记), 修复路径复用过滤且相关句<3报错不缓存; 短语卡阈值升4/4+强制人类主语(防"火车旅行查地图"式无灵主语); 技巧卡+情境卡共16个topic内置手写演示。尝试次数3→4; 已清英语缓存
- 2026-11-07 v1.10: **AI演示收窄为仅Java课程** — 用户决定移除非Java模块的AI演示(1.5b在英语/解释类任务上问题反复: 抄题面/丢翻译/跑题, 修不胜修)。改法: switchCourse里按钮显隐(java课才显示)+aiGenDemo课程守卫; Bridge.ai()非java返回null; AiDemoStore瘦身为Java单路提示词(删掉英语的全部提示词工程与四路分流); aidemos-{algo,co,os,en}.jsonl已删, 保留aidemos-java.jsonl与ai.txt; 演示播放器对非Java课成为无入口死代码(合集入口本就CSS隐藏)。Engine自测/JS语法检查/auto-close全过, Java演示真实生成验证通过
- 2026-11-07 v1.11: **英语课新增「🌐 AI翻译」** — 答题后解析面板内一键把正确答案翻成地道中文(约3-8秒): 按钮/结果区放解析面板里天然满足「只能答题后用」; 仅英语课且答案含英文时显示; 填空翻补全句/选择翻正确选项; 新增AiTransStore(单槽异步, qwen2.5:1.5b, temperature 0.2, 不缓存), 复用AiDemoStore.config/postChat; Bridge加aiTransStart/aiTransState。实测两句翻译地道准确
- 2026-11-07 v1.11.1: **修复AI翻译「点了不出结果」** — 根因: qwen2.5:1.5b超过keep_alive(30m)被Ollama踢出显存后, 首次点击触发冷加载(十几秒+), 用户等不及按回车进下一张, 翻译结果回来时解析面板已关闭。修复: 进英语课tab即后台预热模型(迷你请求+keep_alive续命); 轮询加30秒上限明确提示超时; AiTransStore加日志(~/.javashan/aitrans.log)便于排查
