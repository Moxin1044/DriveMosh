package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class SpeedView extends View {

    private static final float MAX_GAUGE_SPEED = 200f;

    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gaugeBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gaugeProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient numberShader;
    private LinearGradient gaugeShader;

    private float density;
    private float numberBaseline;
    private float gaugeY;
    private float gaugeStart;
    private float gaugeEnd;

    private boolean permissionGranted;
    private boolean gpsEnabled;
    private boolean hasFix;

    private float currentSpeed;
    private float targetSpeed;
    private long lastFrame;

    public SpeedView(Context context) {
        super(context);
        init();
    }

    public SpeedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setColor(0xFF00E5FF);
        labelPaint.setTextSize(dp(9));
        labelPaint.setLetterSpacing(0.3f);
        labelPaint.setShadowLayer(dp(6), 0, 0, 0x6600E5FF);

        statusPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusPaint.setTextSize(dp(9));
        statusPaint.setTextAlign(Paint.Align.RIGHT);
        statusPaint.setLetterSpacing(0.08f);
        statusPaint.setColor(0xFF7E93A8);

        numberPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        numberPaint.setColor(0xFFFFFFFF);
        numberPaint.setLetterSpacing(0.02f);
        numberPaint.setShadowLayer(dp(14), 0, 0, 0xAA00E5FF);

        unitPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unitPaint.setColor(0xCC00E5FF);
        unitPaint.setTextSize(dp(11));

        hintPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setColor(0xFF00E5FF);
        hintPaint.setTextSize(dp(14));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setShadowLayer(dp(8), 0, 0, 0x8800E5FF);

        gaugeBasePaint.setStyle(Paint.Style.STROKE);
        gaugeBasePaint.setStrokeWidth(dp(2));
        gaugeBasePaint.setStrokeCap(Paint.Cap.ROUND);
        gaugeBasePaint.setColor(0xFF00E5FF);

        gaugeProgressPaint.setStyle(Paint.Style.STROKE);
        gaugeProgressPaint.setStrokeWidth(dp(2.6f));
        gaugeProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        gaugeProgressPaint.setShadowLayer(dp(8), 0, 0, 0x9900E5FF);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(1));
        tickPaint.setColor(0xFF00E5FF);

        headPaint.setStyle(Paint.Style.FILL);
        headPaint.setShadowLayer(dp(10), 0, 0, 0xCC00E5FF);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setPermissionGranted(boolean granted) {
        permissionGranted = granted;
        invalidate();
    }

    public void setGpsEnabled(boolean enabled) {
        gpsEnabled = enabled;
        invalidate();
    }

    public void setFix(boolean fix) {
        hasFix = fix;
        if (!fix) {
            targetSpeed = 0f;
        }
        invalidate();
    }

    public void setSpeed(float speedKmh) {
        targetSpeed = Math.max(0f, Math.min(300f, speedKmh));
        hasFix = true;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        gaugeY = h - dp(9);
        numberBaseline = gaugeY - dp(19);
        gaugeStart = dp(12);
        gaugeEnd = w - dp(12);

        numberPaint.setTextSize(dp(44));
        unitPaint.setTextSize(dp(11));

        numberShader = new LinearGradient(0, numberBaseline - dp(36), 0, numberBaseline,
                new int[]{0xFFFFFFFF, 0xFFC8FCFF, 0xFF00E5FF},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        gaugeShader = new LinearGradient(gaugeStart, 0, gaugeEnd, 0,
                new int[]{0xFF00E5FF, 0xFF55F0FF, 0xFFFF3CAC},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
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
        float dt = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        currentSpeed += (targetSpeed - currentSpeed) * Math.min(1f, dt * 5f);

        canvas.drawText("SPEED", dp(12), numberBaseline, labelPaint);
        drawGpsStatus(canvas, w, numberBaseline, now);

        boolean speedAvailable = permissionGranted && gpsEnabled;
        drawGauge(canvas, speedAvailable);

        if (!speedAvailable) {
            String hint = permissionGranted ? "开启 GPS 定位 ›" : "开启 GPS 测速 ›";
            canvas.drawText(hint, w / 2f, numberBaseline, hintPaint);
        } else {
            drawSpeedNumber(canvas, w);
        }

        postInvalidateOnAnimation();
    }

    private void drawGpsStatus(Canvas canvas, int w, float baseline, long now) {
        String text;
        int color;
        boolean active = permissionGranted && gpsEnabled && hasFix;
        if (!permissionGranted) {
            text = "GPS 未授权";
            color = 0xFFFF3CAC;
        } else if (!gpsEnabled) {
            text = "GPS 未开启";
            color = 0xFFFF3CAC;
        } else if (!hasFix) {
            text = "GPS 搜索中";
            color = 0xFF7E93A8;
        } else {
            text = "GPS";
            color = 0xFF00E5FF;
        }
        statusPaint.setColor(color);
        canvas.drawText(text, w - dp(12), baseline, statusPaint);

        float textWidth = statusPaint.measureText(text);
        float dotX = w - dp(12) - textWidth - dp(8);
        float dotY = baseline - dp(3);
        dotPaint.setColor(color);
        if (active) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 300.0);
            dotPaint.setAlpha((int) (150 + 105 * pulse));
            dotPaint.setShadowLayer(dp(7), 0, 0, color);
        } else {
            dotPaint.setAlpha(170);
            dotPaint.setShadowLayer(0, 0, 0, color);
        }
        canvas.drawCircle(dotX, dotY, dp(2f), dotPaint);
        dotPaint.setShadowLayer(0, 0, 0, color);
    }

    private void drawGauge(Canvas canvas, boolean speedAvailable) {
        tickPaint.setAlpha(70);
        for (int i = 0; i <= 10; i++) {
            float x = gaugeStart + (gaugeEnd - gaugeStart) * i / 10f;
            boolean major = i % 5 == 0;
            tickPaint.setAlpha(major ? 110 : 45);
            float top = gaugeY - (major ? dp(6) : dp(4));
            canvas.drawLine(x, top, x, gaugeY - dp(1), tickPaint);
        }

        gaugeBasePaint.setAlpha(speedAvailable ? 60 : 30);
        canvas.drawLine(gaugeStart, gaugeY, gaugeEnd, gaugeY, gaugeBasePaint);

        if (!speedAvailable || !hasFix || currentSpeed < 0.2f) {
            return;
        }
        float fraction = Math.min(1f, currentSpeed / MAX_GAUGE_SPEED);
        float headX = gaugeStart + (gaugeEnd - gaugeStart) * fraction;

        gaugeProgressPaint.setShader(gaugeShader);
        canvas.drawLine(gaugeStart, gaugeY, headX, gaugeY, gaugeProgressPaint);

        float heat = Math.min(1f, currentSpeed / 160f);
        int headColor = lerpColor(0xFF00E5FF, 0xFFFF3CAC, heat);
        headPaint.setColor(headColor);
        headPaint.setShadowLayer(dp(10), 0, 0, headColor);
        canvas.drawCircle(headX, gaugeY, dp(3.2f), headPaint);
        headPaint.setColor(0xFFFFFFFF);
        headPaint.setShadowLayer(0, 0, 0, headColor);
        canvas.drawCircle(headX, gaugeY, dp(1.2f), headPaint);
        gaugeProgressPaint.setShader(null);
    }

    private void drawSpeedNumber(Canvas canvas, int w) {
        String value = hasFix ? String.valueOf(Math.round(currentSpeed)) : "--";
        float numberWidth = numberPaint.measureText(value);
        float unitWidth = unitPaint.measureText("km/h");
        float gap = dp(7);
        float total = numberWidth + gap + unitWidth;
        float startX = (w - total) / 2f;

        if (hasFix) {
            numberPaint.setShader(numberShader);
            numberPaint.setColor(0xFFFFFFFF);
        } else {
            numberPaint.setShader(null);
            numberPaint.setColor(0xFF516477);
        }
        canvas.drawText(value, startX, numberBaseline, numberPaint);
        numberPaint.setShader(null);

        canvas.drawText("km/h", startX + numberWidth + gap, numberBaseline - dp(1), unitPaint);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private float dp(float value) {
        return value * density;
    }
}
