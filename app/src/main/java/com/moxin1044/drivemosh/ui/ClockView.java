package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

public class ClockView extends View {

    private static final String[] WEEK_CN = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    private static final String[] WEEK_EN = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint colonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sideLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Calendar calendar = Calendar.getInstance();
    private LinearGradient timeShader;
    private LinearGradient underlineShader;
    private LinearGradient labelLineShader;
    private LinearGradient sideLineShader;

    private float density;
    private float timeSize;
    private float baseline;
    private float centerX;

    private String hours = "00";
    private String minutes = "00";
    private String seconds = "00";
    private String label = "DRIVE MODE";
    private String dateText = "";
    private int lastSecond = -1;

    public ClockView(Context context) {
        super(context);
        init();
    }

    public ClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

        timePaint.setTypeface(thin);
        timePaint.setColor(0xFFFFFFFF);
        timePaint.setShadowLayer(22 * density, 0, 0, 0xAA00E5FF);

        colonPaint.setTypeface(thin);
        colonPaint.setColor(0xFF00E5FF);
        colonPaint.setShadowLayer(14 * density, 0, 0, 0xAA00E5FF);

        secondsPaint.setTypeface(light);
        secondsPaint.setColor(0xFF00E5FF);
        secondsPaint.setShadowLayer(10 * density, 0, 0, 0x8800E5FF);
        secondsPaint.setLetterSpacing(0.08f);

        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setColor(0xCC00E5FF);
        labelPaint.setLetterSpacing(0.4f);
        labelPaint.setShadowLayer(8 * density, 0, 0, 0x6600E5FF);

        datePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        datePaint.setColor(0xB38FA9BF);
        datePaint.setLetterSpacing(0.14f);

        linePaint.setStyle(Paint.Style.FILL);
        sideLinePaint.setStyle(Paint.Style.STROKE);
        sideLinePaint.setStrokeWidth(density);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        centerX = w / 2f;

        labelPaint.setTextSize(clamp(h * 0.032f, 10 * density, 13 * density));
        datePaint.setTextSize(clamp(h * 0.038f, 10 * density, 15 * density));

        float size = h * 0.32f;
        timePaint.setTextSize(size);
        colonPaint.setTextSize(size);
        secondsPaint.setTextSize(size * 0.32f);

        float gap = size * 0.12f;
        float timeWidth = timePaint.measureText("00:00");
        float secondsWidth = secondsPaint.measureText("00");
        float available = w * 0.90f;
        float scale = available / (timeWidth + gap + secondsWidth);
        if (scale < 1f && scale > 0f) {
            size *= scale;
            timePaint.setTextSize(size);
            colonPaint.setTextSize(size);
            secondsPaint.setTextSize(size * 0.32f);
        }
        timeSize = size;

        Paint.FontMetrics fm = timePaint.getFontMetrics();
        baseline = h * 0.52f - (fm.ascent + fm.descent) / 2f;

        timeShader = new LinearGradient(0, baseline - size, 0, baseline,
                new int[]{0xFFFFFFFF, 0xFFB8FBFF, 0xFF00E5FF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        timePaint.setShader(timeShader);

        underlineShader = new LinearGradient(0, 0, w, 0,
                new int[]{0x0000E5FF, 0xAA00E5FF, 0xCCFF3CAC, 0x0000E5FF},
                new float[]{0.1f, 0.4f, 0.6f, 0.9f}, Shader.TileMode.CLAMP);
        labelLineShader = new LinearGradient(0, 0, w, 0,
                new int[]{0x0000E5FF, 0x6600E5FF},
                new float[]{0.2f, 0.5f}, Shader.TileMode.CLAMP);
        sideLineShader = new LinearGradient(0, h * 0.28f, 0, h * 0.72f,
                new int[]{0x0000E5FF, 0x4400E5FF, 0x0000E5FF},
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
        long now = System.currentTimeMillis();
        calendar.setTimeInMillis(now);
        int second = calendar.get(Calendar.SECOND);
        if (second != lastSecond) {
            lastSecond = second;
            hours = String.format(Locale.US, "%02d", calendar.get(Calendar.HOUR_OF_DAY));
            minutes = String.format(Locale.US, "%02d", calendar.get(Calendar.MINUTE));
            seconds = String.format(Locale.US, "%02d", second);
            int dow = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            dateText = String.format(Locale.US, "%04d.%02d.%02d   %s   %s",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH),
                    WEEK_EN[dow], WEEK_CN[dow]);
        }

        float pulse = 0.5f + 0.5f * (float) Math.cos((now % 2000L) / 2000.0 * Math.PI * 2);

        drawSideLines(canvas, w, h);
        drawLabel(canvas, pulse);
        drawTime(canvas, pulse, w);
        drawUnderline(canvas, w);
        drawDate(canvas);

        postInvalidateDelayed(33);
    }

    private void drawSideLines(Canvas canvas, int w, int h) {
        sideLinePaint.setShader(sideLineShader);
        canvas.drawLine(w * 0.035f, h * 0.3f, w * 0.035f, h * 0.7f, sideLinePaint);
        canvas.drawLine(w * 0.965f, h * 0.3f, w * 0.965f, h * 0.7f, sideLinePaint);
        sideLinePaint.setShader(null);
    }

    private void drawLabel(Canvas canvas, float pulse) {
        float labelWidth = labelPaint.measureText(label);
        float labelBaseline = baseline - timeSize * 1.06f;
        canvas.drawText(label, centerX - labelWidth / 2f, labelBaseline, labelPaint);

        dotPaint.setColor(0xCC00E5FF);
        dotPaint.setAlpha((int) (110 + 145 * pulse));
        float dotY = labelBaseline - labelPaint.getTextSize() * 0.32f;
        canvas.drawCircle(centerX - labelWidth / 2f - density * 16f, dotY, density * 2.2f, dotPaint);

        linePaint.setShader(labelLineShader);
        linePaint.setAlpha(150);
        float lineY = dotY;
        canvas.drawRect(centerX - labelWidth / 2f - density * 52f, lineY - density * 0.5f,
                centerX - labelWidth / 2f - density * 26f, lineY + density * 0.5f, linePaint);
        linePaint.setShader(null);
        linePaint.setAlpha(255);
    }

    private void drawTime(Canvas canvas, float pulse, int w) {
        float hhWidth = timePaint.measureText(hours);
        float colonWidth = timePaint.measureText(":");
        float mmWidth = timePaint.measureText(minutes);
        float gap = timeSize * 0.12f;
        float secWidth = secondsPaint.measureText(seconds);
        float total = hhWidth + colonWidth + mmWidth + gap + secWidth;
        float startX = (w - total) / 2f;

        canvas.drawText(hours, startX, baseline, timePaint);

        colonPaint.setAlpha((int) (90 + 165 * pulse));
        float colonX = startX + hhWidth;
        canvas.drawText(":", colonX, baseline, colonPaint);

        float mmX = colonX + colonWidth;
        canvas.drawText(minutes, mmX, baseline, timePaint);

        float secX = mmX + mmWidth + gap;
        float secBaseline = baseline - timeSize * 0.46f;
        canvas.drawText(seconds, secX, secBaseline, secondsPaint);
    }

    private void drawUnderline(Canvas canvas, int w) {
        float y = baseline + timeSize * 0.16f;
        float half = w * 0.30f;

        linePaint.setShader(null);
        linePaint.setColor(0x3300E5FF);
        canvas.drawRect(centerX - half, y - density * 4f, centerX + half, y + density * 4f, linePaint);

        linePaint.setShader(underlineShader);
        linePaint.setAlpha(220);
        canvas.drawRect(centerX - half, y - density * 0.8f, centerX + half, y + density * 0.8f, linePaint);
        linePaint.setShader(null);
    }

    private void drawDate(Canvas canvas) {
        float dateWidth = datePaint.measureText(dateText);
        float dateBaseline = baseline + timeSize * 0.62f;
        canvas.drawText(dateText, centerX - dateWidth / 2f, dateBaseline, datePaint);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
