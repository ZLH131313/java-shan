import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.List;

/**
 * 暴露给网页 JS 调用的桥接对象, 支持双课程(java / algo)。
 * JS 侧: JSON.parse(bridge.chapters('java')) 等, 课程参数: 'java' | 'algo'。
 *
 * 数据位置约定:
 *   卡片库 cards-{课程}.jsonl / 演示 demos-{课程}.jsonl —— 跟随程序, 属于"内容";
 *   个人数据(进度/会话/音效设置) 存用户主目录 .javashan\ —— 与程序分离。
 * 旧版单课程文件(progress.jsonl / session.txt)首次启动自动迁移为 java 课程数据。
 */
public class Bridge {

    private final Engine java;
    private final Engine algo;
    private final Engine co;
    private final Engine os;
    private final CheckinStore checkin;
    private final SoundFX sfx = new SoundFX();
    private final Path settingsFile;
    private final Path dataHome;

    public Bridge() throws IOException {
        dataHome = Path.of(System.getProperty("user.home"), ".javashan");
        Files.createDirectories(dataHome);

        // 旧版单课程数据迁移到 java 课程名下
        moveIfAbsent(dataHome.resolve("progress.jsonl"), dataHome.resolve("progress-java.jsonl"));
        moveIfAbsent(dataHome.resolve("session.txt"), dataHome.resolve("session-java.txt"));

        settingsFile = dataHome.resolve("settings.txt");
        if (Files.exists(settingsFile)) {
            try {
                if (Files.readString(settingsFile).trim().contains("sound=0")) sfx.setEnabled(false);
            } catch (IOException ignored) {
            }
        }

        java = new Engine(dataCards("cards-java"), dataHome.resolve("progress-java.jsonl"), Engine.JAVA_CHAPTERS);
        algo = new Engine(dataCards("cards-algo"), dataHome.resolve("progress-algo.jsonl"), Engine.ALGO_CHAPTERS);
        co = new Engine(dataCards("cards-co"), dataHome.resolve("progress-co.jsonl"), Engine.CO_CHAPTERS);
        os = new Engine(dataCards("cards-os"), dataHome.resolve("progress-os.jsonl"), Engine.OS_CHAPTERS);
        checkin = CheckinStore.load(dataHome.resolve("checkin.jsonl"));
    }

    private static void moveIfAbsent(Path from, Path to) throws IOException {
        if (Files.exists(from) && !Files.exists(to)) Files.move(from, to);
    }

    /** 定位课程数据文件(程序目录或上一级) */
    private static Path dataCards(String base) {
        for (String dir : new String[]{".", ".."}) {
            Path p = Path.of(dir, base + ".jsonl");
            if (Files.exists(p)) return p;
        }
        return Path.of(base + ".jsonl");
    }

    private Engine of(String course) {
        if ("algo".equals(course)) return algo;
        if ("co".equals(course)) return co;
        if ("os".equals(course)) return os;
        return java;
    }

    public String chapters(String course) { return of(course).chapters(); }
    public String session(String course, int chapter) { return of(course).session(chapter); }
    /** 温故模式: 不与其他方法同名重载 —— LiveConnect 对同名重载派发不稳定 */
    public String sessionWarm(String course, int chapter) { return of(course).session(chapter, true); }
    public String cardsByIds(String course, String idsCsv) { return of(course).cardsByIds(idsCsv); }
    public void answer(String course, int cardId, boolean correct) {
        of(course).answer(cardId, correct);
        checkin.tick();          // 打卡按"答过的卡"累计, 三门课共享同一份记录
    }
    public void zhan(String course, int cardId) { of(course).zhan(cardId); }
    public String stats(String course) { return of(course).stats(); }

    /* ---- 打卡(全局, 不分课程; 方法名唯一, 不做重载) ---- */
    public String checkinStatus() { return checkin.statusJson(); }
    public String checkinMonth(String ym) { return checkin.monthJson(ym); }

    /* ---- 会话续做(按课程隔离, 存用户主目录) ---- */
    public String loadSession(String course) {
        try {
            Path f = dataHome.resolve("session-" + course + ".txt");
            return Files.exists(f) ? Files.readString(f) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public void saveSession(String course, String json) {
        try {
            Files.writeString(dataHome.resolve("session-" + course + ".txt"), json == null ? "" : json);
        } catch (IOException ignored) {
        }
    }

    /* ---- 音效(全局, 不分课程) ---- */
    public void sfx(String type) { sfx.play(type); }
    public boolean soundOn() { return sfx.isEnabled(); }
    public void setSound(boolean on) {
        sfx.setEnabled(on);
        try {
            Files.write(settingsFile, List.of("sound=" + (on ? 1 : 0)), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }


    /* ---- 外部应用: 豆包 ---- */
    /** 唤起豆包并与本程序并排分屏: 执行 resources 里的 layout-doubao.ps1(找句柄/启动/左右对半/置前), 兜底网页版 */
    public void openDoubao() {
        try {
            Path ps = Path.of(System.getProperty("java.io.tmpdir"), "shan-doubao.ps1");
            try (var in = Bridge.class.getResourceAsStream("/layout-doubao.ps1")) {
                if (in != null) Files.copy(in, ps, StandardCopyOption.REPLACE_EXISTING);
            }
            new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps.toString()).start();
            return;
        } catch (Exception ignored) { }
        try { new ProcessBuilder("cmd", "/c", "start", "", "https://www.doubao.com").start(); } catch (Exception ignored) { }
    }
}
