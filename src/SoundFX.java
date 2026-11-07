import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * 程序合成音效(无需任何音频文件, 离线可用):
 *   ok  答对 —— 两声上行"叮——咚", 尾音悠长
 *   bad 答错 —— 低沉"嗡——", 缓缓落底
 *   win 完成 —— 上行琶音小号角, 末音绵长
 * 音色: 基频+2/3次谐波(饱满) + 慢衰减包络(延长) + 8ms起音(无咔哒声)。
 * 用 javax.sound.sampled 在守护线程里播放, 不阻塞答题。
 */
public class SoundFX {

    private static final float RATE = 44100f;
    private volatile boolean enabled = true;

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    public void play(String type) {
        if (!enabled) return;
        final byte[] data;
        switch (type) {
            case "ok":  data = tone(new double[][]{{659, .13}, {988, .38}}, .52); break;
            case "bad": data = tone(new double[][]{{196, .20}, {147, .45}}, .50); break;
            case "win": data = tone(new double[][]{{523, .14}, {659, .14}, {784, .14}, {1047, .55}}, .52); break;
            default: return;
        }
        Thread t = new Thread(() -> {
            try (SourceDataLine line = AudioSystem.getSourceDataLine(
                    new AudioFormat(RATE, 16, 1, true, false))) {
                line.open(line.getFormat(), 8192);
                line.start();
                line.write(data, 0, data.length);
                line.drain();
            } catch (Exception ignored) {
                // 没有音频设备/被占用时静默跳过, 不影响答题
            }
        }, "sfx");
        t.setDaemon(true);
        t.start();
    }

    /** 合成音符序列: 基频+轻微2次谐波(柔和) + 慢指数衰减包络 + 12ms起音/20ms收音渐变, 16bit 单声道 PCM
     *  起音和收音都渐变到零: 波形不断裂, 消除音符边界的"啪"爆音(耳机上尤其明显) */
    private static byte[] tone(double[][] notes, double volume) {
        int frames = 0;
        for (double[] n : notes) frames += (int) (n[1] * RATE);
        byte[] out = new byte[frames * 2];
        int pos = 0;
        for (double[] n : notes) {
            int len = (int) (n[1] * RATE);
            int attack = Math.min(len, (int) (0.012 * RATE));
            int release = Math.min(len, (int) (0.020 * RATE));
            for (int i = 0; i < len; i++) {
                double env = Math.exp(-1.3 * i / len);          // 衰减放缓: 尾音仍在30%以上, 听感更延长
                if (i < attack) env *= (double) i / attack;     // 起音坡: 消掉开头爆音
                int tail = len - i;
                if (tail < release) env *= (double) tail / release;   // 收音坡: 淡出到零, 消掉结尾爆音(关键修复)
                double t = 2 * Math.PI * n[0] * i / RATE;
                double wave = Math.sin(t) + 0.18 * Math.sin(2 * t);   // 只留轻柔的八度泛音
                int sample = (int) (wave * volume * env * 32767 / 1.18);
                out[pos++] = (byte) (sample & 0xff);
                out[pos++] = (byte) ((sample >> 8) & 0xff);
            }
        }
        return out;
    }

    /** 自测: 打印三种音效的时长(秒), 并各播一遍 */
    public static void main(String[] args) throws Exception {
        java.util.Map<String, double[][]> sounds = new java.util.LinkedHashMap<>();
        sounds.put("ok", new double[][]{{659, .13}, {988, .38}});
        sounds.put("bad", new double[][]{{196, .20}, {147, .45}});
        sounds.put("win", new double[][]{{523, .14}, {659, .14}, {784, .14}, {1047, .55}});
        SoundFX sfx = new SoundFX();
        for (java.util.Map.Entry<String, double[][]> e : sounds.entrySet()) {
            double sec = 0;
            for (double[] n : e.getValue()) sec += n[1];
            System.out.println(e.getKey() + " = " + String.format("%.2f", sec) + "s");
            sfx.play(e.getKey());
            Thread.sleep(1200);
        }
    }
}
