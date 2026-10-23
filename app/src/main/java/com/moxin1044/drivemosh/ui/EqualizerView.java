package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class EqualizerView extends View {

    private static final int BARS = 7;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private LinearGradient shader;
    private boolean playing = true;
    private float density;
    private float[] levels = new float[BARS];
    private long lastFrame;

    public EqualizerView(Context context) {
        super(context);
        init();
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        barPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BARS; i++) {
            levels[i] = 0.25f;
        }
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h == 0) {
            return;
        }
        shader = new LinearGradient(0, h, 0, 0,
                new int[]{0xFFFF3CAC, 0xFF00E5FF},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        barPaint.setShader(shader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastFrame == 0) {
            lastFrame = now;
        }
        float dt = Math.min((now - lastFrame) / 1000f, 0.08f);
        lastFrame = now;

        float gap = density * 3f;
        float barWidth = (w - gap * (BARS - 1)) / BARS;
        barPaint.setAlpha(playing ? 235 : 120);

        for (int i = 0; i < BARS; i++) {
            float energy;
            if (playing) {
                float s1 = (float) Math.abs(Math.sin(now / 210.0 + i * 1.35f));
                float s2 = (float) Math.abs(Math.sin(now / 640.0 + i * 0.72f));
                energy = 0.18f + 0.82f * (s1 * 0.65f + s2 * 0.35f);
                energy *= 0.75f + 0.25f * (float) Math.sin(i * 1.9f);
            } else {
                energy = 0.12f + 0.06f * (float) Math.sin(now / 900.0 + i);
            }
            levels[i] += (energy - levels[i]) * Math.min(1f, dt * 14f);

            float barHeight = Math.max(barWidth, h * levels[i]);
            float left = i * (barWidth + gap);
            barRect.set(left, h - barHeight, left + barWidth, h);
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, barPaint);
        }
        postInvalidateOnAnimation();
    }
}
