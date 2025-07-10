package org.telegram.ui.Stars;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.List;

import static org.telegram.messenger.AndroidUtilities.*;

public class StarGiftPatterns {

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_ACTION = 1;
    public static final int TYPE_GIFT = 2;
    public static final int TYPE_LINK_PREVIEW = 3;

    private static final float[][] patternLocations = new float[][] {
        {
            83.33f, 24, 27.33f, .22f,
            68.66f, 75.33f, 25.33f, .21f,
            0, 86, 25.33f, .12f,
            -68.66f, 75.33f, 25.33f, .21f,
            -82.66f, 13.66f, 27.33f, .22f,
            -80, -33.33f, 20, .24f,
            -46.5f, -63.16f, 27, .21f,
            1, -82.66f, 20, .15f,
            46.5f, -63.16f, 27, .21f,
            80, -33.33f, 19.33f, .24f,

            115.66f, -63, 20, .15f,
            134, -10.66f, 20, .18f,
            118.66f, 55.66f, 20, .15f,
            124.33f, 98.33f, 20, .11f,

            -128, 98.33f, 20, .11f,
            -108, 55.66f, 20, .15f,
            -123.33f, -10.66f, 20, .18f,
            -116, -63.33f, 20, .15f
        },
        {
            27.33f, -57.66f, 20, .12f,
            59, -32, 19.33f, .22f,
            77, 4.33f, 22.66f, .2f,
            100, 40.33f, 18, .12f,
            58.66f, 59, 20, .18f,
            73.33f, 100.33f, 22.66f, .15f,
            75, 155, 22, .11f,

            -27.33f, -57.33f, 20, .12f,
            -59, -32.33f, 19.33f, .2f,
            -77, 4.66f, 23.33f, .2f,
            -98.66f, 41, 18.66f, .12f,
            -58, 59.33f, 19.33f, .18f,
            -73.33f, 100, 22, .15f,
            -75.66f, 155, 22, .11f
        },
        {
            -0.83f, -52.16f, 12.33f, .2f,
            26.66f, -40.33f, 16, .2f,
            44.16f, -20.5f, 12.33f, .2f,
            53, 7.33f, 16, .2f,
            31, 23.66f, 14.66f, .2f,
            0, 32, 13.33f, .2f,
            -29, 23.66f, 14, .2f,
            -53, 7.33f, 16, .2f,
            -44.5f, -20.16f, 12.33f, .2f,
            -27.33f, -40.33f, 16, .2f,
            43.66f, 50, 14.66f, .2f,
            -41.66f, 48, 14.66f, .2f
        },
        {
            -0.16f, -103.5f, 20.33f, .15f,
            39.66f, -77.33f, 26.66f, .15f,
            70.66f, -46.33f, 21.33f, .15f,
            84.5f, -3.83f, 29.66f, .15f,
            65.33f, 56.33f, 24.66f, .15f,
            0, 67.66f, 24.66f, .15f,
            -65.66f, 56.66f, 24.66f, .15f,
            -85, -4, 29.33f, .15f,
            -70.66f, -46.33f, 21.33f, .15f,
            -40.33f, -77.66f, 26.66f, .15f,

            62.66f, -109.66f, 21.33f, .11f,
            103.166f, -67.5f, 20.33f, .11f,
            110.33f, 37.66f, 20.66f, .11f,
            94.166f, 91.16f, 20.33f, .11f,
            38.83f, 91.16f, 20.33f, .11f,
            0, 112.5f, 20.33f, .11f,
            -38.83f, 91.16f, 20.33f, .11f,
            -94.166f, 91.16f, 20.33f, .11f,
            -110.33f, 37.66f, 20.66f, .11f,
            -103.166f, -67.5f, 20.33f, .11f,
            -62.66f, -109.66f, 21.33f, .11f
        }
    };

    public static void drawPattern(Canvas canvas, Drawable pattern, float w, float h, float alpha, float scale) {
        drawPattern(canvas, TYPE_DEFAULT, pattern, w, h, alpha, scale);
    }

    public static void drawPattern(Canvas canvas, int type, Drawable pattern, float w, float h, float alpha, float scale) {
        if (alpha <= 0.0f) return;
        for (int i = 0; i < patternLocations[type].length; i += 4) {
            final float x = patternLocations[type][i];
            final float y = patternLocations[type][i + 1];
            final float size = patternLocations[type][i + 2];
            final float thisAlpha = patternLocations[type][i + 3];

            float cx = x, cy = y, sz = size;
            if (w < h && type == TYPE_DEFAULT) {
                cx = y;
                cy = x;
            }
            cx *= scale;
            cy *= scale;
            sz *= scale;
            pattern.setBounds((int) (dp(cx) - dp(sz) / 2.0f), (int) (dp(cy) - dp(sz) / 2.0f), (int) (dp(cx) + dp(sz) / 2.0f), (int) (dp(cy) + dp(sz) / 2.0f));

            pattern.setAlpha((int) (0xFF * alpha * thisAlpha));
            pattern.draw(canvas);
        }
    }

    private static final float[] profileRight = new float[] {
        -35.66f, -5, 24, .2388f,
        -14.33f, -29.33f, 20.66f, .32f,
        -15, -73.66f, 19.33f, .32f,
        -2, -99.66f, 18, .1476f,
        -64.33f, -24.66f, 23.33f, .3235f,
        -40.66f, -53.33f, 24, .3654f,
        -50.33f, -85.66f, 20, .172f,
        -96, -1.33f, 19.33f, .3343f,
        -136.66f, -13, 18.66f, .2569f,
        -104.66f, -33.66f, 20.66f, .2216f,
        -82, -62.33f, 22.66f, .2562f,
        -131.66f, -60, 18, .1316f,
        -105.66f, -88.33f, 18, .1487f
    };
    private static final float[] profileLeft = new float[] {
        0, -107.33f, 16, .1505f,
        14.33f, -84, 18, .1988f,
        0, -50.66f, 18.66f, .3225f,
        13, -15, 18.66f, .37f,
        43.33f, 1, 18.66f, .3186f
    };

    public static void drawProfilePattern(Canvas canvas, Drawable pattern, float w, float h, float alpha, float full) {
        if (alpha <= 0.0f) return;

        final float b = h;
        final float l = 0, r = w;

        if (full > 0) {
            for (int i = 0; i < profileLeft.length; i += 4) {
                final float x = profileLeft[i];
                final float y = profileLeft[i + 1];
                final float size = profileLeft[i + 2];
                final float thisAlpha = profileLeft[i + 3];

                pattern.setBounds(
                    (int) (l + dpf2(x) - dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) - dpf2(size) / 2.0f),
                    (int) (l + dpf2(x) + dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) + dpf2(size) / 2.0f)
                );
                pattern.setAlpha((int) (0xFF * alpha * thisAlpha * full));
                pattern.draw(canvas);
            }

            final float sl = 77.5f, sr = 173.33f;
            final float space = w / AndroidUtilities.density - sl - sr;
            int count = Math.max(0, Math.round(space / 27.25f));
            if (count % 2 == 0) {
                count++;
            }
            for (int i = 0; i < count; ++i) {
                final float x = sl + space * ((float) i / (count - 1));
                final float y = i % 2 == 0 ? 0 : -12.5f;
                final float size = 17;
                final float thisAlpha = .21f;

                pattern.setBounds(
                    (int) (l + dpf2(x) - dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) - dpf2(size) / 2.0f),
                    (int) (l + dpf2(x) + dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) + dpf2(size) / 2.0f)
                );
                pattern.setAlpha((int) (0xFF * alpha * thisAlpha * full));
                pattern.draw(canvas);
            }
        }

        for (int i = 0; i < profileRight.length; i += 4) {
            final float x = profileRight[i];
            final float y = profileRight[i + 1];
            final float size = profileRight[i + 2];
            final float thisAlpha = profileRight[i + 3];

            pattern.setBounds(
                (int) (r + dpf2(x) - dpf2(size) / 2.0f),
                (int) (b + dpf2(y) - dpf2(size) / 2.0f),
                (int) (r + dpf2(x) + dpf2(size) / 2.0f),
                (int) (b + dpf2(y) + dpf2(size) / 2.0f)
            );
            pattern.setAlpha((int) (0xFF * alpha * thisAlpha));
            pattern.draw(canvas);
        }
    }

    private static final float[] radialData = new float[]{
            // Vertical line
            60.00F, 1.571F, 18.67F, .3F,
            68.67F, -1.571F, 17.33F, .3F,
            // Horizontal line (r)
            133.17F, -0.050F, 15.67F, .6F,
            85.15F, -0.059F, 17.33F, .4F,
            // Top diagonal line (r)
            64.10F, 0.464F, 18F, 0,
            107.18F, 0.468F, 15.67F, .15F,
            // Almost top (r)
            84.91F, 1.012F, 15.33F, .5F,
            // Bottom diagonal line (r)
            72.11F, -0.588F, 18F, 0,
            117.21F, -0.554F, 16.67F, .15F,
            // Almost bottom (r)
            94.83F, -1.088F, 18.67F, .7F
    };
    private static final int radialPoints = radialData.length / 4;

    public static void drawRadialPattern(Canvas canvas, Drawable pattern, float cx, float cy, float ty, float alpha, float progress, float overscroll) {
        for (int p = 0; p < radialPoints; p++) {
            boolean flip = p >= 2;
            drawRadialPatternElement(canvas, pattern, radialData[p*4], radialData[p*4+1], radialData[p*4+2], radialData[p*4+3], cx, cy, ty, alpha, progress, overscroll, flip ? p : -1);
        }
    }

    public static void drawRadialPattern(Canvas canvas, List<? extends RadialPatternElement> elements, float cx, float cy, float ty, float alpha, float progress, float overscroll) {
        for (int p = 0; p < elements.size(); p ++) {
            float[] info = elements.get(p).getRadialInfo();
            drawRadialPatternElement(canvas, elements.get(p).getRadialDrawable(), info[0], info[1], info[2], info[3], cx, cy, ty, alpha, progress, overscroll, -1);
        }
    }

    private static void drawRadialPatternElement(Canvas canvas, Drawable drawable, float length, float angle, float size, float friction, float cx, float ay, float ty, float alpha, float progress, float overscroll, int flipIndex) {
        if (flipIndex >= 0) {
            // Add a tiny friction delta to the flipped element for a more natural animation
            drawRadialPatternElement(canvas, drawable, length, angle, size, friction, cx, ay, ty, alpha, progress, overscroll, -1);
            drawRadialPatternElement(canvas, drawable, length, (float) Math.PI - angle, size, friction + (flipIndex % 2 == 0 ? 0.05F : -0.05F), cx, ay, ty, alpha, progress, overscroll,-1);
            return;
        }
        // Regular layout
        float halfSize = dpf2(size) / 2F;
        float x = dpf2(length) * (float) Math.cos(angle);
        float y = dpf2(length) * (float) Math.sin(angle);
        alpha *= lerp(1F, .4F, (length - 60F) / 80F);

        // Progress animation
        float clip0 = .3F * (1F - friction);
        float clip1 = 1F - .15F * friction;
        progress = Math.max(0F, Math.min(1F, (progress - clip0) / (clip1 - clip0)));
        float shrink = (float) Math.pow(progress, lerp(3F, 0.2F, friction)); // ease in or out depending on friction
        x *= shrink;
        y *= shrink;
        if (progress == 1F && overscroll > 0F) {
            x *= (1F + .3F * overscroll);
            y *= (1F + .3F * overscroll);
        }
        alpha *= lerp(.6F, 1F, shrink);
        halfSize *= lerp(.6F, 1F, shrink);
        float cy = lerp(ay, ty, CubicBezierInterpolator.EASE_OUT.getInterpolation(progress));

        // Users can pass alpha = -1 to disable alpha interpolation
        drawable.setAlpha(alpha < 0F ? 0xFF : (int) (0xFF * alpha));
        drawAt(canvas, drawable, halfSize, cx + x, cy - y);
    }

    public interface RadialPatternElement {
        float[] getRadialInfo();
        Drawable getRadialDrawable();
    }

    private static void drawAt(Canvas canvas, Drawable pattern, float halfSize, float cx, float cy) {
        pattern.setBounds((int) (cx - halfSize), (int) (cy - halfSize), (int) (cx + halfSize), (int) (cy + halfSize));
        pattern.draw(canvas);
    }

}
