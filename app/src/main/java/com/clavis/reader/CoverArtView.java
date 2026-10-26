package com.clavis.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** A raised book cover. The card follows the image aspect ratio instead of cropping it. */
final class CoverArtView extends FrameLayout {
    CoverArtView(Context context) {
        super(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(16));
        setBackground(background);
        setPadding(dp(10), dp(10), dp(10), dp(10));
        setClipToOutline(true);
        setElevation(dp(14));
        if (Build.VERSION.SDK_INT >= 28) {
            setOutlineAmbientShadowColor(0x55292664);
            setOutlineSpotShadowColor(0x66292664);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void setArtwork(Bitmap bitmap, int maxWidthDp, int maxHeightDp) {
        float aspect = bitmap.getWidth() / (float) bitmap.getHeight();
        int border = dp(20);
        int imageWidth = Math.min(dp(maxWidthDp) - border, Math.round((dp(maxHeightDp) - border) * aspect));
        int width = imageWidth + border;
        int height = Math.round(imageWidth / aspect) + border;
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = width;
        params.height = height;
        setLayoutParams(params);
        removeAllViews();
        ImageView image = new ImageView(getContext());
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Audiobook cover");
        addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    }
}

