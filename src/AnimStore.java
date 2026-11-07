import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动画合集(个人数据): 记下哪些卡片生成过演示动画, 存 ~/.javashan/anims-{course}.jsonl,
 * 每行 {"id":12,"d":"2026-11-07"}。
 *
 * 动画脚本不入库: 分步动画由界面里的本地规则引擎按卡片字段(题干/选项/答案/解析/演示)
 * 确定性重算, 所以改 cards-*.jsonl 后动画自动跟着更新, 也不会出现脚本与卡片不一致。
 * 合集只需保存「这张卡生成过」这个事实与日期。
 *
 * 与 NotesStore 同样不缓存内存副本, 每次读写直接过磁盘, 多窗口互不覆盖。
 */
public class AnimStore {

    private static final Pattern KV = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");

    private final Path file;

    AnimStore(Path file) {
        this.file = file;
    }

    private static Map<Integer, String> readAll(Path file) {
        Map<Integer, String> map = new LinkedHashMap<>();
        if (!Files.exists(file)) return map;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Map<String, String> raw = new LinkedHashMap<>();
                Matcher m = KV.matcher(line);
                while (m.find()) {
                    String value = m.group(2);
                    raw.put(m.group(1), value.startsWith("\"") ? m.group(3) : value);
                }
                if (!raw.containsKey("id")) continue;
                try {
                    map.put(Integer.parseInt(raw.get("id")), raw.getOrDefault("d", ""));
                } catch (NumberFormatException ignored) {
                    /* 坏行跳过 */
                }
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    public synchronized void add(int cardId) {
        Map<Integer, String> disk = readAll(file);
        disk.put(cardId, LocalDate.now().toString());
        save(disk);
    }

    public synchronized void remove(int cardId) {
        Map<Integer, String> disk = readAll(file);
        disk.remove(cardId);
        save(disk);
    }

    public synchronized boolean has(int cardId) {
        return readAll(file).containsKey(cardId);
    }

    public synchronized int count() {
        return readAll(file).size();
    }

    /** [{"id":12,"d":"2026-11-07"}, ...] 按日期新→旧, 同日按 id 小→大 */
    public synchronized String listJson() {
        List<Map.Entry<Integer, String>> list = new ArrayList<>(readAll(file).entrySet());
        list.sort((a, b) -> {
            int byDay = b.getValue().compareTo(a.getValue());
            return byDay != 0 ? byDay : Integer.compare(a.getKey(), b.getKey());
        });
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(list.get(i).getKey())
              .append(",\"d\":\"").append(list.get(i).getValue()).append("\"}");
        }
        return sb.append(']').toString();
    }

    private void save(Map<Integer, String> data) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, String> e : data.entrySet()) {
            lines.add("{\"id\":" + e.getKey() + ",\"d\":\"" + e.getValue() + "\"}");
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            /* 合集写盘失败不能打断刷题 */
        }
    }

    /** 自测入口: java -Dfile.encoding=UTF-8 -cp out AnimStore */
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shan-anims");
        Path f = dir.resolve("anims.jsonl");
        AnimStore a = new AnimStore(f);
        System.out.println("[测试] 初始为空(应=0): " + (a.count() == 0));
        a.add(12);
        a.add(7);
        System.out.println("[测试] 加入两张(应=2): " + (a.count() == 2));
        System.out.println("[测试] 查存在(应=true): " + (a.has(12) && !a.has(99)));
        a.add(12);
        System.out.println("[测试] 重复加入不翻倍(应=2): " + (new AnimStore(f).count() == 2));
        AnimStore b = new AnimStore(f);
        a.add(15);
        System.out.println("[测试] 另一窗口立刻读得到(应=3): " + (b.count() == 3));
        a.remove(7);
        System.out.println("[测试] 删除后(应=2): " + (new AnimStore(f).count() == 2));
        String j = a.listJson();
        System.out.println("[测试] 列表含 id 与日期(应=true): " + (j.contains("\"id\":") && j.contains("\"d\":\"")));
        System.out.println("[测试] 坏行容错(应=true): " + toleratesBadLines(dir));
        System.out.println("=== 动画合集自测完成 ===");
    }

    private static boolean toleratesBadLines(Path dir) throws IOException {
        Path f = dir.resolve("bad.jsonl");
        Files.write(f, List.of("not json at all", "{\"id\":\"abc\",\"d\":\"x\"}", "{\"id\":3,\"d\":\"2026-11-07\"}"),
                StandardCharsets.UTF_8);
        AnimStore s = new AnimStore(f);
        return s.count() == 1 && s.has(3);
    }
}
