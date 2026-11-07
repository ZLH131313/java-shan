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
 * 卡片备注(个人数据): 每张卡一条, 存 ~/.javashan/notes-{course}.jsonl,
 * 每行 {"id":12,"t":"2026-11-07","n":"备注文本"}。
 *
 * 文本按项目「简单解析器」约定消毒: 英文双引号→单引号, 反斜杠删除, 换行→¶, ;;→;
 * 读回时 ¶ 还原成换行, 界面用 pre-wrap 显示。
 *
 * 不缓存内存副本, 每次读写都直接过一遍磁盘(文件只有几 KB):
 * 桌面上可能同时开着多个窗口共用一份文件, 这样写入前磁盘上的其它卡片不会被旧副本盖掉。
 * 方法全部 synchronized, 同一窗口内的并发调用也安全。
 */
public class NotesStore {

    private static final int MAX_LEN = 400;
    private static final Pattern KV = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+)");

    static class Note {
        final String text;
        final String day;
        Note(String text, String day) { this.text = text; this.day = day; }
    }

    private final Path file;

    NotesStore(Path file) {
        this.file = file;
    }

    private static Map<Integer, Note> readAll(Path file) {
        Map<Integer, Note> map = new LinkedHashMap<>();
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
                if (!raw.containsKey("id") || !raw.containsKey("n")) continue;
                int id;
                try {
                    id = Integer.parseInt(raw.get("id"));
                } catch (NumberFormatException ignored) {
                    continue;   /* 坏行直接跳过, 不污染其它备注 */
                }
                map.put(id, new Note(raw.get("n").replace("¶", "\n"), raw.getOrDefault("t", "")));
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    /** 消毒: 保证写出的行仍符合「值里没有双引号和反斜杠」这个简单解析前提 */
    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\\", "").replace("\"", "'")
                .replace("\r\n", "\n").replace("\t", " ").replace(";;", ";")
                .trim();
        s = s.replaceAll("\n{3,}", "\n\n");
        if (s.length() > MAX_LEN) s = s.substring(0, MAX_LEN);
        return s;
    }

    public synchronized String get(int cardId) {
        Note n = readAll(file).get(cardId);
        return n == null ? "" : n.text;
    }

    /** 空文本 = 删除这条备注 */
    public synchronized void set(int cardId, String text) {
        String v = clean(text);
        Map<Integer, Note> disk = readAll(file);
        if (v.isEmpty()) disk.remove(cardId);
        else disk.put(cardId, new Note(v, LocalDate.now().toString()));
        save(disk);
    }

    public synchronized int count() {
        return readAll(file).size();
    }

    /** [{"id":12,"n":"文本","t":"2026-11-07"}, ...] 供界面标出哪些卡有备注 */
    public synchronized String allJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Integer, Note> e : readAll(file).entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(e.getKey())
              .append(",\"n\":\"").append(e.getValue().text.replace("\n", "¶")).append("\"")
              .append(",\"t\":\"").append(e.getValue().day).append("\"}");
        }
        return sb.append(']').toString();
    }

    private void save(Map<Integer, Note> data) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, Note> e : data.entrySet()) {
            lines.add("{\"id\":" + e.getKey()
                    + ",\"t\":\"" + e.getValue().day + "\""
                    + ",\"n\":\"" + e.getValue().text.replace("\n", "¶") + "\"}");
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            /* 备注写盘失败不能打断答题 */
        }
    }

    /** 自测入口: java -Dfile.encoding=UTF-8 -cp out NotesStore */
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shan-notes");
        Path f = dir.resolve("notes.jsonl");
        NotesStore s = new NotesStore(f);
        System.out.println("[测试] 初始无备注(应=0): " + (s.count() == 0));
        s.set(7, "  这里记一下:\n分号与\"双引号\"和\\反斜杠都要被消毒  ");
        String v = s.get(7);
        System.out.println("[测试] 消毒后无双引号无反斜杠(应=true): " + (!v.contains("\"") && !v.contains("\\")));
        System.out.println("[测试] 换行保留(应=true): " + v.contains("\n"));
        System.out.println("[测试] 重新读取一致(应=true): " + new NotesStore(f).get(7).equals(v));
        s.set(9, "第二张");
        System.out.println("[测试] 两条计数(应=2): " + (s.count() == 2));
        s.set(9, "   ");
        System.out.println("[测试] 空文本即删除(应=1): " + (new NotesStore(f).count() == 1));
        s.set(11, "多窗口先写的");
        NotesStore b = new NotesStore(f);
        s.set(12, "我这窗口新加的");
        System.out.println("[测试] 另一窗口读得到且不丢(应=true): "
                + (b.get(12).equals("我这窗口新加的") && b.count() == 3) + " | 实际条数=" + b.count());
        System.out.println("[测试] allJson 可解析(应=true): " + s.allJson().startsWith("[{\"id\":"));
        System.out.println("[测试] 坏行容错(应=true): " + toleratesBadLines(dir));
        System.out.println("=== 备注逻辑自测完成 ===");
    }

    private static boolean toleratesBadLines(Path dir) throws IOException {
        Path f = dir.resolve("bad.jsonl");
        Files.write(f, List.of("not json at all", "{\"id\":\"abc\",\"n\":\"坏id\"}",
                "{\"id\":3,\"t\":\"2026-11-07\",\"n\":\"好的一条\"}"), StandardCharsets.UTF_8);
        NotesStore s = new NotesStore(f);
        return s.count() == 1 && s.get(3).equals("好的一条");
    }
}
