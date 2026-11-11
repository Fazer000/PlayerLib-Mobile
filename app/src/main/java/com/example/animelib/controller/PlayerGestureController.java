package com.example.animelib.controller;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;

import com.example.animelib.R;
import com.example.animelib.ui.M3LinearProgressIndicator;
import com.example.animelib.util.GesturePlaqueAnimationHelper;

import java.util.Locale;

/**
 * Контроллер жестов воспроизведения видео (яркость, громкость, перемотка свайпом, 2x скорость, двойной тап).
 * Использует централизованный GesturePlaqueAnimationHelper для плавных кинематографичных анимаций появления/исчезновения.
 */
public class PlayerGestureController {

    public interface GestureCallback {
        void onSingleTap();
        void onSeekCommit(long targetPositionMs);
        long getCurrentPosition();
        long getDuration();
        void onHoldSpeed(boolean isHolding);
        void onHoldSpeedChanged(float speed);
        boolean isLocked();
        int getSkipDurationSeconds();
    }

    private final Activity activity;
    private final PlayerView playerView;
    private final GestureCallback callback;

    private final AudioManager audioManager;
    private final int maxVolume;

    // UI Overlays - Seek Plaque
    private final View seekPreviewPlaque;
    private final ImageView seekPreviewIcon;
    private final TextView seekPreviewOffset;
    private final TextView seekPreviewTime;
    private final TextView seekPreviewDuration;

    // UI Overlays - Hold Speed Plaque
    private final View holdSpeedPlaque;
    private final ImageView holdSpeedIcon;
    private final TextView holdSpeedToast;

    // UI Overlays - Double Tap Skip
    private final LinearLayout skipIndicatorLeft;
    private final TextView skipTextLeft;
    private final ImageView skipIconLeft;
    private final LinearLayout skipIndicatorRight;
    private final TextView skipTextRight;
    private final ImageView skipIconRight;

    // UI Overlays - Brightness / Volume Plaque
    private final View gestureIndicatorPlaque;
    private final ImageView gestureIndicatorIcon;
    private final M3LinearProgressIndicator gestureIndicatorProgress;
    private final TextView gestureIndicatorText;

    // Gesture State
    private float touchStartX = -1f, touchStartY = -1f;
    private float holdStartX = 0f;
    private boolean isTouching = false;
    private boolean isHorizontalSeeking = false;
    private boolean isVerticalBrightness = false;
    private boolean isVerticalVolume = false;
    private boolean isHoldingSpeed = false;
    private long seekTargetPosition = 0;

    private final Handler holdSpeedHandler = new Handler(Looper.getMainLooper());
    private final Handler hidePlaqueHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideGestureIndicatorRunnable;
    private final Runnable hideSeekPreviewRunnable;
    private Runnable hideLeftSkipRunnable = null;
    private Runnable hideRightSkipRunnable = null;

    public PlayerGestureController(@NonNull Activity activity,
                                   @NonNull PlayerView playerView,
                                   @Nullable View seekPreviewPlaque,
                                   @Nullable View holdSpeedPlaque,
                                   @Nullable LinearLayout skipIndicatorLeft,
                                   @Nullable TextView skipTextLeft,
                                   @Nullable LinearLayout skipIndicatorRight,
                                   @Nullable TextView skipTextRight,
                                   @Nullable View gestureIndicatorPlaque,
                                   @Nullable ImageView gestureIndicatorIcon,
                                   @Nullable M3LinearProgressIndicator gestureIndicatorProgress,
                                   @Nullable TextView gestureIndicatorText,
                                   @NonNull GestureCallback callback) {
        this.activity = activity;
        this.playerView = playerView;
        this.seekPreviewPlaque = seekPreviewPlaque;
        if (seekPreviewPlaque != null) {
            this.seekPreviewIcon = seekPreviewPlaque.findViewById(R.id.seekPreviewIcon);
            this.seekPreviewOffset = seekPreviewPlaque.findViewById(R.id.seekPreviewOffset);
            this.seekPreviewTime = seekPreviewPlaque.findViewById(R.id.seekPreviewTime);
            this.seekPreviewDuration = seekPreviewPlaque.findViewById(R.id.seekPreviewDuration);
        } else {
            this.seekPreviewIcon = null;
            this.seekPreviewOffset = null;
            this.seekPreviewTime = null;
            this.seekPreviewDuration = null;
        }

        this.holdSpeedPlaque = holdSpeedPlaque;
        if (holdSpeedPlaque != null) {
            this.holdSpeedIcon = holdSpeedPlaque.findViewById(R.id.holdSpeedIcon);
            this.holdSpeedToast = holdSpeedPlaque instanceof TextView ? (TextView) holdSpeedPlaque : holdSpeedPlaque.findViewById(R.id.holdSpeedToast);
        } else {
            this.holdSpeedIcon = null;
            this.holdSpeedToast = null;
        }

        this.skipIndicatorLeft = skipIndicatorLeft;
        this.skipTextLeft = skipTextLeft;
        this.skipIconLeft = skipIndicatorLeft != null ? skipIndicatorLeft.findViewById(R.id.skipIconLeft) : null;

        this.skipIndicatorRight = skipIndicatorRight;
        this.skipTextRight = skipTextRight;
        this.skipIconRight = skipIndicatorRight != null ? skipIndicatorRight.findViewById(R.id.skipIconRight) : null;

        this.gestureIndicatorPlaque = gestureIndicatorPlaque;
        this.gestureIndicatorIcon = gestureIndicatorIcon;
        this.gestureIndicatorProgress = gestureIndicatorProgress;
        this.gestureIndicatorText = gestureIndicatorText;
        if (this.gestureIndicatorText != null) {
            // Стабилизируем ширину индикатора, чтобы числовые проценты не вызывали микро-дергания плашки
            int maxPercentWidth = (int) Math.ceil(this.gestureIndicatorText.getPaint().measureText("100%"));
            this.gestureIndicatorText.setWidth(maxPercentWidth);
        }

        this.callback = callback;

        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.maxVolume = audioManager != null ? audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;

        this.hideGestureIndicatorRunnable = () -> GesturePlaqueAnimationHelper.hide(this.gestureIndicatorPlaque);
        this.hideSeekPreviewRunnable = () -> GesturePlaqueAnimationHelper.hide(this.seekPreviewPlaque);

        setupGestureListeners();
    }

    /**
     * Динамически перестраивает размеры и отступы плашек перемотки, зажатия 2x и жестов
     * в зависимости от ориентации экрана.
     */
    public void updateOrientation(boolean isLandscape) {
        float density = activity.getResources().getDisplayMetrics().density;

        // 1. Двойной тап (перемотка назад и вперед)
        int skipMargin = (int) ((isLandscape ? 130 : 32) * density);
        int iconSize = (int) ((isLandscape ? 64 : 36) * density);
        float skipTextSize = isLandscape ? 14f : 11.5f;
        int skipTextPadH = (int) ((isLandscape ? 12 : 8) * density);
        int skipTextPadV = (int) ((isLandscape ? 6 : 2) * density);
        int skipTextMarginTop = (int) ((isLandscape ? 8 : 3) * density);

        if (skipIndicatorLeft != null && skipIndicatorLeft.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) skipIndicatorLeft.getLayoutParams();
            lp.leftMargin = skipMargin;
            lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            skipIndicatorLeft.setLayoutParams(lp);
        }
        if (skipIconLeft != null) {
            ViewGroup.LayoutParams lp = skipIconLeft.getLayoutParams();
            lp.width = iconSize;
            lp.height = iconSize;
            skipIconLeft.setLayoutParams(lp);
        }
        if (skipTextLeft != null) {
            skipTextLeft.setTextSize(skipTextSize);
            skipTextLeft.setPadding(skipTextPadH, skipTextPadV, skipTextPadH, skipTextPadV);
            if (skipTextLeft.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) skipTextLeft.getLayoutParams();
                lp.topMargin = skipTextMarginTop;
                skipTextLeft.setLayoutParams(lp);
            }
        }

        if (skipIndicatorRight != null && skipIndicatorRight.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) skipIndicatorRight.getLayoutParams();
            lp.rightMargin = skipMargin;
            lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            skipIndicatorRight.setLayoutParams(lp);
        }
        if (skipIconRight != null) {
            ViewGroup.LayoutParams lp = skipIconRight.getLayoutParams();
            lp.width = iconSize;
            lp.height = iconSize;
            skipIconRight.setLayoutParams(lp);
        }
        if (skipTextRight != null) {
            skipTextRight.setTextSize(skipTextSize);
            skipTextRight.setPadding(skipTextPadH, skipTextPadV, skipTextPadH, skipTextPadV);
            if (skipTextRight.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) skipTextRight.getLayoutParams();
                lp.topMargin = skipTextMarginTop;
                skipTextRight.setLayoutParams(lp);
            }
        }

        // 2. Плашка перемотки свайпом
        if (seekPreviewPlaque != null) {
            if (seekPreviewPlaque.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) seekPreviewPlaque.getLayoutParams();
                lp.topMargin = (int) ((isLandscape ? 80 : 18) * density);
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                seekPreviewPlaque.setLayoutParams(lp);
            }
        }

        // 3. Плашка скорости при зажатии
        if (holdSpeedPlaque != null) {
            if (holdSpeedPlaque.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) holdSpeedPlaque.getLayoutParams();
                lp.topMargin = (int) ((isLandscape ? 80 : 14) * density);
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                holdSpeedPlaque.setLayoutParams(lp);
            }
        }

        // 4. Плашка громкости и яркости
        if (gestureIndicatorPlaque != null) {
            if (gestureIndicatorPlaque.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) gestureIndicatorPlaque.getLayoutParams();
                lp.topMargin = (int) ((isLandscape ? 80 : 16) * density);
                lp.width = (int) ((isLandscape ? 172 : 130) * density);
                lp.height = (int) ((isLandscape ? 36 : 28) * density);
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                gestureIndicatorPlaque.setLayoutParams(lp);
            }
        }
    }

    public void release() {
        holdSpeedHandler.removeCallbacksAndMessages(null);
        hidePlaqueHandler.removeCallbacksAndMessages(null);
        GesturePlaqueAnimationHelper.hideImmediate(seekPreviewPlaque);
        GesturePlaqueAnimationHelper.hideImmediate(holdSpeedPlaque);
        GesturePlaqueAnimationHelper.hideImmediate(gestureIndicatorPlaque);
        GesturePlaqueAnimationHelper.hideImmediate(skipIndicatorLeft);
        GesturePlaqueAnimationHelper.hideImmediate(skipIndicatorRight);
    }

    private float initialBrightness = 0.5f;
    private int initialVolume = 0;

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestureListeners() {
        GestureDetector gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (!callback.isLocked()) {
                    callback.onSingleTap();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (callback.isLocked()) return false;
                int width = playerView.getWidth();
                int seekSec = callback.getSkipDurationSeconds();
                if (seekSec <= 0) seekSec = 10;
                long currentPos = callback.getCurrentPosition();
                long duration = callback.getDuration();

                if (e.getX() < width / 3.0f) {
                    long newPos = Math.max(0, currentPos - (seekSec * 1000L));
                    callback.onSeekCommit(newPos);
                    showDoubleTapSkipIndicator(false, seekSec);
                    return true;
                } else if (e.getX() > (width * 2.0f / 3.0f)) {
                    long newPos = Math.min(duration, currentPos + (seekSec * 1000L));
                    callback.onSeekCommit(newPos);
                    showDoubleTapSkipIndicator(true, seekSec);
                    return true;
                }
                return false;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            if (callback.isLocked()) return false;

            float density = activity.getResources().getDisplayMetrics().density;
            float topDeadZonePx = 60f * density;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchStartX = event.getX();
                touchStartY = event.getY();
            }

            // Ignore touches starting in the top dead zone (notification shade area)
            if (touchStartY >= 0 && touchStartY < topDeadZonePx) {
                holdSpeedHandler.removeCallbacksAndMessages(null);
                hidePlaqueHandler.removeCallbacksAndMessages(null);
                GesturePlaqueAnimationHelper.hideImmediate(gestureIndicatorPlaque);
                GesturePlaqueAnimationHelper.hideImmediate(seekPreviewPlaque);
                return false;
            }

            gestureDetector.onTouchEvent(event);

            int width = playerView.getWidth();
            int height = playerView.getHeight();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isTouching = true;
                    isHorizontalSeeking = false;
                    isVerticalBrightness = false;
                    isVerticalVolume = false;
                    isHoldingSpeed = false;

                    float curB = activity.getWindow().getAttributes().screenBrightness;
                    initialBrightness = curB < 0 ? 0.5f : curB;
                    initialVolume = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;

                    // Hold for 2x speed
                    holdSpeedHandler.postDelayed(() -> {
                        if (isTouching && !isHorizontalSeeking && !isVerticalBrightness && !isVerticalVolume && touchStartY >= topDeadZonePx) {
                            isHoldingSpeed = true;
                            holdStartX = touchStartX;
                            callback.onHoldSpeed(true);
                            if (holdSpeedToast != null) {
                                holdSpeedToast.setText("2x");
                            }
                            GesturePlaqueAnimationHelper.show(holdSpeedPlaque);
                        }
                    }, 500);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isHoldingSpeed) {
                        float holdDx = event.getX() - holdStartX;
                        float adjustedSpeed = 2.0f + (holdDx / 200f);
                        adjustedSpeed = Math.max(1.0f, Math.min(4.0f, Math.round(adjustedSpeed * 10f) / 10f));
                        callback.onHoldSpeedChanged(adjustedSpeed);
                        if (holdSpeedToast != null) {
                            holdSpeedToast.setText(String.format(Locale.US, "%.1fx", adjustedSpeed));
                        }
                        return true;
                    }

                    float dx = event.getX() - touchStartX;
                    float dy = event.getY() - touchStartY;

                    if (!isHorizontalSeeking && !isVerticalBrightness && !isVerticalVolume) {
                        if (Math.abs(dx) > 30 && Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSeeking = true;
                            holdSpeedHandler.removeCallbacksAndMessages(null);
                            seekTargetPosition = callback.getCurrentPosition();
                        } else if (Math.abs(dy) > 30) {
                            holdSpeedHandler.removeCallbacksAndMessages(null);
                            if (touchStartX < width / 2.0f) {
                                isVerticalBrightness = true;
                            } else {
                                isVerticalVolume = true;
                            }
                        }
                    }

                    if (isHorizontalSeeking) {
                        long duration = callback.getDuration();
                        if (duration > 0) {
                            long deltaMs = (long) (dx / width * 90000L); // 90 sec range
                            long previewPos = Math.max(0, Math.min(duration, seekTargetPosition + deltaMs));
                            showSeekPreview(previewPos, duration, deltaMs);
                        }
                    } else if (isVerticalBrightness) {
                        float deltaRatio = -dy / height;
                        float targetBrightness = Math.max(0.01f, Math.min(1.0f, initialBrightness + deltaRatio));
                        setBrightness(targetBrightness);
                    } else if (isVerticalVolume) {
                        float deltaRatio = -dy / height;
                        int targetVolume = Math.max(0, Math.min(maxVolume, Math.round(initialVolume + deltaRatio * maxVolume)));
                        setVolume(targetVolume);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdSpeedHandler.removeCallbacksAndMessages(null);
                    if (isHoldingSpeed) {
                        isHoldingSpeed = false;
                        callback.onHoldSpeed(false);
                        GesturePlaqueAnimationHelper.hide(holdSpeedPlaque);
                    }

                    if (isHorizontalSeeking) {
                        float finalDx = event.getX() - touchStartX;
                        long deltaMs = (long) (finalDx / width * 90000L);
                        long target = Math.max(0, Math.min(callback.getDuration(), seekTargetPosition + deltaMs));
                        callback.onSeekCommit(target);
                        GesturePlaqueAnimationHelper.hide(seekPreviewPlaque);
                    }

                    isTouching = false;
                    isHorizontalSeeking = false;
                    isVerticalBrightness = false;
                    isVerticalVolume = false;
                    touchStartX = -1f;
                    touchStartY = -1f;

                    hidePlaqueHandler.removeCallbacks(hideGestureIndicatorRunnable);
                    hidePlaqueHandler.postDelayed(hideGestureIndicatorRunnable, 800);
                    return true;
            }
            return false;
        });
    }

    private void showSeekPreview(long targetPos, long duration, long deltaMs) {
        if (seekPreviewPlaque == null) return;
        long offsetSec = deltaMs / 1000;
        String offsetStr = (offsetSec >= 0 ? "+" : "") + offsetSec + " сек";
        String currentFormatted = formatDuration(targetPos);
        String durationFormatted = formatDuration(duration);

        if (seekPreviewOffset != null) {
            seekPreviewOffset.setText(offsetStr);
        }
        if (seekPreviewTime != null) {
            seekPreviewTime.setText(currentFormatted);
        }
        if (seekPreviewDuration != null) {
            seekPreviewDuration.setText(" / " + durationFormatted);
        }
        if (seekPreviewIcon != null) {
            seekPreviewIcon.setRotation(deltaMs >= 0 ? 0f : 180f);
        }
        if (seekPreviewPlaque instanceof TextView) {
            ((TextView) seekPreviewPlaque).setText(String.format(Locale.getDefault(), "%s (%s / %s)",
                    offsetStr, currentFormatted, durationFormatted));
        }
        GesturePlaqueAnimationHelper.show(seekPreviewPlaque);
    }

    private void setBrightness(float brightness) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = brightness;
        activity.getWindow().setAttributes(lp);

        int percent = Math.round(brightness * 100);
        showGesturePlaque(R.drawable.ic_brightness, percent);
    }

    private void setVolume(int volume) {
        if (audioManager == null || maxVolume <= 0) return;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);

        int percent = (volume * 100) / maxVolume;
        showGesturePlaque(R.drawable.ic_volume, percent);
    }

    private void showGesturePlaque(int iconRes, int percent) {
        if (gestureIndicatorPlaque == null) return;
        hidePlaqueHandler.removeCallbacks(hideGestureIndicatorRunnable);
        if (gestureIndicatorIcon != null) gestureIndicatorIcon.setImageResource(iconRes);
        if (gestureIndicatorProgress != null) gestureIndicatorProgress.setProgress(percent, false);
        if (gestureIndicatorText != null) gestureIndicatorText.setText(percent + "%");
        GesturePlaqueAnimationHelper.show(gestureIndicatorPlaque);
    }

    public void showDoubleTapSkipIndicator(boolean isForward, int seconds) {
        LinearLayout indicator = isForward ? skipIndicatorRight : skipIndicatorLeft;
        TextView text = isForward ? skipTextRight : skipTextLeft;
        if (indicator == null || text == null) return;

        text.setText((isForward ? "+" : "-") + seconds + " сек");
        hidePlaqueHandler.removeCallbacks(isForward ? hideRightSkipRunnable : hideLeftSkipRunnable);
        GesturePlaqueAnimationHelper.show(indicator);

        Runnable hideRunnable = () -> GesturePlaqueAnimationHelper.hide(indicator);
        if (isForward) {
            hideRightSkipRunnable = hideRunnable;
        } else {
            hideLeftSkipRunnable = hideRunnable;
        }
        hidePlaqueHandler.postDelayed(hideRunnable, 650);
    }

    private String formatDuration(long millis) {
        long totalSecs = Math.max(0, millis / 1000);
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", mins, secs);
        }
    }
}
