package com.newvent.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.asset.service.ImageProbe;
import com.newvent.registry.Asset;

/**
 * 올라온 바이트가 정말 이미지인가, 그리고 얼마나 덮어야 글자가 읽히나.
 *
 * ★ 덮개 농도를 올릴 때 한 번 재는 이유는 ImageProbe 주석에 있다.
 *   한 줄로: 사진 위에서는 테마 · 팔레트 글자색을 쓸 수 없고, 얼마나 덮어야 하는지는 사진마다 다르다.
 */
class ImageProbeTest {

    private static byte[] solid(Color c, String format) throws IOException {
        BufferedImage img = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, 200, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("형식은 디코딩해서 알아낸다 - 확장자나 Content-Type 을 안 믿는다")
    void 형식을_디코딩해서_알아낸다() throws IOException {
        ImageProbe.Probed png = ImageProbe.probe(solid(Color.WHITE, "png")).orElseThrow();
        assertEquals("png", png.format());
        assertEquals(200, png.width());
        assertEquals(120, png.height());

        assertEquals("jpeg", ImageProbe.probe(solid(Color.WHITE, "jpg")).orElseThrow().format());
    }

    // ★ 확장자만 보고 받으면 svg 가 들어온다. svg 는 정화 정책이 따로 필요해서 아직 안 받는다.
    @Test
    @DisplayName("★ 이미지가 아니면 거절한다")
    void 이미지가_아니면_거절() {
        assertTrue(ImageProbe.probe("hello".getBytes()).isEmpty());
        assertTrue(ImageProbe.probe(new byte[0]).isEmpty());
        assertTrue(ImageProbe.probe("<svg onload=\"alert(1)\"></svg>".getBytes()).isEmpty());
    }

    // ★ 실측(브라우저 렌더, hero 안 가장 밝은 지점):
    //   흰 글씨 25% 2.06 · 35% 2.71 · 42% 3.37 · 50% 4.40 · 55% 5.18 ✓
    //   42% 를 감으로 잡았다가 모자랐다. 그래서 기계가 재게 했다.
    @Test
    @DisplayName("밝은 그림은 어두운 글씨, 어두운 사진은 흰 글씨")
    void 글자색은_사진이_정한다() throws IOException {
        assertEquals(Asset.Tone.LIGHT, tone(Color.WHITE));
        assertEquals(Asset.Tone.LIGHT, tone(new Color(250, 214, 228)));
        assertEquals(Asset.Tone.DARK, tone(new Color(24, 22, 30)));
        assertEquals(Asset.Tone.DARK, tone(new Color(22, 48, 30)));
    }

    // ★★ 덮개가 **셋 중 하나에만** 가는 게 이 판정의 요점이다.
    //   전에는 모든 사진에 55% 를 씌워서 밝은 일러스트가 갈색으로 뭉개졌다 —
    //   보여 주려고 올린 사진을 덮어 버린 셈이었다.
    @Test
    @DisplayName("덮개는 섞인 사진에만 — 밝은 그림 · 어두운 사진은 안 덮는다")
    void 덮개는_섞인_사진에만() throws IOException {
        assertEquals(0, probe(Color.WHITE).scrim(), 1e-9);
        assertEquals(0, probe(new Color(24, 22, 30)).scrim(), 1e-9);

        ImageProbe.Probed mixed = ImageProbe.probe(half(new Color(180, 215, 245), new Color(30, 60, 35)))
                .orElseThrow();
        assertEquals(Asset.Tone.MIXED, mixed.tone());
        assertTrue(mixed.scrim() > 0, "섞인 사진은 덮어야 합니다");
    }

    // ★ 경계는 계산으로 정해진다 — 어두운 글씨(#0f172a)가 4.5 를 넘는 가장 어두운 바탕.
    //   회색 단색으로 재면 120 과 130 사이다.
    @Test
    @DisplayName("판정 경계 - 회색 120 은 섞임, 130 부터 밝음")
    void 경계() throws IOException {
        assertEquals(Asset.Tone.MIXED, tone(new Color(120, 120, 120)));
        assertEquals(Asset.Tone.LIGHT, tone(new Color(130, 130, 130)));
    }

    // ★ 덮개를 쓰는 경우에는 그 값이 흰 글씨 4.5 를 만족해야 한다
    @Test
    @DisplayName("덮개는 흰 글씨가 4.5 를 넘기는 값이어야 한다")
    void 덮개는_대비를_만족한다() throws IOException {
        ImageProbe.Probed p = ImageProbe.probe(half(Color.WHITE, Color.BLACK)).orElseThrow();
        assertEquals(Asset.Tone.MIXED, p.tone());

        double lin = linear(1.0 * (1 - p.scrim()));
        assertTrue(1.05 / (lin + 0.05) >= 4.5, "흰 바탕 위에서 대비가 모자랍니다");
    }

    private static Asset.Tone tone(Color c) throws IOException {
        return probe(c).tone();
    }

    private static ImageProbe.Probed probe(Color c) throws IOException {
        return ImageProbe.probe(solid(c, "png")).orElseThrow();
    }

    /** 위아래로 반씩 다른 색 — 어느 글자색도 양 끝을 못 만족시키는 사진 */
    private static byte[] half(Color top, Color bottom) throws IOException {
        BufferedImage im = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) im.setRGB(x, y, (y < 20 ? top : bottom).getRGB());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(im, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("GIF 도 받는다 - 첫 장만 본다")
    void gif() throws IOException {
        Optional<ImageProbe.Probed> p = ImageProbe.probe(solid(Color.PINK, "gif"));

        assertFalse(p.isEmpty());
        assertEquals("gif", p.orElseThrow().format());
    }

    /**
     * ★★ 제보: "2번 이미지 배경으로 깔아달라고 하니까 전체적으로 까매졌어".
     *   getRGB 는 완전 투명 픽셀에 0x00000000 을 준다 — 알파를 안 보면 r=g=b=0,
     *   휘도 0, 즉 **칠흑**으로 세어진다. 그래서 배경이 비어 있는 일러스트가
     *   "가장 어두운 곳이 검정" 으로 측정돼 MIXED 로 떨어지고,
     *   화면 전체에 55% 검은 덮개가 깔렸다.
     *
     * ★ 사진에는 투명한 자리가 없다. 투명 배경 PNG 는 "뒤에 깔 그림" 이 아니라
     *   "위에 얹을 그림" 이라는 뜻이므로 덮지 않는다.
     */
    @Test
    @DisplayName("★ 투명 배경 PNG 는 장식이다 - 화면 전체에 덮개를 씌우지 않는다")
    void 투명_배경은_덮지_않는다() throws IOException {
        // 흰 네모 하나만 있고 나머지는 전부 투명 — 오려 낸 장식
        BufferedImage im = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(24, 24, 32, 32);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(im, "png", out);

        ImageProbe.Probed p = ImageProbe.probe(out.toByteArray()).orElseThrow();
        assertEquals(Asset.Tone.LIGHT, p.tone(), "투명 배경인데 섞인 사진으로 봤습니다");
        assertEquals(0, p.scrim(), "장식에 덮개가 깔렸습니다");
    }

    /** ★ 불투명 사진의 판정은 하나도 달라지면 안 된다 — 알파만 건너뛴 것이다 */
    @Test
    @DisplayName("불투명 사진은 전과 똑같이 판정한다")
    void 불투명_사진은_그대로() throws IOException {
        assertEquals(Asset.Tone.LIGHT, tone(Color.WHITE));
        assertEquals(Asset.Tone.DARK, tone(new Color(24, 22, 30)));
        assertEquals(Asset.Tone.MIXED, tone(new Color(120, 120, 120)));
    }

    /**
     * ★★ 파일 크기만 재면 **압축 폭탄**을 못 막는다.
     *   단색 PNG 는 6000×6000(3600만 화소)이 수십 KB 로 압축된다. 업로드 상한 3MB 를
     *   가볍게 통과하고, 풀리는 순간 화소당 4바이트라 140MB 를 먹는다.
     *   30000×30000 이면 3.6GB — 파일 한 장으로 서버가 멈춘다.
     *
     * ★ 그래서 ImageProbe 는 read(0) 보다 **먼저** getWidth · getHeight 로 크기를 본다.
     *   둘은 헤더만 읽어서 화소를 풀지 않는다.
     */
    @Test
    @DisplayName("★ 압축 폭탄은 풀기 전에 거절한다 - 파일이 작아도 화소가 많으면 안 받는다")
    void 압축_폭탄은_풀기_전에_거절한다() throws IOException {
        // ★ 같은 방법으로 만든 작은 PNG 는 통과한다 — 아래 거절이 "형식이 깨져서" 가 아니라는 증거
        assertEquals(40, ImageProbe.probe(flatPng(40, 40)).orElseThrow().width());

        byte[] bomb = flatPng(6000, 6000);
        assertTrue(bomb.length < 3 * 1024 * 1024, "업로드 상한 3MB 를 통과하는 크기여야 시험이 성립합니다");
        assertTrue(ImageProbe.probe(bomb).isEmpty(), "풀면 140MB 인데 받아들였습니다");
    }

    /** 단색 PNG 를 줄 단위로 흘려 쓴다 — 거대한 크기도 메모리에 안 펼쳐 놓고 만들 수 있다 */
    private static byte[] flatPng(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' });

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream head = new DataOutputStream(ihdr);
        head.writeInt(w);
        head.writeInt(h);
        head.write(new byte[] { 8, 2, 0, 0, 0 });   // 8비트 · 트루컬러 · 기본 압축 · 기본 필터 · 비인터레이스
        chunk(out, "IHDR", ihdr.toByteArray());

        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        try (DeflaterOutputStream zip = new DeflaterOutputStream(idat)) {
            byte[] row = new byte[1 + w * 3];       // 필터 바이트 + RGB, 전부 0 = 검정
            for (int y = 0; y < h; y++) zip.write(row);
        }
        chunk(out, "IDAT", idat.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] body) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(body);

        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(body.length);
        d.write(name);
        d.write(body);
        d.writeInt((int) crc.getValue());
    }

    private static double linear(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
