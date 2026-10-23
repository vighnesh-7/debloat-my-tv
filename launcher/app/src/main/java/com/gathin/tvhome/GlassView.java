package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A glassy focus frame: bright rim and outer glow, almost no fill over the app icon.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(7f * density);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2.2f * density);
        rim.setColor(0xF5FFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0xA6FFFFFF, 0x3DFFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 8f * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        for (int i = 1; i <= 4; i++) {
            glow.setStrokeWidth(i * 2.2f * density);
            glow.setColor((0x55 / i) << 24 | 0xFFFFFF);
            float o = i * 1.5f * density;
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o,
                    radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
