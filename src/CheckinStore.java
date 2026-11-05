import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 打卡记录器(全局, 不分课程):
 *   规则 —— 一天内在任意课程答满 GOAL 张卡(答对答错都算, 看答案也算), 当天即「打卡成功」。
 *   连续打卡天数 = 从今天(未打满则从昨天)往前数连续的打卡日; 断一天则连续归零重计, 最长连击永久保留。
 * 存储: ~/.javashan/checkin.jsonl, 每行一天 {"d":"2026-11-05","n":12}
 *   d=日期, n=当日作答张数。只保留最近 KEEP_DAYS 天, 文件不会无限增长。
 * 说明: 手机与电脑共用同一份文件, 打卡进度天然同步; 读写全部 synchronized(手机服务是多线程)。
 */
public class CheckinStore {

    /** 一天答满这么多张卡即视为打卡成功 */
    public static final int GOAL = 10;
    /** 历史记录保留天数(约一年半, 足够看连续/最长连击) */
    static final int KEEP_DAYS = 500;

    private final Path file;
    private final Map<String, Integer> counts = new HashMap<>();   // "yyyy-MM-dd" -> 当日作答张数

    private CheckinStore(Path file) {
        this.file = file;
    }

    static CheckinStore load(Path file) throws IOException {
        CheckinStore store = new CheckinStore(file);
        if (!Files.exists(file)) return store;
        Pattern kv = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Map<String, String> raw = new HashMap<>();
            Matcher m = kv.matcher(line);
            while (m.find()) {
                String value = m.group(2);
                raw.put(m.group(1), value.startsWith("\"") ? m.group(3) : value);
            }
            String d = raw.get("d");
            if (d == null || !raw.containsKey("n")) continue;      // 坏行直接跳过, 不让打卡功能整体失效
            try {
                store.counts.put(LocalDate.parse(d).toString(), Integer.parseInt(raw.get("n")));
            } catch (RuntimeException ignored) {
            }
        }
        return store;
    }

    /** 写盘失败不能打断答题, 因此静默忽略(进度文件本身另有 SrsStore 兜底) */
    private void save() {
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                lines.add("{\"d\":\"" + e.getKey() + "\",\"n\":" + e.getValue() + "}");
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** 每答一张卡调用一次 */
    synchronized void tick() {
        addDay(LocalDate.now().toString(), 1);
    }

    /** 给某天加 n 张(自测与未来可能的补录都走这里) */
    synchronized void addDay(String day, int n) {
        counts.merge(day, n, Integer::sum);
        trim();
        save();
    }

    /** 只保留最近 KEEP_DAYS 天 */
    private void trim() {
        String limit = LocalDate.now().minusDays(KEEP_DAYS).toString();
        counts.keySet().removeIf(d -> d.compareTo(limit) < 0);
    }

    /** 某天是否打卡成功(答满了) */
    private boolean done(String day) {
        return counts.getOrDefault(day, 0) >= GOAL;
    }

    /** 当前连续打卡天数: 今天打满从今天往前数, 否则从昨天往前数(今天还来得及就不算断) */
    private int currentStreak() {
        LocalDate d = LocalDate.now();
        if (!done(d.toString())) d = d.minusDays(1);
        int n = 0;
        while (done(d.toString())) { n++; d = d.minusDays(1); }
        return n;
    }

    /** 历史最长连击(在保留窗口内) */
    private int bestStreak() {
        List<String> days = new ArrayList<>();
        for (String d : counts.keySet()) if (done(d)) days.add(d);
        java.util.Collections.sort(days);
        int best = 0, run = 0;
        LocalDate prev = null;
        for (String s : days) {
            LocalDate cur = LocalDate.parse(s);
            run = (prev != null && prev.equals(cur.minusDays(1))) ? run + 1 : 1;
            if (run > best) best = run;
            prev = cur;
        }
        return best;
    }

    /** 今日打卡概况 JSON */
    synchronized String statusJson() {
        LocalDate today = LocalDate.now();
        String key = today.toString();
        int n = counts.getOrDefault(key, 0);
        boolean checked = n >= GOAL;
        int monthDays = 0;
        YearMonth ym = YearMonth.from(today);
        for (String d : counts.keySet()) {
            if (d.length() == 10 && d.startsWith(ym.toString()) && done(d)) monthDays++;
        }
        return "{\"today\":" + n
                + ",\"goal\":" + GOAL
                + ",\"need\":" + Math.max(0, GOAL - n)
                + ",\"checkedToday\":" + checked
                + ",\"streak\":" + currentStreak()
                + ",\"best\":" + Math.max(bestStreak(), currentStreak())
                + ",\"total\":" + doneCount()
                + ",\"thisMonth\":" + monthDays + "}";
    }

    private int doneCount() {
        int c = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() >= GOAL) c++;
        return c;
    }

    /**
     * 某月打卡日历 JSON: ym 形如 "2026-11"(非法或未来月份自动回到当前月)。
     * checked=该月打卡成功的「日」列表, today=今天在该月的第几天(不在该月则为 0),
     * lead=1 号前面的空格数(周一为每周第一格), days=该月天数。
     */
    synchronized String monthJson(String ymRaw) {
        YearMonth now = YearMonth.now();
        YearMonth ym = now;
        if (ymRaw != null && !ymRaw.isEmpty()) {
            try {
                YearMonth p = YearMonth.parse(ymRaw);
                if (!p.isAfter(now)) ym = p;                       // 不给看未来月份
            } catch (RuntimeException ignored) {
            }
        }
        int days = ym.lengthOfMonth();
        int lead = (ym.atDay(1).getDayOfWeek().getValue() + 6) % 7; // 周一开头
        StringBuilder checked = new StringBuilder("[");
        int hit = 0;
        for (int d = 1; d <= days; d++) {
            String key = ym.atDay(d).toString();
            if (!done(key)) continue;
            if (hit++ > 0) checked.append(',');
            checked.append(d);
        }
        checked.append(']');
        int today = ym.equals(now) ? LocalDate.now().getDayOfMonth() : 0;
        boolean nowMonth = ym.equals(now);
        return "{\"ym\":\"" + ym + "\",\"days\":" + days
                + ",\"lead\":" + lead
                + ",\"checked\":" + checked
                + ",\"today\":" + today
                + ",\"isNow\":" + nowMonth + ",\"count\":" + hit + "}";
    }

    /** 打卡逻辑自测: java -cp out CheckinStore (只动临时目录, 不碰真实记录) */
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("shan-checkin");
        CheckinStore c = CheckinStore.load(tmp.resolve("checkin.jsonl"));
        String today = LocalDate.now().toString();

        System.out.println("[测试] 空记录统计(应 today=0/streak=0): " + c.statusJson());

        c.addDay(today, GOAL - 1);
        boolean almost = c.statusJson().contains("\"checkedToday\":false") && c.statusJson().contains("\"need\":1");
        System.out.println("[测试] 答满前不算打卡(应=true): " + almost);

        c.tick();                                     // 第 GOAL 张: 今天打卡成功
        String s1 = c.statusJson();
        System.out.println("[测试] 答满即打卡(应 checkedToday=true streak=1): " + s1);

        c.addDay(LocalDate.now().minusDays(1).toString(), GOAL);
        c.addDay(LocalDate.now().minusDays(2).toString(), GOAL);
        String s2 = c.statusJson();
        boolean streak3 = s2.contains("\"streak\":3") && s2.contains("\"best\":3") && s2.contains("\"total\":3");
        System.out.println("[测试] 连续3天累计(应=true): " + streak3 + " | " + s2);

        /* 断一天: 清空昨天, 今天与前天不再连续 —— streak 与 best 都只剩 1 */
        c.counts.remove(LocalDate.now().minusDays(1).toString());
        String s3 = c.statusJson();
        boolean broke = s3.contains("\"streak\":1") && s3.contains("\"best\":1") && s3.contains("\"total\":2");
        System.out.println("[测试] 昨天断签→连续与最长都归到1(应=true): " + broke + " | " + s3);

        /* 今天没打满时, 昨天打满了也不算断(还来得及) */
        CheckinStore d = CheckinStore.load(tmp.resolve("none.jsonl"));
        d.addDay(LocalDate.now().minusDays(1).toString(), GOAL);
        String s4 = d.statusJson();
        System.out.println("[测试] 今日未打满但昨日已打→连续仍算(应 streak=1): " + s4);

        /* 坏行容错 */
        Path bad = tmp.resolve("bad.jsonl");
        Files.write(bad, java.util.List.of("{oops}", "{\"d\":\"2026-01-01\",\"n\":12}", "   "), StandardCharsets.UTF_8);
        String s5 = CheckinStore.load(bad).statusJson();
        System.out.println("[测试] 坏行不影响读取(应 total=1): " + s5);

        String m = c.monthJson(today.substring(0, 7));
        System.out.println("[测试] 本月日历: " + m);
        System.out.println("[测试] 非法月份自动回本月(应=true): "
                + c.monthJson("abc").contains("\"ym\":\"" + YearMonth.now() + "\""));
        System.out.println("=== 打卡逻辑自测完成 ===");
    }
}
