package com.moxin1044.drivemosh.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class TechPanel extends FrameLayout {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private LinearGradient fillShader;
    private LinearGradient accentShader;
    private float radius;
    private float bracketLength;
    private float bracketInset;

    public TechPanel(Context context) {
        super(context);
        init();
    }

    public TechPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        float density = getResources().getDisplayMetrics().density;
        radius = 22 * density;
        bracketLength = 15 * density;
        bracketInset = 9 * density;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(density);
        borderPaint.setColor(0x3300E5FF);

        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(1.4f * density);

        bracketPaint.setStyle(Paint.Style.STROKE);
        bracketPaint.setStrokeWidth(1.8f * density);
        bracketPaint.setColor(0xB300E5FF);
        bracketPaint.setStrokeCap(Paint.Cap.SQUARE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        fillShader = new LinearGradient(0, 0, 0, h,
                new int[]{0xD90B1622, 0xB3060C14},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        accentShader = new LinearGradient(w * 0.08f, 0, w * 0.55f, 0,
                new int[]{0x0000E5FF, 0xCC00E5FF, 0x0000E5FF},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fillShader == null) {
            return;
        }
        fillPaint.setShader(fillShader);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.drawRoundRect(rect, radius, radius, borderPaint);

        accentPaint.setShader(accentShader);
        float y = bracketInset + 0.5f;
        canvas.drawLine(rect.left + bracketInset, y, rect.right - bracketInset, y, accentPaint);
        accentPaint.setShader(null);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawBrackets(canvas);
    }

    private void drawBrackets(Canvas canvas) {
        float l = bracketInset;
        float t = bracketInset;
        float r = getWidth() - bracketInset;
        float b = getHeight() - bracketInset;
        float len = bracketLength;

        canvas.drawLine(l, t, l + len, t, bracketPaint);
        canvas.drawLine(l, t, l, t + len, bracketPaint);

        canvas.drawLine(r, t, r - len, t, bracketPaint);
        canvas.drawLine(r, t, r, t + len, bracketPaint);

        canvas.drawLine(l, b, l + len, b, bracketPaint);
        canvas.drawLine(l, b, l, b - len, bracketPaint);

        canvas.drawLine(r, b, r - len, b, bracketPaint);
        canvas.drawLine(r, b, r, b - len, bracketPaint);
    }
}
