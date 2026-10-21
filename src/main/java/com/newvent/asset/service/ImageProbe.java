package com.newvent.asset.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.newvent.registry.Asset.Tone;

/**
 * 올라온 바이트가 **정말 이미지인가**, 그리고 **그 위의 글자를 어떻게 해야 읽히는가**.
 *
 * ★ 확장자와 Content-Type 은 거짓말한다. 실제로 디코딩해 보는 것만이 확실하다.
 *   형식 이름도 여기서 나온 것을 쓴다 — 브라우저가 보낸 값이 아니다.
 *
 * ★★ 왜 올릴 때 재나 — 그릴 때는 못 잰다.
 *   CSS 는 사진이 무슨 색인지 모르고, 모델은 그림을 아예 못 본다.
 *   사진 위의 글자색을 정할 수 있는 곳은 **바이트가 손에 있는 이 순간뿐**이다.
 *
 * ★★ 셋 중 하나로 답한다.
 *     LIGHT  밝은 그림  → 어두운 글씨. 덮개 없음
 *     DARK   어두운 사진 → 흰 글씨.   덮개 없음
 *     MIXED  섞인 사진  → 흰 글씨 + 덮개
 *   앞의 둘이 사진을 안 건드린다. 전에는 **모든** 사진에 55% 를 씌워서
 *   밝은 일러스트가 갈색으로 뭉개졌다 — 사진을 보여 주려고 올렸는데 덮어 버린 셈이다.
 *   덮개는 이제 MIXED 에만 간다. 위는 하늘이고 아래는 숲인 사진은 어느 글자색도
 *   양쪽을 못 만족시키므로, 그때는 덮는 수밖에 없다.
 */
public final class ImageProbe {

    private ImageProbe() {}

    /** MIXED 일 때 쓸 덮개 농도. CSS 의 ev-asset-dim-* 와 짝이다 */
    private static final double[] SCRIMS = { 0.35, 0.55, 0.68 };

    /**
     * 풀었을 때의 화소 수 상한 (2500만 = 5000×5000).
     *
     * ★★ 파일 크기(3MB)만으로는 못 막는다 — **압축 폭탄**이다.
     *   한 가지 색으로 채운 PNG 는 30000×30000(9억 화소)이 수백 KB 로 압축된다.
     *   풀면 화소당 4바이트라 3.6GB 다. 업로드 상한을 통과한 작은 파일 하나가
     *   힙을 통째로 먹고 서버를 멈춘다.
     *
     * ★ 그래서 **읽기 전에** 크기를 본다. getWidth · getHeight 는 헤더만 읽어서
     *   화소를 풀지 않는다 — 폭탄이어도 여기서 되돌아간다.
     *
     * ★ 2500만인 이유 — 이벤트 페이지에 쓰는 가장 큰 그림(페이지 전체 배경)도
     *   2000×1400 = 280만이다. 10배 가까이 여유를 두고도 최악 100MB 에서 끊긴다.
     *   올릴 수 있는 사람이 관리자뿐이라 더 조일 이유는 없다.
     */
    private static final long MAX_PIXELS = 25_000_000L;

    /** WCAG AA 본문 */
    private static final double TARGET = 4.5;

    /**
     * ★ 한두 점에 끌려가지 않도록 양 끝을 5% 씩 잘라 본다.
     *   흰 글씨의 최악은 **가장 밝은 영역**, 어두운 글씨의 최악은 **가장 어두운 영역**이다.
     */
    private static final double BRIGHT_PERCENTILE = 0.95;
    private static final double DARK_PERCENTILE = 0.05;

    /**
     * 어두운 글씨의 상대 휘도. #0f172a — 테마 넷이 모두 쓰는 제목색이다(실측).
     * 상수로 박아 둔 이유: 팔레트가 글자색을 바꾸면 이 판정이 틀어진다.
     * 지금 팔레트 18종은 --ev-text-main 을 건드리지 않는다(theme-sale 만 건드리고,
     * 그건 템플릿이라 에셋 대상이 아니다).
     */
    private static final double DARK_TEXT_LUMINANCE = 0.0088;

    public record Probed(String format, int width, int height, double scrim, Tone tone) {}

    /**
     * @return 디코딩되지 않으면 빈 값 — 거절해야 한다
     */
    public static Optional<Probed> probe(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) return Optional.empty();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return Optional.empty();

            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                String format = reader.getFormatName().toLowerCase();

                // ★★ **푸는 것보다 먼저** 크기를 본다 (MAX_PIXELS 주석 참고).
                //   read(0) 을 부르는 순간 화소가 메모리에 풀리므로, 그 뒤에 재면 늦다.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) return Optional.empty();

                // ★ GIF 는 첫 장만 본다. 밝기를 재는 용도라 그걸로 충분하고,
                //   장마다 디코딩하면 큰 GIF 에서 박스가 흔들린다 (1.9GB 다).
                BufferedImage img = reader.read(0);
                if (img == null) return Optional.empty();

                double[] ends = ends(img);
                // null = 거의 다 비치는 장식. 덮지 않고 글자색도 테마에 맡긴다
                Tone tone = ends == null ? Tone.LIGHT : toneOf(ends[0], ends[1]);
                double scrim = tone == Tone.MIXED ? scrimFor(ends[1]) : 0;
                return Optional.of(new Probed(format, img.getWidth(), img.getHeight(), scrim, tone));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // 깨진 파일 · 지원하지 않는 형식 — 둘 다 "이미지가 아니다" 로 같이 묶는다
            return Optional.empty();
        }
    }

    /**
     * ★ 둘이 동시에 통과할 수는 없다. 어두운 글씨가 통과하려면 가장 어두운 곳이
     *   0.215 보다 밝아야 하고, 흰 글씨가 통과하려면 가장 밝은 곳이 0.183 보다
     *   어두워야 한다. 가장 어두운 곳 ≤ 가장 밝은 곳이므로 겹치지 않는다.
     *   그래서 순서가 결과를 바꾸지 않는다 — 우선순위를 고민할 필요가 없다.
     *
     * @param darkEnd  가장 어두운 영역의 휘도 (어두운 글씨의 최악)
     * @param brightEnd 가장 밝은 영역의 휘도 (흰 글씨의 최악)
     */
    static Tone toneOf(double darkEnd, double brightEnd) {
        if ((darkEnd + 0.05) / (DARK_TEXT_LUMINANCE + 0.05) >= TARGET) return Tone.LIGHT;
        if (1.05 / (brightEnd + 0.05) >= TARGET) return Tone.DARK;
        return Tone.MIXED;
    }

    /** 흰 글씨가 4.5 를 넘기는 가장 옅은 덮개. 다 모자라면 제일 진한 것 */
    static double scrimFor(double brightEnd) {
        for (double a : SCRIMS) {
            if (contrastOnScrim(brightEnd, a) >= TARGET) return a;
        }
        return SCRIMS[SCRIMS.length - 1];
    }

    /**
     * ★ 덮개는 검정을 알파로 얹는 것이라, 합성 결과는 **채널마다** c*(1-a) 다.
     *   휘도는 비선형(감마)이라 "휘도 × (1-a)" 로 줄여 잡으면 틀린다.
     *   그래서 채널 쪽에서 줄이고 휘도를 다시 구한다.
     */
    private static double contrastOnScrim(double luminance, double alpha) {
        double srgb = toSrgb(luminance) * (1 - alpha);
        return 1.05 / (linear(srgb) + 0.05);
    }

    /**
     * 샘플의 양 끝 휘도 → { 하위 5%, 상위 95% }.
     *
     * ★ 채널 최대값이 아니라 **상대 휘도**로 잰다. 전에는 max(r,g,b) 로 잡았는데,
     *   그건 흰 글씨 쪽만 보수적으로 보려던 어림이었다. 이제 양쪽을 다 보므로
     *   어느 한쪽으로 치우치지 않는 진짜 휘도가 필요하다.
     */
    /** 이 알파 아래는 "비치는 자리" 로 본다. 절반이 기준이다 */
    private static final int OPAQUE_ALPHA = 128;

    /**
     * 비치는 자리가 이보다 많으면 **오려 낸 장식**으로 본다.
     *
     * ★★ 사진은 투명한 자리가 없다(0%). 투명 배경 PNG 는 바탕을 비우려고 만든 것이고,
     *   그건 "뒤에 깔 그림" 이 아니라 "위에 얹을 그림" 이라는 뜻이다.
     *   그런 그림에 화면 전체 덮개를 씌우면, 비치라고 만든 자리까지 같이 어두워진다.
     *   실측 — 수료생 일러스트는 불투명한 점만 보면 머리카락(검정)과 수료증(흰색)이
     *   같이 있어 MIXED 가 맞게 나오지만, 그 판정으로 덮개를 깔면 페이지가 통째로 까매진다.
     */
    private static final double DECOR_TRANSPARENT_RATIO = 0.25;

    private static double[] ends(BufferedImage img) {
        int w = Math.max(1, Math.min(img.getWidth(), 64));
        int h = Math.max(1, Math.min(img.getHeight(), 64));
        double[] vals = new double[w * h];
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = img.getRGB(x * img.getWidth() / w, y * img.getHeight() / h);
                // ★★ 알파를 건너뛰면 **투명한 자리가 검정으로 세어진다.**
                //   getRGB 는 완전 투명 픽셀에 0x00000000 을 준다 — r=g=b=0, 휘도 0 이다.
                //   그래서 배경이 거의 다 비치는 장식 PNG 가 "가장 어두운 곳이 칠흑" 으로
                //   측정돼 MIXED 로 떨어지고, 화면 전체에 55% 검은 덮개가 깔렸다(제보:
                //   "2번 배경으로 깔아달라고 하니까 전체적으로 까매졌어").
                //   비치는 자리는 사진이 아니다. 그 뒤에 있는 건 페이지 바탕이다 — 세지 않는다.
                if (((px >>> 24) & 0xff) < OPAQUE_ALPHA) continue;
                vals[n++] = luminance((px >> 16) & 0xff, (px >> 8) & 0xff, px & 0xff);
            }
        }
        // ★ 비치는 자리가 많으면 장식이다 — 덮개도 흰 글씨도 강요하지 않는다
        if (n <= w * h * (1 - DECOR_TRANSPARENT_RATIO)) return null;
        Arrays.sort(vals, 0, n);
        return new double[] {
            vals[Math.min(n - 1, (int) (n * DARK_PERCENTILE))],
            vals[Math.min(n - 1, (int) (n * BRIGHT_PERCENTILE))],
        };
    }

    private static double luminance(int r, int g, int b) {
        return 0.2126 * linear(r / 255.0) + 0.7152 * linear(g / 255.0) + 0.0722 * linear(b / 255.0);
    }

    private static double linear(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** linear 의 역함수 — 덮개를 채널 쪽에서 곱하려면 sRGB 로 돌아가야 한다 */
    private static double toSrgb(double lin) {
        return lin <= 0.0031308 ? lin * 12.92 : 1.055 * Math.pow(lin, 1 / 2.4) - 0.055;
    }
}
