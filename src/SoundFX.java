import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * 程序合成音效(无需任何音频文件, 离线可用):
 *   ok  答对 —— 两声清脆上行"叮咚"
 *   bad 答错 —— 低沉短促"嗡"
 *   win 完成 —— 上行琶音小号角
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
            case "ok":  data = tone(new double[][]{{659, .10}, {988, .16}}, .62); break;
            case "bad": data = tone(new double[][]{{196, .15}, {147, .22}}, .58); break;
            case "win": data = tone(new double[][]{{523, .11}, {659, .11}, {784, .11}, {1047, .28}}, .62); break;
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

    /** 合成音符序列: 正弦波 + 指数衰减包络, 16bit 单声道 PCM */
    private static byte[] tone(double[][] notes, double volume) {
        int frames = 0;
        for (double[] n : notes) frames += (int) (n[1] * RATE);
        byte[] out = new byte[frames * 2];
        int pos = 0;
        for (double[] n : notes) {
            int len = (int) (n[1] * RATE);
            for (int i = 0; i < len; i++) {
                double env = Math.exp(-2.0 * i / len);
                int sample = (int) (Math.sin(2 * Math.PI * n[0] * i / RATE) * volume * env * 32767);
                out[pos++] = (byte) (sample & 0xff);
                out[pos++] = (byte) ((sample >> 8) & 0xff);
            }
        }
        return out;
    }
}
