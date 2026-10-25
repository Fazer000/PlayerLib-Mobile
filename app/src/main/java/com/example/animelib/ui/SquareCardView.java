package com.example.animelib.ui;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.card.MaterialCardView;

public class SquareCardView extends MaterialCardView {

    public SquareCardView(Context context) {
        super(context);
    }

    public SquareCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force square aspect ratio (height = width)
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
