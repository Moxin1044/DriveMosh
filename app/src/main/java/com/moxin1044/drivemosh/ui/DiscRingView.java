package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class DiscRingView extends View {

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private SweepGradient sweepShader;
    private RadialGradient haloShader;
    private float centerX;
    private float centerY;
    private float radius;
    private float angle;
    private long lastFrame;
    private boolean playing = true;
    private float density;

    public DiscRingView(Context context) {
        super(context);
        init();
    }

    public DiscRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(density * 2.6f);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(density);
        tickPaint.setColor(0x3D00E5FF);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(density);
        ringPaint.setColor(0x2E00E5FF);
        ringPaint.setPathEffect(new DashPathEffect(new float[]{density * 2, density * 4}, 0));

        haloPaint.setStyle(Paint.Style.FILL);
        innerPaint.setStyle(Paint.Style.STROKE);
        innerPaint.setStrokeWidth(density * 0.8f);
        innerPaint.setColor(0x2600E5FF);
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - density * 2f;

        haloShader = new RadialGradient(centerX, centerY, radius,
                new int[]{0x0000E5FF, 0x1400E5FF, 0x0000E5FF},
                new float[]{0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP);

        sweepShader = new SweepGradient(centerX, centerY,
                new int[]{0x0000E5FF, 0x0000E5FF, 0xEE00E5FF, 0x88FF3CAC, 0x0000E5FF},
                new float[]{0f, 0.55f, 0.78f, 0.92f, 1f});
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastFrame == 0) {
            lastFrame = now;
        }
        float dt = Math.min((now - lastFrame) / 1000f, 0.06f);
        lastFrame = now;
        angle += (playing ? 58f : 9f) * dt;
        angle %= 360f;

        haloPaint.setShader(haloShader);
        canvas.drawCircle(centerX, centerY, radius, haloPaint);
        haloPaint.setShader(null);

        for (int i = 0; i < 72; i++) {
            float a = (float) Math.toRadians(i * 5);
            boolean major = i % 6 == 0;
            float start = radius - (major ? density * 6f : density * 3f);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            tickPaint.setAlpha(major ? 120 : 55);
            canvas.drawLine(centerX + cos * start, centerY + sin * start,
                    centerX + cos * radius, centerY + sin * radius, tickPaint);
        }

        canvas.drawCircle(centerX, centerY, radius, ringPaint);
        canvas.drawCircle(centerX, centerY, radius - density * 6f, innerPaint);

        matrix.setRotate(angle, centerX, centerY);
        sweepShader.setLocalMatrix(matrix);
        arcPaint.setShader(sweepShader);
        canvas.drawCircle(centerX, centerY, radius - density * 2f, arcPaint);
        arcPaint.setShader(null);

        postInvalidateOnAnimation();
    }
}
