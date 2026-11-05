# Java 百词斩(图形版)

像刷单词一样刷知识点: 首页两个课程标签随时切换 ——
**☕ Java 基础**(8 章 209 卡) 与 **📐 算法设计**(按王晓东《计算机算法设计与分析》
第五版第 1-6 章, 120 卡, 适合考前速刷)。
答对进入更长间隔的复习队列, 答错当场重排再刷, 两门课程进度完全独立。

## 文件说明

- 卡片库: cards-java.jsonl / cards-algo.jsonl(与 demos-java/algo.jsonl 配套), 在程序目录
- 个人进度: C:\Users\你的用户名\.javashan\ 下按课程分文件(progress-java/algo.jsonl)

## 怎么运行

### 方式一: 桌面快捷方式(日常使用)

双击桌面上的「Java百词斩」图标即可。它指向 dist\JavaShan\JavaShan.exe,
自带运行时(含 JavaFX), 不需要电脑装 JDK。

**数据放在哪(重要)**:
- 卡片库 dist\JavaShan\cards.jsonl —— 属于"内容", 随程序升级更新, 可用记事本直接改;
- 答题进度 C:\Users\你的用户名\.javashan\progress.jsonl —— 属于"个人数据",
  与程序完全分离, 升级、重打包、移动程序目录都不会丢。想清零重来删掉它即可。

### 方式二: 从源码运行(学习/改代码时)

需要先有 JavaFX SDK(解压到项目下的 jfx/ 目录), 然后编译运行:

```
javac -encoding UTF-8 --module-path jfx/lib --add-modules javafx.controls,javafx.web -d out src/*.java
java --module-path jfx/lib --add-modules javafx.controls,javafx.web -cp "out;resources" App
```

用 IDEA 的话: 打开本项目 → 右键 src/App.java → Run 'App.main' 即可。

### 重新打包 exe(改了源码之后)

```
javac -encoding UTF-8 --module-path jfx/lib --add-modules javafx.controls,javafx.web -d out src/*.java
jar --create --file dist-input/javashan.jar -C out . -C resources .
jpackage --type app-image --name JavaShan --input dist-input --module-path jfx/lib --add-modules javafx.controls,javafx.web --main-jar javashan.jar --main-class App --dest dist
copy jfx\bin\*.dll dist\JavaShan\runtime\bin\        ← 关键! JavaFX 原生库要手动补进去
copy cards.jsonl dist\JavaShan\
```

最后一行 copy 不能省: jpackage 只打包 jar 里的内容, 而 JavaFX 的渲染原生库
(prism_d3d.dll、jfxwebkit.dll 等)在 SDK 的 bin 目录里, 不补进去会报
no suitable pipeline found。

## 项目结构

```
java-shan/
├── cards.jsonl        卡片库(209 张, 8 章, 每张带 ch 章节号 + ord 章内顺序)
├── progress.jsonl     答题进度(自动生成, 删掉即重置)
├── src/
│   ├── App.java       JavaFX 窗口: WebView 加载界面
│   ├── Bridge.java    JS ↔ Java 桥接层
│   ├── Engine.java    学习引擎: 章节解锁/有序出题/间隔复习 + 自测入口
│   ├── Card.java      卡片数据类
│   ├── CardStore.java 读卡片库
│   ├── SrsStore.java  记忆调度(SM-2 简化版)
│   └── Main.java      旧版控制台程序(保留供学习对照)
├── resources/app.html 全部界面(HTML+CSS+JS 单文件, 无需联网)
├── jfx/               JavaFX SDK 21(打包依赖, 勿删)
└── dist/JavaShan/     打包产物, 桌面快捷方式指向这里
```

## 章节与学习顺序

| 章 | 名称 | 卡数 |
|----|------|-----|
| 1 | 初见Java | 24 |
| 2 | 变量与数据类型 | 30 |
| 3 | 流程控制 | 24 |
| 4 | 数组与方法 | 26 |
| 5 | 面向对象基础 | 24 |
| 6 | 面向对象进阶 | 26 |
| 7 | 常用类与集合 | 35 |
| 8 | 异常与多线程入门 | 20 |

规则: 第 1 章默认解锁; 每学满上一章 80% 的卡片, 自动解锁下一章。
新卡按章节内的学习顺序出(每次最多 10 张), 复习卡按遗忘曲线混入。

## 玩法

- 章节地图: 第 1 章默认解锁, 学满上一章 80% 自动解锁下一章, 新卡按章内顺序出
- 每次学习: 到期的复习卡 + 最多 10 张新卡
- **当场重做**: 学习中答错的卡自动排到队尾, 一直错就一直重做, 答对才放行
- **错题本**: 答错的卡自动进本(首页右上角入口显示在册数量), 错得多的排前面;
  重做或日常复习中答对一次即移出; 被斩的卡不会进本
- **音效**: 答对/答错/完成三种程序合成音效(无音频文件, 离线可用),
  顶栏右侧 🔊 按钮可静音, 偏好保存在个人数据目录
- 选择题点选项, 或直接按键盘 A/B/C/D(数字 1~4 也行, 中文输入法下同样有效)作答
- 不会的题: 点「👀 看答案」或按 K 键 —— 直接看答案, 但按答错处理
  (进错题本、清连击、排到队尾重做), 不是免费的午餐
- 填空题输入后回车(焦点不在输入框时回车同样生效)
- 解析面板弹出后按 回车/空格 = 继续 —— 全程可纯键盘刷题, 手感拉满
- ⚔ 斩掉当前卡, 永不再现(队尾的重做副本也会一并跳过); ← 随时返回, 进度实时保存

## 记忆调度规则(简化版 SM-2)

| 连对次数 | 下次复习间隔 |
|---------|-------------|
| 1 次 | 1 天后 |
| 2 次 | 2 天后 |
| 3 次 | 4 天后 |
| 4 次 | 7 天后 |
| 5 次 | 15 天后 |
| 6 次及以上 | 30 天后 |

答错一次即清零重来。所以: 想让一张卡「消失」, 就连续答对它 6 次。

## 怎么加新卡片

用记事本打开 cards.jsonl, 照抄一行改字段:

选择题: {"id":210,"ch":1,"ord":25,"topic":"主题","tag":"概念","q":"题面","o":"A;;B;;C;;D","a":0,"e":"解析"}

填空题(不写 o, a 是答案文本): {"id":211,"ch":1,"ord":26,"topic":"主题","tag":"填空","q":"题面","a":"答案","e":"解析"}

- ch 是章节号, ord 是章内顺序(决定出题次序)
- 选择题的 a 是正确选项下标(从 0 起), o 里选项用 ;; 分隔
- 题面和解析里不要用英文双引号, 需要引用代码字面量用中文引号「」
- id 不要重复; 改完保存, 程序下次启动生效

### 代码演示卡片

答完题在解析面板显示短代码演示的卡片, 其演示内容放在 demos.jsonl(与 cards.jsonl 同目录),
按 id 关联, 格式一行一条:

```
{"id":6,"demo":"int a = 1;¶System.out.println(a + 1);  // 2"}
```

约定: ¶ 表示换行, § 表示双引号(因为文件格式里不能直接写双引号)。
想给某张卡加演示, 查它的 id, 照抄一行改内容即可。

## 可以继续扩展的方向

- 实战卡: 每周一道小编程题
- 统计页: 正确率曲线、各主题掌握度
- 错题本: 专门重刷高频错卡
- 导出/导入进度, 多设备同步
