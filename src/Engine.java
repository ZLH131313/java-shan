import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 学习引擎: 管理按顺序出题、间隔复习, 并把结果拼成 JSON 供界面调用。
 * 章节规则: 全部章节随时可学(不做解锁门槛), 章内仍按 ch/ord 决定的学习次序出题。
 */
public class Engine {

    /** 课程章节元数据: {编号, 名称, 简介} */
    static final String[][] JAVA_CHAPTERS = {
            {"1", "初见Java", "环境、工具与第一个程序"},
            {"2", "变量与数据类型", "八大类型、运算与类型转换"},
            {"3", "流程控制", "分支与循环的组合拳"},
            {"4", "数组与方法", "批量数据与逻辑封装"},
            {"5", "面向对象基础", "类、对象、封装与构造器"},
            {"6", "面向对象进阶", "继承、多态、抽象与接口"},
            {"7", "常用类与集合", "String、包装类与容器"},
            {"8", "异常与多线程入门", "稳健程序的第一步"},
            {"9", "Spring 入门", "IOC、AOP、事务与SpringBoot"}
    };

    /** 算法课程(王晓东《计算机算法设计与分析》第五版 第1-6章) */
    static final String[][] ALGO_CHAPTERS = {
            {"1", "概述与复杂度", "算法特性、渐进记号与主定理"},
            {"2", "递归与分治", "递归方程与经典分治问题"},
            {"3", "动态规划", "最优子结构与经典DP模型"},
            {"4", "贪心算法", "贪心选择与经典问题"},
            {"5", "回溯法", "解空间树搜索与剪枝"},
            {"6", "分支限界法", "广度搜索与限界函数"}
    };

    /** 计算机组成原理课程(经典教材章节结构) */
    static final String[][] CO_CHAPTERS = {
            {"1", "计算机系统概述", "冯诺依曼结构与性能指标"},
            {"2", "数据表示与运算", "补码、浮点数与校验码"},
            {"3", "存储系统", "主存、Cache 与虚拟存储"},
            {"4", "指令系统", "指令格式与寻址方式"},
            {"5", "中央处理器", "数据通路、控制器与流水线"},
            {"6", "总线与输入输出", "总线、中断与DMA"}
    };

    /** 操作系统课程(黄红桃主编《现代操作系统教程》, 清华大学出版社) */
    static final String[][] OS_CHAPTERS = {
            {"1", "操作系统概述", "概念、特征与发展历程"},
            {"2", "系统运行环境", "体系结构、中断与系统调用"},
            {"3", "进程与线程", "PCB、状态转换与上下文切换"},
            {"4", "处理机调度", "调度算法与周转时间计算"},
            {"5", "同步互斥与死锁", "信号量、经典问题与银行家算法"},
            {"6", "存储管理", "分页分段与虚拟存储置换"},
            {"7", "文件管理", "逻辑结构、目录与磁盘分配"},
            {"8", "设备管理", "IO控制方式、SPOOLing与磁盘调度"}
    };

    /** 英语四级课程 */
    static final String[][] EN_CHAPTERS = {
            {"1", "高频词·语境识记", "在句子里认识单词"},
            {"2", "熟词僻义与形近词", "一词多义与孪生词陷阱"},
            {"3", "搭配与短语动词", "地道表达的地基"},
            {"4", "语法冲刺", "时态、从句与虚拟语气"},
            {"5", "长难句与阅读思维", "先抓骨架再读血肉"},
            {"6", "中式英语矫正", "像母语者一样表达"},
            {"7", "写作句型库", "翻译与写作即插即用"}
    };

    static final int NEW_PER_SESSION = 10;   // 每次学新卡上限

    private final List<Card> cards;
    private final SrsStore srs;
    private final String[][] CHAPTERS;       // 本引擎对应课程的章节表
    private final LocalDate today = LocalDate.now();

    public Engine(Path cardsFile, Path progressFile, String[][] chapters) throws IOException {
        this.cards = CardStore.load(cardsFile);
        this.srs = SrsStore.load(progressFile);
        this.CHAPTERS = chapters;
    }

    /** 章节列表 JSON: 供首页地图渲染 */
    public String chapters() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < CHAPTERS.length; i++) {
            int id = Integer.parseInt(CHAPTERS[i][0]);
            int total = 0, seen = 0, due = 0;
            for (Card c : cards) {
                if (c.chapter != id) continue;
                total++;
                if (!srs.isNew(c.id)) seen++;
                if (!srs.isNew(c.id) && srs.isDue(c.id, today)) due++;
            }
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(id)
              .append(",\"name\":\"").append(CHAPTERS[i][1]).append("\"")
              .append(",\"desc\":\"").append(CHAPTERS[i][2]).append("\"")
              .append(",\"total\":").append(total)
              .append(",\"seen\":").append(seen)
              .append(",\"due\":").append(due)
              .append(",\"pct\":").append(total == 0 ? 0 : seen * 100 / total)
              .append('}');
        }
        return sb.append(']').toString();
    }

    /**
     * 开一组学习卡片。
     * chapter > 0: 该章到期复习卡(乱序) + 新卡按学习顺序补足;
     * chapter = 0: 所有章节的到期复习卡(不含新卡);
     * chapter = -1: 错题本 —— 在册错题按累计错次从多到少, 最多 15 张。
     */
    public String session(int chapter) {
        return session(chapter, false);
    }

    /**
     * warm = true 时为「温故」模式: 忽略到期时间, 该章(或全部)已学卡乱序重刷, 最多 15 张。
     * 用于学完一章后随时回去巩固。
     */
    public String session(int chapter, boolean warm) {
        List<Card> queue;
        boolean wrongMode = chapter == -1;

        if (wrongMode) {
            queue = new ArrayList<>();
            for (Card c : cards) {
                SrsStore.CardState s = srs.state(c.id);
                if (s != null && s.inWrong && !s.suspended) queue.add(c);
            }
            queue.sort((a, b) -> Integer.compare(srs.state(b.id).wrong, srs.state(a.id).wrong));
            if (queue.size() > 15) queue = queue.subList(0, 15);
        } else if (warm) {
            queue = new ArrayList<>();
            for (Card c : cards) {
                SrsStore.CardState st = srs.state(c.id);
                if (st != null && !st.suspended && (chapter == 0 || c.chapter == chapter)) {
                    queue.add(c);
                }
            }
            java.util.Collections.shuffle(queue);
            if (queue.size() > 15) queue = queue.subList(0, 15);
        } else {
            List<Card> review = new ArrayList<>();
            List<Card> fresh = new ArrayList<>();
            for (Card c : cards) {
                if (!srs.isNew(c.id)) {
                    if (srs.isDue(c.id, today) && (chapter == 0 || c.chapter == chapter)) {
                        review.add(c);
                    }
                } else if (chapter > 0 && c.chapter == chapter) {
                    fresh.add(c);
                }
            }
            java.util.Collections.shuffle(review);
            fresh.sort(Comparator.comparingInt(c -> c.ord));
            if (chapter > 0 && fresh.size() > NEW_PER_SESSION) {
                fresh = fresh.subList(0, NEW_PER_SESSION);
            }
            queue = new ArrayList<>(review);
            queue.addAll(fresh);
        }
        return buildSessionJson(queue, wrongMode);
    }

    private String buildSessionJson(List<Card> queue, boolean wrongMode) {
        StringBuilder sb = new StringBuilder("{\"cards\":[");
        for (int i = 0; i < queue.size(); i++) {
            Card c = queue.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(c.id)
              .append(",\"ch\":").append(c.chapter)
              .append(",\"topic\":\"").append(c.topic).append("\"")
              .append(",\"tag\":\"").append(c.tag).append("\"")
              .append(",\"q\":\"").append(c.question).append("\"");
            if (c.isFill()) {
                sb.append(",\"fill\":true,\"ans\":\"").append(c.answerText).append("\"");
            } else {
                sb.append(",\"fill\":false,\"opts\":[");
                for (int j = 0; j < c.options.size(); j++) {
                    if (j > 0) sb.append(',');
                    sb.append('"').append(c.options.get(j)).append('"');
                }
                sb.append("],\"ans\":").append(c.answerIndex);
            }
            sb.append(",\"review\":").append(!srs.isNew(c.id))
              .append(",\"wrong\":").append(wrongMode)
              .append(",\"e\":\"").append(c.explain).append("\"");
            if (c.demo != null) sb.append(",\"d\":\"").append(c.demo).append("\"");
            sb.append('}');
        }
        return sb.append("],\"count\":").append(queue.size()).append('}').toString();
    }

    public void answer(int cardId, boolean correct) {
        srs.applyResult(cardId, correct, today);
        try { srs.save(); } catch (IOException e) { throw new RuntimeException(e); }
    }

    public void zhan(int cardId) {
        srs.suspend(cardId);
        try { srs.save(); } catch (IOException e) { throw new RuntimeException(e); }
    }

    /** 按给定 id 顺序重建一组卡片(用于恢复中断的学习会话) */
    public String cardsByIds(String idsCsv) {
        List<Card> qs = new ArrayList<>();
        for (String s : idsCsv.split(",")) {
            int id = Integer.parseInt(s.trim());
            for (Card c : cards) {
                if (c.id == id) { qs.add(c); break; }
            }
        }
        return buildSessionJson(qs, false);
    }

    /** 顶部统计 JSON */
    public String stats() {
        int total = cards.size(), learned = 0, dueNow = 0;
        for (Card c : cards) {
            if (!srs.isNew(c.id)) {
                learned++;
                if (srs.isDue(c.id, today)) dueNow++;
            }
        }
        return "{\"total\":" + total
                + ",\"learned\":" + learned
                + ",\"zhan\":" + srs.suspendedCount()
                + ",\"wrong\":" + srs.wrongBookCount()
                + ",\"dueNow\":" + dueNow
                + ",\"dueTmr\":" + srs.dueTomorrowCount(today) + "}";
    }

    /** EngineTest 用: 在临时目录里复制一份卡片库做测试, 不动真实进度 */
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("shan-test");
        Files.copy(Path.of("cards-java.jsonl"), tmp.resolve("cards.jsonl"));
        Engine e = new Engine(tmp.resolve("cards.jsonl"), tmp.resolve("progress.jsonl"), JAVA_CHAPTERS);

        String ch = e.chapters();
        int chapterCount = 0;
        for (String seg : ch.split("\\},\\{")) {
            if (seg.contains("\"name\"")) chapterCount++;
        }
        System.out.println("[测试] 章节数(应=9): " + chapterCount + " | 章节JSON里不应再有 unlocked 字段: " + !ch.contains("unlocked"));

        String s1 = e.session(1);
        int cnt = Integer.parseInt(s1.substring(s1.lastIndexOf("\"count\":") + 8, s1.length() - 1));
        System.out.println("[测试] 第1章首轮卡片数(应=10): " + cnt);
        int i1 = s1.indexOf("JDK 的全称和含义是?"), i2 = s1.indexOf("JDK 与 JRE 的关系是?");
        System.out.println("[测试] 新卡按学习顺序出(应=true): " + (i1 >= 0 && i2 >= 0 && i1 < i2));

        for (int id = 1; id <= 20; id++) e.answer(id, true);  // 第1章刷 20 张

        /* 解锁门槛已取消: 完全没学上一章也应能直接开第 2 章 */
        Path tmpFree = Files.createTempDirectory("shan-free");
        Files.copy(Path.of("cards-java.jsonl"), tmpFree.resolve("cards.jsonl"));
        Engine eFree = new Engine(tmpFree.resolve("cards.jsonl"), tmpFree.resolve("progress.jsonl"), JAVA_CHAPTERS);
        String sf = eFree.session(2);
        int fcnt = Integer.parseInt(sf.substring(sf.lastIndexOf("\"count\":") + 8, sf.length() - 1));
        System.out.println("[测试] 未学上一章直接开第2章(应=10): " + fcnt);

        e.answer(21, false);
        e.zhan(30);
        boolean zhaned = e.session(1).indexOf("\"id\":30,") < 0;
        System.out.println("[测试] 被斩卡不再出现(应=true): " + zhaned);

        String w1 = e.session(-1);
        System.out.println("[测试] 答错卡进入错题本(应=true): " + (w1.contains("\"id\":21,") && w1.contains("\"wrong\":true")));
        System.out.println("[测试] 统计含错题数1(应=true): " + e.stats().contains("\"wrong\":1"));
        e.answer(21, true);
        System.out.println("[测试] 重做答对移出错题本(应=true): " + (e.session(-1).contains("\"count\":0")));
        System.out.println("[测试] Java统计: " + e.stats());

        /* 算法课程加载自检 */
        if (Files.exists(Path.of("cards-algo.jsonl"))) {
            Path tmp2 = Files.createTempDirectory("shan-algo");
            Files.copy(Path.of("cards-algo.jsonl"), tmp2.resolve("cards.jsonl"));
            Files.copy(Path.of("demos-algo.jsonl"), tmp2.resolve("demos.jsonl"));
            Engine a = new Engine(tmp2.resolve("cards.jsonl"), tmp2.resolve("progress.jsonl"), ALGO_CHAPTERS);
            String ach = a.chapters();
            int ac = 0;
            for (String seg : ach.split("\\},\\{")) if (seg.contains("\"name\"")) ac++;
            System.out.println("[测试] 算法课程章节数(应=6): " + ac + " | 卡片数: "
                    + a.stats());
        }
        /* 计组课程加载自检 */
        if (Files.exists(Path.of("cards-co.jsonl"))) {
            Path tmp3 = Files.createTempDirectory("shan-co");
            Files.copy(Path.of("cards-co.jsonl"), tmp3.resolve("cards.jsonl"));
            Files.copy(Path.of("demos-co.jsonl"), tmp3.resolve("demos.jsonl"));
            Engine c = new Engine(tmp3.resolve("cards.jsonl"), tmp3.resolve("progress.jsonl"), CO_CHAPTERS);
            int cc = 0;
            for (String seg : c.chapters().split("\\},\\{")) if (seg.contains("\"name\"")) cc++;
            System.out.println("[测试] 计组课程章节数(应=6): " + cc + " | 卡片数: "
                    + c.stats());
        }
        /* 操作系统课程加载自检 */
        if (Files.exists(Path.of("cards-os.jsonl"))) {
            Path tmp4 = Files.createTempDirectory("shan-os");
            Files.copy(Path.of("cards-os.jsonl"), tmp4.resolve("cards.jsonl"));
            Files.copy(Path.of("demos-os.jsonl"), tmp4.resolve("demos.jsonl"));
            Engine o = new Engine(tmp4.resolve("cards.jsonl"), tmp4.resolve("progress.jsonl"), OS_CHAPTERS);
            int oc = 0;
            for (String seg : o.chapters().split("\\},\\{")) if (seg.contains("\"name\"")) oc++;
            System.out.println("[测试] 操作系统课程章节数(应=8): " + oc + " | 卡片数: "
                    + o.stats());
            StringBuilder osPer = new StringBuilder();
            java.util.regex.Matcher mm = java.util.regex.Pattern
                    .compile("\"id\":(\\d+),[^\\[]*?\"total\":(\\d+)").matcher(o.chapters());
            while (mm.find()) osPer.append(mm.group(1)).append('=').append(mm.group(2)).append(' ');
            System.out.println("[测试] OS各章卡数(不应有0): " + osPer);
        }
                if (Files.exists(Path.of("cards-en.jsonl"))) {
            Path tmp5 = Files.createTempDirectory("shan-en");
            Files.copy(Path.of("cards-en.jsonl"), tmp5.resolve("cards.jsonl"));
            Engine n = new Engine(tmp5.resolve("cards.jsonl"), tmp5.resolve("progress.jsonl"), EN_CHAPTERS);
            System.out.println("[测试] 四级课程加载: " + n.stats());
        }
        System.out.println("=== 引擎逻辑自测完成 ===");
    }
}
