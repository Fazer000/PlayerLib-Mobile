package com.example.animelib.ui;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.card.MaterialCardView;

public class PortraitCardView extends MaterialCardView {

    public PortraitCardView(Context context) {
        super(context);
    }

    public PortraitCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PortraitCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force portrait elongated 9:16 aspect ratio (height = width * 16 / 9)
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (width * 16f / 9f);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
