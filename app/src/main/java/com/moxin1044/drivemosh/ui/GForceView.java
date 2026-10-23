package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class GForceView extends View {

    private static final float MAX_G = 1.0f;
    private static final long TRAIL_LIFETIME = 950L;

    public interface Listener {
        void onValues(float magnitude, float x, float y);
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowCyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowMagentaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();

    private final List<float[]> trail = new ArrayList<>();

    private RadialGradient glowCyanShader;
    private RadialGradient glowMagentaShader;
    private SweepGradient sweepShader;

    private float density;
    private float centerX;
    private float centerY;
    private float radius;

    private float targetX;
    private float targetY;
    private float currentX;
    private float currentY;
    private float displayedX;
    private float displayedY;

    private float magnitude;
    private float peak;
    private long lastFrameTime;
    private long lastTrailTime;

    private Listener listener;

    public GForceView(Context context) {
        super(context);
        init();
    }

    public GForceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(density);
        ringPaint.setColor(0x3300E5FF);
        ringPaint.setPathEffect(new DashPathEffect(new float[]{density * 4, density * 5}, 0));

        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(density * 1.3f);
        outerRingPaint.setColor(0x7700E5FF);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(density);
        tickPaint.setColor(0x5500E5FF);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(density);
        crossPaint.setColor(0x2200E5FF);
        crossPaint.setPathEffect(new DashPathEffect(new float[]{density * 2, density * 6}, 0));

        sweepPaint.setStyle(Paint.Style.FILL);

        trailPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFF00E5FF);

        dotRingPaint.setStyle(Paint.Style.STROKE);
        dotRingPaint.setStrokeWidth(density * 1.2f);
        dotRingPaint.setColor(0xAA00E5FF);

        glowCyanPaint.setStyle(Paint.Style.FILL);
        glowMagentaPaint.setStyle(Paint.Style.FILL);

        scalePaint.setStyle(Paint.Style.FILL);
        scalePaint.setColor(0x5500E5FF);
        scalePaint.setTextSize(density * 8f);
        scalePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        scalePaint.setTextAlign(Paint.Align.CENTER);
        scalePaint.setLetterSpacing(0.08f);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) * 0.40f;

        glowCyanShader = new RadialGradient(0, 0, density * 34f,
                new int[]{0xBB22EAFF, 0x3322EAFF, 0x0022EAFF},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
        glowMagentaShader = new RadialGradient(0, 0, density * 34f,
                new int[]{0xBBFF3CAC, 0x33FF3CAC, 0x00FF3CAC},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);

        sweepShader = new SweepGradient(centerX, centerY,
                new int[]{0x0000E5FF, 0x0000E5FF, 0x1F00E5FF, 0x0000E5FF},
                new float[]{0f, 0.62f, 0.86f, 1f});
    }

    public void setAcceleration(float x, float y) {
        targetX = x;
        targetY = y;

        displayedX += (x - displayedX) * 0.35f;
        displayedY += (y - displayedY) * 0.35f;

        magnitude = (float) Math.hypot(displayedX, displayedY);
        if (magnitude > peak) {
            peak = magnitude;
        }
        if (listener != null) {
            listener.onValues(magnitude, displayedX, displayedY);
        }
    }

    public float getPeak() {
        return peak;
    }

    public void resetPeak() {
        peak = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastFrameTime == 0) {
            lastFrameTime = now;
        }
        float dt = Math.min((now - lastFrameTime) / 1000f, 0.05f);
        lastFrameTime = now;

        float blend = Math.min(1f, dt * 12f);
        currentX += (displayedX - currentX) * blend;
        currentY += (displayedY - currentY) * blend;

        if (now - lastTrailTime > 45) {
            lastTrailTime = now;
            trail.add(new float[]{currentX, currentY, now});
            if (trail.size() > 34) {
                trail.remove(0);
            }
        }
        for (int i = trail.size() - 1; i >= 0; i--) {
            if (now - (long) trail.get(i)[2] > TRAIL_LIFETIME) {
                trail.remove(i);
            }
        }

        float dotMagnitude = Math.min(1f, (float) Math.hypot(currentX, currentY) / MAX_G);
        float heat = smoothStep(0.35f, 0.95f, dotMagnitude);

        drawRadar(canvas, now);
        drawGrid(canvas);
        drawTrail(canvas, now, heat);
        drawDot(canvas, now, dotMagnitude, heat);

        postInvalidateOnAnimation();
    }

    private void drawRadar(Canvas canvas, long now) {
        shaderMatrix.setRotate((now / 40f) % 360f, centerX, centerY);
        sweepShader.setLocalMatrix(shaderMatrix);
        sweepPaint.setShader(sweepShader);
        canvas.drawCircle(centerX, centerY, radius, sweepPaint);
        sweepPaint.setShader(null);
    }

    private void drawGrid(Canvas canvas) {
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, crossPaint);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, crossPaint);

        ringPaint.setAlpha(90);
        canvas.drawCircle(centerX, centerY, radius * 0.25f, ringPaint);
        canvas.drawCircle(centerX, centerY, radius * 0.5f, ringPaint);
        canvas.drawCircle(centerX, centerY, radius * 0.75f, ringPaint);

        canvas.drawCircle(centerX, centerY, radius, outerRingPaint);

        for (int i = 0; i < 72; i++) {
            float angle = (float) Math.toRadians(i * 5);
            boolean major = i % 6 == 0;
            float start = radius - (major ? density * 8f : density * 3.5f);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            tickPaint.setAlpha(major ? 150 : 70);
            canvas.drawLine(centerX + cos * start, centerY + sin * start,
                    centerX + cos * radius, centerY + sin * radius, tickPaint);
        }

        scalePaint.setAlpha(100);
        canvas.drawText("0.5 G", centerX, centerY - radius * 0.5f + density * 12f, scalePaint);
        scalePaint.setAlpha(150);
        canvas.drawText("1.0 G", centerX, centerY - radius + density * 13f, scalePaint);
    }

    private void drawTrail(Canvas canvas, long now, float heat) {
        for (int i = 0; i < trail.size(); i++) {
            float[] point = trail.get(i);
            float age = (now - point[2]) / (float) TRAIL_LIFETIME;
            float life = 1f - age;
            if (life <= 0f) {
                continue;
            }
            float x = centerX + clamp(point[0] / MAX_G, -1f, 1f) * radius;
            float y = centerY - clamp(point[1] / MAX_G, -1f, 1f) * radius;
            int alpha = (int) (110 * life * life);
            trailPaint.setColor(lerpColor(0xFF00E5FF, 0xFFFF3CAC, heat));
            trailPaint.setAlpha(alpha);
            canvas.drawCircle(x, y, density * (0.8f + 1.6f * life), trailPaint);
        }
    }

    private void drawDot(Canvas canvas, long now, float dotMagnitude, float heat) {
        float x = centerX + clamp(currentX / MAX_G, -1f, 1f) * radius;
        float y = centerY - clamp(currentY / MAX_G, -1f, 1f) * radius;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 240.0);

        shaderMatrix.setTranslate(x, y);

        glowCyanShader.setLocalMatrix(shaderMatrix);
        glowCyanPaint.setShader(glowCyanShader);
        glowCyanPaint.setAlpha((int) (200 * (1f - heat)));
        canvas.drawCircle(x, y, density * 34f, glowCyanPaint);

        glowMagentaShader.setLocalMatrix(shaderMatrix);
        glowMagentaPaint.setShader(glowMagentaShader);
        glowMagentaPaint.setAlpha((int) (220 * heat));
        canvas.drawCircle(x, y, density * 34f, glowMagentaPaint);

        glowCyanPaint.setShader(null);
        glowMagentaPaint.setShader(null);

        int dotColor = lerpColor(0xFF22EAFF, 0xFFFF3CAC, heat);
        dotPaint.setColor(dotColor);
        canvas.drawCircle(x, y, density * 5.2f, dotPaint);

        dotRingPaint.setColor(dotColor);
        dotRingPaint.setAlpha((int) (110 + 90 * pulse));
        canvas.drawCircle(x, y, density * (10f + pulse * 3.5f), dotRingPaint);

        dotPaint.setColor(0xFFFFFFFF);
        dotPaint.setAlpha(235);
        canvas.drawCircle(x, y, density * 2.1f, dotPaint);
        dotPaint.setAlpha(255);

        if (dotMagnitude > 0.82f) {
            tickPaint.setAlpha(40);
            canvas.drawCircle(centerX, centerY, radius, tickPaint);
        }
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.max(0f, Math.min(1f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
