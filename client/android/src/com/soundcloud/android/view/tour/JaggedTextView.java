package com.soundcloud.android.view.tour;

import com.wehack.syncedQ.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

public class JaggedTextView extends TextView {
    private Paint mBackgroundPaint;

    public JaggedTextView(Context context) {
        super(context);
    }

    public JaggedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        applyColor(context, attrs);
    }

    public JaggedTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        applyColor(context, attrs);
    }

    private void applyColor(Context context, AttributeSet attrs) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.JaggedTextView);
        int color = array.getColor(R.styleable.JaggedTextView_jagged_background, 0x000000);
        getBackgroundPaint().setColor(color);
        array.recycle();
    }

    private Paint getBackgroundPaint() {
        if (mBackgroundPaint == null) {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(0x00000000);
        }

        return mBackgroundPaint;
    }

    @Override
    public void setBackgroundDrawable(Drawable drawable) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getLayout() == null) {
            forceLayout();
        }

        Layout layout = getLayout();

        canvas.translate(getPaddingLeft(), getPaddingTop());
        for (int line = 0; line < getLayout().getLineCount(); line++) {
            float left   = layout.getLineLeft(line);
            float top    = layout.getLineTop(line);
            float right  = layout.getLineRight(line);
            float bottom = layout.getLineBottom(line);

            // Apply padding to background rectangles
            if (line == 0) {
                top -= getPaddingTop();
            }

            if (line == layout.getLineCount() - 1) {
                bottom += getPaddingBottom();
            }

            left  -= getPaddingLeft();
            right += getPaddingRight();

            canvas.drawRect(left, top, right, bottom, getBackgroundPaint());
        }

        layout.getPaint().setColor(getTextColors().getDefaultColor());
        layout.draw(canvas);
    }
}
