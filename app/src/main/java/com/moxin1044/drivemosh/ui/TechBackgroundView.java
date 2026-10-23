package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class TechBackgroundView extends View {

    private final Paint bgPaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient bgShader;
    private RadialGradient cyanGlow;
    private RadialGradient magentaGlow;
    private RadialGradient vignetteShader;
    private LinearGradient horizonShader;

    private float density;

    public TechBackgroundView(Context context) {
        super(context);
        init();
    }

    public TechBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density * 0.8f);
        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(density * 1.2f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        bgShader = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF04070D, 0xFF071522, 0xFF03060B},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        cyanGlow = new RadialGradient(w * 0.52f, h * 0.3f, Math.max(w, h) * 0.55f,
                new int[]{0x2A00E5FF, 0x0000E5FF}, null, Shader.TileMode.CLAMP);
        magentaGlow = new RadialGradient(w * 0.95f, h * 0.9f, h * 0.9f,
                new int[]{0x20FF3CAC, 0x00FF3CAC}, null, Shader.TileMode.CLAMP);
        vignetteShader = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.7f,
                new int[]{0x00000000, 0x00000000, 0xB302040A},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        horizonShader = new LinearGradient(0, 0, w, 0,
                new int[]{0x0000E5FF, 0x6600E5FF, 0x00FF3CAC},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        long time = SystemClock.uptimeMillis();

        bgPaint.setShader(bgShader);
        canvas.drawRect(0, 0, w, h, bgPaint);

        glowPaint.setShader(cyanGlow);
        canvas.drawRect(0, 0, w, h, glowPaint);
        glowPaint.setShader(magentaGlow);
        canvas.drawRect(0, 0, w, h, glowPaint);

        drawGrid(canvas, time, w, h);
        drawScanLine(canvas, time, w, h);

        vignettePaint.setShader(vignetteShader);
        canvas.drawRect(0, 0, w, h, vignettePaint);

        postInvalidateDelayed(33);
    }

    private void drawGrid(Canvas canvas, long time, int w, int h) {
        float cx = w / 2f;
        float horizon = h * 0.68f;
        int horizonColor = 0x0F66F4FF;
        gridPaint.setColor(horizonColor);

        for (int i = -12; i <= 12; i++) {
            float bottomX = cx + i * (w / 7f);
            canvas.drawLine(cx, horizon, bottomX, h, gridPaint);
        }

        float phase = (time % 3000L) / 3000f;
        for (int k = 0; k < 8; k++) {
            float p = ((k + phase) % 8f) / 8f;
            float y = horizon + (h - horizon) * p * p;
            int alpha = (int) (8 + 52 * p);
            gridPaint.setColor((alpha << 24) | 0x0066F4FF);
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        accentPaint.setShader(horizonShader);
        accentPaint.setAlpha(90);
        canvas.drawLine(0, horizon, w, horizon, accentPaint);
        accentPaint.setAlpha(255);
        accentPaint.setShader(null);
    }

    private void drawScanLine(Canvas canvas, long time, int w, int h) {
        float travel = h * 1.35f;
        float y = ((time % 9000L) / 9000f) * travel - h * 0.2f;
        float half = density * 34;
        scanPaint.setShader(new LinearGradient(0, y - half, 0, y + half,
                new int[]{0x0000E5FF, 0x1A00E5FF, 0x0000E5FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, y - half, w, y + half, scanPaint);
        scanPaint.setShader(null);
    }
}
