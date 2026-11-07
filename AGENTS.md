# Java 百词斩 —— 项目上下文文档

> 本文档供任何 AI agent / 开发者接手本项目使用。**每次对话/修改后请更新本文档的「变更记录」。**

## 一、项目是什么

桌面刷题软件「Java 百词斩」: 仿百词斩/多邻国的间隔重复刷卡学习工具, 自包含 exe 交付。
- 用户: 一名学生(Java 初学者, 正备考算法考试), Windows 11, 高分屏(2880×1800, 200%缩放), Intel Arc 核显
- 交付方式: 桌面快捷方式(OneDrive\Desktop\Java百词斩.lnk) → F:\ZCodeProjects\java-shan\dist\JavaShan\JavaShan.exe
- 用户全局规则(必须遵守): 项目放 F:\ZCodeProjects\; 桌面(C:\Users\33406\OneDrive\Desktop)必须有可双击入口; exe 自包含; 交付时告知完整路径

## 二、当前功能清单(v1.9)

- 五门课程: ☕ Java 基础(9章229卡, 含第9章Spring) / 📐 算法设计(王晓东教材第1-6章, 120卡) / 🖧 计算机组成原理(6章108卡) / 🖥 操作系统(黄红桃《现代操作系统教程》8章124卡) / 📖 英语四级(7章130卡: 语境30+辨析10+搭配20+语法20+长难句15+矫正15+写作20)
- 每课程独立: **全部章节随时可学(v1.8 取消顺序解锁门槛, 想学哪章点哪章)**、错题本(答错自动进/答对出)、温故模式(已学卡乱序重刷)、会话续做(中途退出恢复断点)
- 刷题机制: 简化SM-2(连对1/2/4/7/15/30天递增, 答错清零)、当场重做(答错排队尾直到答对)、⚔斩卡(永不再现)、👀看答案(按答错处理, K键)
- 键盘流: A-D/1-4作答(中文输入法下也可), 回车/空格继续, K看答案; 选项每次出现重新洗牌
- 概念词典: 术语自动高亮(每卡每词只亮一次, 超基础词不亮, 最长词优先), 点击弹解释气泡; 答完题后选项内术语仍可点
- 代码演示: 答题后在解析面板显示深色代码块(demos-*.jsonl 按id关联)
- **🎬 分步讲解动画(v1.9, 本地规则引擎)**: 每张卡右上角「🎬 生成动画」按钮, 由 app.html 的 `buildSteps(卡片)` 按题干/选项/答案/解析/演示**确定性重算**出一串讲解步骤(认清这道题→读完题干→选项就位→逐项排除→锁定答案→拆解解析→代码演示→收尾一句话), 纯 CSS transform 播放, 不联网不调 AI 不生成脚本; 播放器支持 ‹上一步/暂停·继续/下一步›、重播、自动播放, 快捷键 空格·回车=暂停继续, ←→=单步, Esc=关; **脚本不入库**, 合集只记「这张卡生成过」+日期, 所以改了 cards-*.jsonl 动画自动跟着变
- **📝 卡片笔记(v1.9)**: 每张卡可写一段只给自己看的备注(记不住的原因/易错的点/想通的类比), 存 `~/.javashan/notes-{course}.jsonl`, 保存后显示在该卡题干下方; 文本里的反斜杠与英文双引号会被清洗, 上限400字
- **🎬 动画合集(v1.9)**: 首页 hero 下方入口卡(显示本门课已生成张数), 点开是列表面板(题干 + 章节·日期 + ▶播放 / ✕移出), 播放即重新生成并放映; 存 `~/.javashan/anims-{course}.jsonl`
- **每日打卡(全局, 不分课程)**: 一天答满 10 张卡(五门课累计, 答对答错/看答案都算)即自动打卡; 顶栏🔥芯片 + 首页 hero 胶囊显示今日进度与连续天数; 点击开月历面板(打卡日高亮/今天显示进度/‹›换月, 未来月禁止); 完成弹层给出打卡结论, 刚达成时绿toast庆祝+音效; 断一天连续清零, 最长连击永久保留
- 反馈系统: 答对CSS弹跳/答错抖动+「你选的/正确答案」标签、连击横幅、分色toast(红=错/橙=斩/绿=喜/靛=信息, 原地淡入淡出)、程序合成音效(🔊可静音)
- ~~手机访问~~ **v1.7 已删除**: 不再有 HTTP 服务(WebServer.java 已删)、不再有 📱 按钮、不再有手机小屏适配; 只有桌面窗口能刷题。打卡仍是全局一份文件, 多个桌面窗口共享

## 三、架构

```
java-shan/
├── src/                 Java 源码(默认包)
│   ├── App.java         JavaFX 窗口: WebView 加载 app.html, 注入 Bridge; 支持 --auto-close/--keytest/--uishow 自测参数
│   ├── Bridge.java      JS↔Java 桥(引擎方法第一个参数是课程 'java'/'algo'/'co'/'os'/'en'; 打卡 checkinStatus/checkinMonth 是全局无课程参数; v1.9 新增按课程的 noteGet/noteSet/noteAll/noteCount 与 animAdd/animDel/animHas/animList/animCount, 方法名全部唯一无重载); 音效设置全局
│   ├── Engine.java      学习引擎(SM-2/错题本/温故/会话JSON); JAVA/ALGO/CO/OS/EN 五套章节表, 各章不做解锁门槛; main()=自测入口
│   ├── CheckinStore.java 每日打卡(GOAL=10张/天, 连续·最长·累计·月历; 方法全 synchronized 因桌面可多实例; main()=打卡自测)
│   ├── NotesStore.java  v1.9 卡片笔记(notes-{course}.jsonl, 每行 {"id":12,"t":"2026-11-07","n":"…¶…"}, ¶=换行; 写入前清洗反斜杠/双引号并截400字; 不缓存内存副本, 多窗口互不覆盖; main()=笔记自测)
│   ├── AnimStore.java   v1.9 动画合集(anims-{course}.jsonl, 每行 {"id":12,"d":"2026-11-07"}, 只记「生成过」不存脚本; main()=合集自测)
│   ├── CardStore.java   读卡片库 + 合并 demos-*.jsonl(按id挂演示)
│   ├── SrsStore.java    进度存储(iv/st/due/z/w/wb字段)
│   ├── Card.java / SoundFX.java
│   └── Main.java        旧版控制台程序(保留供学习对照, 不再维护)
├── resources/
│   ├── app.html         全部界面(单文件 HTML+CSS+JS, 必须离线自包含, 无外部依赖)
│   └── icon.ico/png     程序图标(MakeIcon 生成)
├── cards-java/algo/co/os/en.jsonl + demos-同名.jsonl   各课程内容(随包部署到 exe 旁)
├── jfx/                 JavaFX SDK 21.0.5(打包依赖, 勿删)
├── tools/               MakeIcon(图标生成)等工具
├── dist/JavaShan/       jpackage 产物(JavaShan.exe + runtime + app/javashan.jar + 数据文件), 快捷方式指向这里
└── dist-input/          jar 打包中转
```

数据文件位置:
- 内容(卡片/演示): exe 旁(cards-*.jsonl / demos-*.jsonl), 可直接用记事本改, 下次启动生效, 无需重打包
- 个人数据: C:\Users\33406\.javashan\ (progress-{course}.jsonl 进度 / session-{course}.txt 续做 / settings.txt 音效 / **checkin.jsonl 打卡, 全局一份: 每行 {"d":"2026-11-05","n":12}, 只留最近500天** / **notes-{course}.jsonl 卡片笔记: {"id":12,"t":"2026-11-07","n":"…¶…"}** / **anims-{course}.jsonl 动画合集: {"id":12,"d":"2026-11-07"}**) —— 与程序分离, 升级不丢; 这几个文件首次写入时才创建, 刚装好看不到是正常的

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
"$JDK/javac" -encoding UTF-8 --module-path jfx/lib --add-modules javafx.controls,javafx.web -d out src/*.java
"$JDK/java" -Dfile.encoding=UTF-8 -cp out Engine          # 引擎自测(五课程加载/出题顺序/错题本逻辑)
"$JDK/java" -Dfile.encoding=UTF-8 -cp out CheckinStore    # 打卡自测
"$JDK/jar" --create --file dist-input/javashan.jar -C out . -C resources .
rm -rf dist && "$JDK/jpackage" --type app-image --name JavaShan --input dist-input \
  --module-path jfx/lib --add-modules javafx.controls,javafx.web \
  --main-jar javashan.jar --main-class App --java-options "-Dprism.order=d3d" \
  --icon resources/icon.ico --dest dist
cp jfx/bin/*.dll dist/JavaShan/runtime/bin/        # 关键! 见坑#1
cp cards-java.jsonl demos-java.jsonl cards-algo.jsonl demos-algo.jsonl cards-co.jsonl demos-co.jsonl cards-os.jsonl demos-os.jsonl cards-en.jsonl demos-en.jsonl dist/JavaShan/
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
8. **windows 编码**: Git Bash 中文 grep 可能失配(用 ASCII 关键词过滤); heredoc python 会挂(Store占位程序), 用 sed/printf
9. **手机刷题功能已于 2026-11-06 整体删除(v1.7)**: 删除范围 = src/WebServer.java 整个文件、App.java 里的 webserver 线程、Bridge.webUrl/webUrl()/openUrl()、app.html 的 📱 按钮与访问弹窗标记+`.phoneBtn/.phoneUrl/.phoneSteps` 样式+`@media (max-width:640px)` 手机小屏适配+`sfxWeb()`/`installHttpBridge()`/`location.protocol` 浏览器启动兜底。曾经为此修的「浏览器打开页面不 boot」bug(旧坑#9)也一并随代码消失。**代价: 现在没有 HTTP 服务, 无法再用浏览器整页实测界面, 界面验证只剩 `JavaShan.exe --auto-close`(自检脚本+人工看窗口), 改界面时要格外小心, 出错的界面只能从桌面窗口才发现。** 另: 手机功能由其它 agent 完善过(提交 c7c4630、410c0f3), 按用户要求删除, 别再恢复
10. **打卡是全局数据, 不分课程**: checkinStatus()/checkinMonth() 没有课程首参数(与 Bridge 其它方法约定不同, 别顺手加); 桌面上会同时开着多个窗口实例共用一份 checkin.jsonl, 所以 CheckinStore 读写全部 synchronized 且 tick() 写盘前先按日取较大值合并磁盘记录, 写盘失败静默忽略(不能打断答题)
11. **截图别用 java.awt.Robot**: 本机 200% 缩放下 JavaFX 报 DIP(窗口 920×780, Screen bounds 1440×900, outputScale 2.0), 而 AWT Robot 工作在另一套虚拟化坐标里, `stage.getX()*outputScaleX` 算出来的裁剪区会偏到桌面+别的窗口上(实测截出 933×876 的桌面图), 而且应用被遮挡就什么都看不见。现方案 = `view.snapshot(new SnapshotParameters(), null)` 直接取 WebView 节点像素(920×780, 不受缩放与遮挡影响), 再用手写的 `toBuffered()`(PixelFormat.getIntArgbInstance + int[] 行缓冲)转成 BufferedImage 存 PNG —— javafx.swing 的 SwingFXUtils 用不了, 它不在打包的模块清单里
12. **弹窗与键盘流互斥(v1.9)**: 笔记/动画播放器/合集三个 overlay 打开时, 绝不能让 A-D·回车·K 这些做题快捷键漏过去。统一用 `modalOpen()`(三个面板任一 show) + `typingHere()`(焦点在 textarea 或非 keycap 的 input) 在 keydown 顶部拦截, keypress 与 #keycap 的 input 通道同样要拦(否则中文输入法在笔记框打字会被当成作答/回车提交); 面板内只放行 Esc 与按钮点击(`onBtn`); 关闭面板要 `refocusCard()` 把焦点还给填空输入框或 #keycap, 否则做题快捷键看起来就失灵了

## 七、自测手段

- `java -cp out Engine`: 引擎逻辑自测(章节/解锁/错题本/三课程加载)
- `java -Dfile.encoding=UTF-8 -cp out CheckinStore`: 打卡逻辑自测(答满即打卡/连续3天/断签归零/今日未打满不算断/坏行容错/月历 lead 与打卡日)
- `java -Dfile.encoding=UTF-8 -cp out NotesStore`: 卡片笔记自测(读写/清空即删/换行¶编码/坏行容错/超长截断/多窗口互不覆盖)
- `java -Dfile.encoding=UTF-8 -cp out AnimStore`: 动画合集自测(加入·查重·移出/日期排序/listJson/坏行容错)
- `JavaShan.exe --auto-close`: 启动4秒自动退出, 打印 bridge ready/页面自检(章节卡数/bridge存活)/空章节标签/笔记与动画桥接自检/帧率探针
- `java ... App --keytest`: Robot 模拟真实按键测试键盘答题(需窗口前台)
- `java ... App --uishow`: **界面截图自检**(v1.9 新增, 从展开的 classpath 跑, 不是 exe): 依次渲染 首页/做题页/动画播放器/笔记面板/合集面板 五屏, 每屏 `view.snapshot()` 存 PNG 到 `%TEMP%\shan-ui-{home,study,anim,note,coll}.png` 并打印队列长度·按钮数·步骤数等断言; 用完自动关面板并把 session-java.txt 复原, 合集里临时加的卡也会撤掉。**看界面就用它, 别只靠人工点窗口**
- 界面验证首选 `--uishow` 的截图(手机/浏览器的 HTTP 整页测试路径已随手机功能删除, 见坑#9); 打包后再用 `JavaShan.exe --auto-close` 看自检输出

## 八、给后续 agent 的约定

1. 修改后必须: 编译+自测通过 → 按第五节重打包 → cp 数据文件 → 确认桌面快捷方式有效 → 更新本文档「变更记录」
2. 界面改动保持在 app.html 单文件内, 离线自包含, 遵守第六节性能纪律
3. 加课程步骤: 写 cards-X.jsonl/demos-X.jsonl → Engine 加 X_CHAPTERS → Bridge 加实例+of()映射 → app.html 加标签和 switchCourse 分支 → 自测/打包
4. 用户沟通用中文; 交付说明路径与验证方法; 测试要拿数据说话(本项目的传统)
5. **Git 纪律**: 本项目已用 Git 管理(仓库在项目根目录, 主分支 main)。每完成一项修改必须提交: `git add -A && git commit -m "简述改了什么"`; 修复 bug 时先确认能回到上一版本(`git log --oneline` 查历史, `git checkout -- 文件` 回退单文件)。禁止提交 out/dist/dist-input/jfx(见 .gitignore)。改动较大或拿不准时, 先提交一次"保存点"再动手。
6. 历史上可能有多个不同 agent 参与开发(本项目已发生过): 发现不认识的代码先读注释和 git log, 不要假设是错误而删除。

## 九、变更记录(每次对话后追加)

- 2026-11-04: v1 初版(控制台50卡) → 图形版(JavaFX WebView, 209卡8章)
- 2026-11-05: 错题本/当场重做/音效/会话续做/键盘答题(含IME)/选项洗牌/看答案(K)/概念词典/代码演示/新图标/分色toast淡入淡出; 三课程(Java/算法/计组); 手机访问服务; 一系列 JavaFX 深坑修复(详见第六节)
- 2026-11-05(晚): 项目纳入 Git 管理(main 分支, .gitignore 排除构建产物与 jfx SDK); 发现并确认外部新增的「每日打卡」功能(CheckinStore.java, 答满10张打卡/连续统计, 已接线并随包部署, 来源为另一 agent 或用户本人)
- 2026-11-05(深夜): **每日打卡功能完成(v1.6)** —— 上一条记录的"外部新增"就是同一次工作被并行 git 提交造成的误认, 打卡只有一套实现, 勿再找第二套。
  新增 CheckinStore.java(一天答满10张即打卡, 三门课累计, 记录 ~/.javashan/checkin.jsonl, 保留500天, 全 synchronized);
  Bridge.answer() 里累计一张并新增全局方法 checkinStatus/checkinMonth(方法名唯一, 无重载); WebServer 加 /api/checkin、/api/checkinmonth;
  app.html: 顶栏🔥打卡芯片 + hero 打卡胶囊(今日进度条, 连续天数, 达成后进度条转绿并点日历) + 完成弹层打卡结论行 dnCheck + 首次达成绿toast与音效 + 月历面板 ckOverlay(周一起, 打卡日渐变高亮, 今天未打满显示 x/10, ‹›换月且未来月禁点, 连续/最长/累计/本月四格统计, 回车空格Esc或点遮罩可关);
  同时修掉坑9(手机/浏览器打开页面根本不启动的老 bug)。**验证**: CheckinStore 自测8项全 true(连续3天→streak=3/best=3, 删昨天→归1, 今日未打满昨日已打→streak仍为1, 坏行不影响); 引擎自测三课程 209/120/108 不变; 浏览器实测(沙箱 user.home)错题本1张答对后 today=10 checkedToday=true streak=2, dnCheck 与 toast 文案正确, 月历 43格/11月打卡2天/换月至10月正常/未来月按钮 disabled, console 零报错; 重打包后 --auto-close: bridge ready、章节卡=8、温故调用 OK、强制重绘 60.3fps / rAF 65fps / 滚动 65fps; 桌面快捷方式 target 与 WorkDir 校验通过
- 2026-11-05(深夜二): 打卡补强 —— 实测桌面上会同时开着多个实例(最多同时 3 个), 而旧写法每次 save() 整体重写文件, 会让后写的实例把先写的当日计数覆盖掉。现 CheckinStore.tick() 写盘前先用 readInto() 把文件里的当日计数按「取较大值」吸收进内存再累加, 多窗口/手机同时刷题不丢打卡数(读取非法日期直接丢弃, 不再污染累计天数); 新增自测项「多实例互不覆盖」(A答3张+B答2张 → 文件为5), 现共 9 项全 true。注意: checkin.jsonl 只在第一次答题时才创建, 刚装好看不到文件是正常的。
- 2026-11-05(晚二): 远程仓库配置完成 —— GitHub 私有仓库 https://github.com/ZLH131313/java-shan (HTTPS 凭证已存, git 身份 ZLH131313 <3340654782@qq.com> 全局生效); v1.5+v1.6 已推送。日常同步: git add -A → git commit → git push
- 2026-11-05(夜): 修复 📱 手机按钮 —— 长URL撑破toast(加word-break); 升级为正规弹窗(大号可复制地址+使用步骤+一键电脑浏览器打开, Bridge.openUrl 用 cmd start 规避 java 字段名与包名冲突)
- 2026-11-05(夜二): 📱按钮改为「📱 手机」带文字标签; 点击逻辑加保险(任何异常都会弹出说明弹窗)
- 2026-11-06(00:30): **删除手机刷题功能(v1.7)** —— 按用户要求整体移除, 上两条对 📱 按钮的修复随之作废。移除内容: src/WebServer.java 整个文件(git rm)、App.java 里启动 webserver 线程的那段、Bridge.webUrl 字段与 webUrl()/openUrl() 两个方法、app.html 的 📱 按钮标记 + 手机访问弹窗(phonePanel) + `.phoneBtn/.phoneUrl/.phoneSteps` 样式 + `@media (max-width:640px)` 手机小屏适配块 + `sfxWeb()`/`installHttpBridge()`(浏览器端伪桥接) + boot() 的伪桥接兜底与 UA 判定 + 脚本结尾 `location.protocol` 启动分支; 编译/打包命令去掉 `jdk.httpserver`。打卡功能完整保留(含坑#10 的多实例合并), 注释里的"手机"字样改为"多窗口实例"。窗口标题 v1.6→v1.7。**验证**: 编译零错误; 引擎自测三课程 209/120/108 不变、无失败断言; CheckinStore 自测 9 项无 `): false`(含多实例 A3+B2→文件 n=5); 打包产物 app/javashan.jar 里 grep 不到 WebServer/httpserver, 包内 app.html grep 不到 "phone"; `JavaShan.exe --auto-close` → bridge ready、章节卡=8、温故 OK count=15、强制重绘 60.3fps / rAF 67fps / 滚动 66fps, 且进程现在能干净退出(旧版手机线程池会挂住 JVM); 软件运行期间 `netstat` 无任何 876x 端口监听。桌面快捷方式 target 与 WorkDir 校验通过并重新拉起窗口。
- 2026-11-06: 修复打卡/结算弹窗卡顿 —— 大面板缩放动画在无GPU合成的WebView下每帧全量重光栅化, 移除之(仅保留遮罩淡入); 月历已打卡格渐变改纯色(执行性能纪律)
- 2026-11-06(二): 双agent并行合龙 —— 新增操作系统课程(按黄红桃《现代操作系统教程》8章124卡+10演示, 章节表与卡片区remap对齐); Java课程新增第9章Spring(20卡+6演示, 总229); 词典补OS/Spring术语24条。注: v1.7(另一agent)已删除手机刷题功能(WebServer/HTTP桥), 本次按该基线构建
- 2026-11-06(三): 答题页新增「🤖 豆包」按钮 —— 点击唤起本地豆包客户端(doubao://协议优先, exe路径次之, 网页版兜底)
- 2026-11-06(四): 修复豆包按钮无反应 —— 豆包真路径是 AppData\Local\Doubao\Application\Doubao.exe(Chrome式布局); 且豆包常驻托盘窗口无标题, doubao://协议唤不出窗口。改为: 找到豆包窗口句柄直接 ShowWindow+SetForegroundWindow 置前台, 无窗口则启动exe, 网页兜底
- 2026-11-06(五): 豆包按钮升级为并排分屏 —— resources/layout-doubao.ps1(启动豆包/等窗口/百词斩左半+豆包右半/置前, SetProcessDPIAware处理200%%缩放); Bridge 从 jar 释放脚本到临时目录执行。注意: Bridge 里字段名 java 遮蔽 java.* 包名, 全限定名不可用, 必须用 import 短名(第六节坑的再现)
- 2026-11-06(六): 修复豆包并排在"进程在但托盘无窗口"场景失效 —— 脚本改为: 无窗口(含进程已在)都再次启动exe敲窗(单实例弹窗), 等待窗扩到12秒
- 2026-11-06(七): 豆包并排新增自动回位 —— 布局前存档百词斩几何(%TEMP%\shan-layout.txt), 守望脚本(restore-doubao.ps1)盯豆包窗口消失即恢复原位并置前; 已在布局状态重复点击不覆盖原始存档; 修复最小化窗口恢复(先SW_RESTORE再MoveWindow); 百词斩关闭时守望自动退出并清理
- 2026-11-06(八): 修复图标变白纸(打包时旧实例锁文件致图标写入失败, 已干净重打包并验证角点像素#0F172A); 修复关豆包不回位(根因: 双开实例致守望恢复到错误窗口 → App加48765端口单实例锁); 守望脚本加 shan-watch.log 追踪日志
- 2026-11-06(九): 豆包"关闭回位"扩展到最小化(IsIconic检测+两连判防抖); 修复最小化时存档垃圾坐标(-32000)——布局脚本量尺寸前先解除最小化并做坐标防线; 注意: 测量/移动窗口的脚本必须SetProcessDPIAware, 否则200%%缩放屏读到虚拟化坐标(数值差一倍)
- 2026-11-06(十): 回位延迟优化 —— 轮询900ms→250ms, 两连判防抖→150ms快速复核; 实测最小化到回位<1秒(原2~3秒)
- 2026-11-06(十一): 新增英语四级课程(五门课) —— ch1语境选词30卡 + ch2熟词僻义/形近辨析10卡(共40, 试刷样卡); 章节规划7章(语境/辨析/搭配/语法/长难句/中式英语/写作句型); 注意: WebServer.java 已于v1.7删除, 接新课程无需改它
- 2026-11-06(十二): **取消章节顺序解锁(v1.8)** —— 按用户要求「想学哪章就学哪章」, 五门课全部生效。Engine 删除 `UNLOCK_RATIO` 常量与 `unlocked()` 方法, `chapters()` 不再输出 `unlocked` 字段(界面契约同步改动!), `session()` 的错题本/温故/常规三条路径与 `stats()` 的 dueNow 都不再做解锁过滤; app.html 去掉 `.chapterCard.locked` 系列样式、卡片渲染的 🔒 分支与「先学完上一章80%」toast、`unlockedBefore` 变量及回首页时的「🔓 解锁新章节」提醒。章内出题次序仍由 ch/ord 决定。**验证**: 编译零错误; 引擎自测新增/改写两条断言 —— 章节 JSON 里不含 unlocked(true)、全新进度直接开第2章得到 10 张(应=10), 其余断言(第1章10张/新卡按序/斩卡不现/错题本进出)全 true, 五课程加载 Java 229、算法 120、计组 108、OS 124、四级 40; CheckinStore 自测 11 项全 true; `node --check` 校验 app.html 内联脚本语法通过; 关旧实例后重打包并 cp 78 个 dll + 10 个数据 jsonl, `JavaShan.exe --auto-close` → bridge ready、页面自检 章节卡=9、温故 OK count=15、强制重绘 60.7fps / rAF 66 / 滚动 66 / transform 65 且进程干净退出; 包内 javashan.jar grep 不到 WebServer、app.html grep 不到 unlocked/🔒/phone/http; 桌面快捷方式 target=F:\...\dist\JavaShan\JavaShan.exe 且 WorkDir 正确, 重启窗口标题为「Java 百词斩 v1.8」。注意坑: 章节卡片渲染依赖的 `c.unlocked` 字段已不存在, 旧代码若再引用会得到 undefined(视作 falsy)从而全章锁死 —— 改这块要看 Engine.chapters() 的实际字段
- 2026-11-06(十三): 修复「空章节伪装成温故知新 + 点击没反应」 —— 四级第3-7章只有章节表没有卡(cards-en.jsonl 仅 ch1:30 + ch2:10), 而渲染判据 `c.seen >= c.total` 在 0>=0 时恒真, 于是五个 0 卡章全显示「温故知新」; 点击后 session() 与 sessionWarm() 都返回 0 张, 只有一句一闪而过的 toast, 看起来就是按钮没反应。现在 app.html 渲染先判 `!c.total` → 灰色「卡库待补」芯片(新增 `.go.empty` 样式, 无 box-shadow), 点击直接提示「这一章还没有录入卡片」不再进 session(); 有卡的章节保持 复习/温故知新/继续学习 三分支。App.java 的 `--auto-close` 自检新增「空章节标签」探针(切到 en 统计各章 `.go` 文案再切回 java)。**验证**: 打包后 `--auto-close` → `空章节标签: 四级章=7 待补=5 温故=1 继续=1`(与 cards-en.jsonl 分布一致), 页面自检 章节卡=9、bridge ready、强制重绘 60.7fps / rAF 66 / 滚动 66 / transform 65, 进程干净退出; `node --check` 内联脚本通过; 引擎自测无失败断言。**遗留**: 四级 3-7 章的卡仍未录入, 要真能刷得补内容。
- 2026-11-06(十四): **补齐英语四级第3-7章(40→130卡)** —— cards-en.jsonl 新增 90 张(id 41-130): 第3章搭配与短语动词20(look up/put up with/set out to vs set about doing/介词后接doing/discuss不加about等)、第4章语法冲刺20(时态/定语与名词性从句/wish与建议虚拟/强调句/only与not until倒装/就近原则/分词状语)、第5章长难句与阅读思维15(抓主干/后置定语/插入语/同位语vs定语/部分否定/not...any more than/主旨·词义·指代·推断题打法)、第6章中式英语矫正15(because与so二选一/price配high不配expensive/knowledge不可数/add oil 等)、第7章写作句型库20(开头套语/观点句/让步对比/数据与趋势句/deserve/倒装加分句/with复合结构)。另在 demos-en.jsonl 追加 10 条对照表(id 48/51/67/72/87/96/99/106/120/128, ¶分行 + §代表双引号)。**格式坑重现**: 初稿有 15 行把 `"ord":13` 打成 `"ord":13"`, 直接不是合法 JSON —— 补卡后务必先逐行 JSON.parse 校验再合并, 校验项包括: 选项数=4、a 不越界、填空答案单值、值里不含双引号/反斜杠/;;、id 不与现有冲突、每章 ord 从1连续。**修掉的语义双解**: id77 干扰项 did we 与 could we 都合法→换成 had we; id79 题干 for the accident 与答案 is 时态打架→改成一般职责陈述; id102 干扰项 made me open my eyes 站得住→换成直译不通的 made my eyes open big; id104 干扰项 You are welcomed here. 本身合法→换成 You are welcomed to come in.。**验证**: Engine 自测新增「四级课程章节数(应=7)」与各章卡数断言 → `四级各章卡数(不应有0): true | 1=30 2=10 3=20 4=20 5=15 6=15 7=20`, total 130; 其余四课程 229/120/108/124 不变; 重打包后 `--auto-close` → `空章节标签: 四级章=7 待补=0 温故=1 继续=6`(上一条的遗留问题清零), bridge ready、章节卡=9、强制重绘 60.7fps。Engine.java 只为自测加了打印, 运行时行为不变。
- 2026-11-07: **智能演示动画 + 卡片笔记 + 动画合集(v1.9)** —— 按用户三个诉求落地(选型经确认: 本地规则引擎/分步讲解动画/首页入口卡)。
  **Java 侧**: 新增 NotesStore.java(卡片笔记, `~/.javashan/notes-{course}.jsonl`, 每行 `{"id":12,"t":"2026-11-07","n":"…¶…"}`, 写入前清洗反斜杠·英文双引号→半角、`;;`→`;`、截400字, set 空文本即删条, 不缓存内存副本故多窗口互不覆盖) 与 AnimStore.java(动画合集, `anims-{course}.jsonl`, 每行 `{"id":12,"d":"2026-11-07"}`, **只记「这张卡生成过」不存脚本**); Bridge 为五门课各建一个实例并新增 10 个方法(noteGet/noteSet/noteAll/noteCount + animAdd/animDel/animHas/animList/animCount, 首参都是课程号, **方法名全唯一无重载**, 见坑#2)。
  **界面侧(app.html 单文件)**: 做题页右上角四个按钮改为工具栏 `.cardTools`(🎬 生成动画 / 📝 笔记 / 👀 看答案 / ⚔ 斩, 替掉原先的绝对定位, 不再与术语高亮抢位置); `buildSteps(卡片)` 本地规则引擎按 题干/选项/答案/解析/演示 确定性生成 5~9 步讲解(认清这道题→读完题干→选项就位→逐项排除→锁定答案/写出答案→拆解解析→代码演示→收尾一句话记住), 填空卡少「排除干扰项」走「写出答案」分支, 有 demo 才加代码步; 播放器 `#animOverlay` 支持 上一步/暂停·继续/下一步/重播/自动播放与进度条, 纯 CSS transform 无 canvas(守坑#4); 笔记面板 `#noteOverlay`(textarea + 保存/删除/关闭, 已有笔记直接回填)与笔记条 `#noteView`(显示在题干下方); 合集面板 `#collOverlay` 列出「题干 + 章节·日期 + ▶播放 + ✕移出」, 播放走同一 buildSteps 重算, 空合集给明确引导; 首页 hero 下方新增动画合集入口卡(显示本门课张数, **一开始放在章节网格下面, Java 已 9 章要滚动才看到, 已上移到 hero 之下**)。
  **键盘/输入法互斥**(新坑#12): 三个面板打开时 keydown 顶部用 `modalOpen()`+`typingHere()` 拦截做题快捷键, keypress 与 #keycap 的 input 通道同样拦截(否则笔记框里中文输入法打字会被误判成作答/回车提前提交), 面板内只放行 Esc 与按钮点击, 关闭时 `refocusCard()` 归还焦点; 动画播放器 空格·回车=暂停/继续, ←→=单步, Esc=关。
  **截图自检**(新坑#11): App 新增 `--uishow` 模式, 依次渲染 首页/做题页/动画播放器/笔记面板/合集面板 五屏并 **`view.snapshot()` 截 WebView 像素**存 `%TEMP%\shan-ui-*.png`(先前用 java.awt.Robot 按 `stage.getX()*outputScaleX` 裁屏, 在本机 200% 缩放下坐标系对不上, 截出来是桌面+被遮挡的残图, 已弃用), 收尾把 session-java.txt 与临时合集条目复原, 自检不动用户断点进度; javafx.swing 不在模块清单里, Image→BufferedImage 用手写的 `toBuffered()`(PixelFormat.getIntArgbInstance) 完成。
  **验证(拿数据说话)**: 编译 0 错误 / 14 个 class; `node --check` 内联脚本通过; 前端规则引擎 harness **26/26 断言 PASS**(选择题9步/填空5步/无演示不出代码步/逐项排除计数与正确答案下标/解析分条上限/收尾句); `java -cp out Engine` 五课程 229/120/108/124/130 且各章无 0 卡、无失败断言; CheckinStore 自测无 `): false`; NotesStore 9 项 + AnimStore 8 项自测全 true(含「另一窗口立刻读得到」「重复加入不翻倍」「坏行容错」); 打包产物 jar 内含 NotesStore/AnimStore/app.html, 包内 app.html grep 不到 WebServer/unlocked/phone/http(0 命中); `JavaShan.exe --auto-close` → bridge ready、页面自检 章节卡=9、JS温故 OK count=15、空章节标签 四级章=7 待补=0、`笔记读回6字/删空后0字 animHas=true→删后false DOM齐全=true 选择9步 填空5步 播放器首帧=1 / 8`、强制重绘 61.0fps / rAF 66 / 滚动 64 / transform 64(**无性能回退**)且进程干净退出; `--uishow` 五屏截图已逐张人工看过: 工具栏四按钮排布正常、动画播放器进度条与四个控制按钮正常、笔记面板与占位提示正常、合集面板 seeded 一行「JDK 与 JRE 的关系是? 初见Java·2026-11-07 ▶播放 ✕」渲染正确、首页动画合集入口卡不滚动即可见; 自检结束后 `anims-java.jsonl` 与 `notes-java.jsonl` 均回到空(0 字节), 未污染用户数据; 78 个 dll + 10 个 jsonl 已随包复制, 桌面快捷方式 target=F:\ZCodeProjects\java-shan\dist\JavaShan\JavaShan.exe、WorkDir 正确并已重新拉起窗口(窗口标题 v1.9)。
- 2026-11-07: 按需求取消全部课程的章节解锁(想学哪章点哪章, Engine.unlocked恒真); 空章节(无卡片)不再显示为温故知新而是直接隐藏(四级第3-7章已由并行agent补满至130卡); 另并行agent交付v1.9智能演示动画(buildSteps规则引擎+手动分步)+卡片笔记+动画合集
- 2026-11-07(二): 修合集播放bug(合集→动画视图切换: 关合集再开动画, 关动画回合集); 算法课实战演示大批扩充(+45张, 覆盖主定理/归并/快排/DP/背包/哈夫曼/Dijkstra/MST/回溯/分支限界全考点); 四门技术课仍缺~465张演示, 后续分批补
- 2026-11-07(三): 新增✨AI实时演示(AiDemoStore+Bridge.aiDemoStart/aiDemoState, 点击现生成5-20秒, 结果按课程缓存到 ~/.javashan/aidemos-课程.jsonl 复用; 需配 ~/.javashan/ai.txt: key=xxx model=glm-4-flash base=https://open.bigmodel.cn/api/paas/v4/chat/completions); jpackage 增加 java.net.http 模块
- 2026-11-07(四): AI演示改为本地模型直连(默认Ollama localhost:11434, model=qwen2.5-coder:7b, 免key); 清除上一轮手写的45张预存算法演示(恢复原始13条); Ollama已安装(0.40.15), 模型qwen2.5-coder:7b下载中(4.7GB受校园网影响较慢); ai.txt已写入~/.javashan/
- 2026-11-07(五): 修复"章节全不见"bug — 我插入AI按钮时一行绑定代码缺了选择器前缀(.onclick=aiGenDemo;), 导致整个JS语法错误页面全白
- 2026-11-07(六): 删除🎬规则动画按钮与genAnim函数(仅保留✨AI演示为唯一演示入口); AI生成成功后自动收入合集(不再手动); 动画播放器/buildSteps保留(AI演示的展示层)
- 2026-11-07(七): 删除全部预存演示(demos-*.jsonl + demoBlock + mergeDemos); AI演示代码块改为纯黑底白字(去掉蓝色行高亮); 演示体验: 点AI→等几秒→看代码→关
- 2026-11-07(九): AI生成提速 — Ollama启用Vulkan+iGPU(OLLAMA_VULKAN=1, OLLAMA_IGPU_ENABLE=1 已setx持久化), Arc核显100%%接管推理, CPU解放; max_tokens=280上限防啰嗦; 注意: 托盘自启的Ollama重启后才继承新变量
- 2026-11-07(十): AI演示按课程分流 — 英语课生成5句渐进例句(英文指令+中文翻译, 1.5b对中文指令跟随意差), 技术课生成代码; 请求加keep_alive=30m防显存卸载导致的偶发慢
- 2026-11-07(十一): 修AI演示与题目无关 — 根因: 提示词只传topic不传题目/答案/解析; 现在完整上下文喂给AI并要求演示题目具体场景(禁止泛泛示例); 注意旧缓存aidemos-*.jsonl是不相关的旧生成, 已一并清除
