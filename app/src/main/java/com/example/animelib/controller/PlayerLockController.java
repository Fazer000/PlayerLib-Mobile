package com.example.animelib.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.animelib.util.CustomToast;

/**
 * Контроллер блокировки экрана во время воспроизведения видео (Lock Screen).
 */
public class PlayerLockController {

    public interface OnLockStateChangeListener {
        void onLockStateChanged(boolean isLocked);
    }

    private final Context context;
    private final FrameLayout layoutLockedOverlay;
    private final LinearLayout lockedStatusBadge;
    private View swipeUnlockPill;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isScreenLocked = false;
    private OnLockStateChangeListener lockStateChangeListener;

    private final Runnable hideLockUiRunnable = new Runnable() {
        @Override
        public void run() {
            if (lockedStatusBadge != null) {
                lockedStatusBadge.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> lockedStatusBadge.setVisibility(View.GONE))
                        .start();
            }
            if (swipeUnlockPill != null) {
                swipeUnlockPill.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> swipeUnlockPill.setVisibility(View.GONE))
                        .start();
            }
        }
    };

    public PlayerLockController(@NonNull Context context,
                                @Nullable FrameLayout layoutLockedOverlay,
                                @Nullable LinearLayout lockedStatusBadge) {
        this.context = context;
        this.layoutLockedOverlay = layoutLockedOverlay;
        this.lockedStatusBadge = lockedStatusBadge;

        if (layoutLockedOverlay != null) {
            this.swipeUnlockPill = layoutLockedOverlay.findViewById(com.example.animelib.R.id.swipeUnlockPill);
        }

        setupLockOverlayGestures();
    }

    public void setOnLockStateChangeListener(OnLockStateChangeListener listener) {
        this.lockStateChangeListener = listener;
    }

    public boolean isLocked() {
        return isScreenLocked;
    }

    private void showLockUiTemp(long delayMs) {
        handler.removeCallbacks(hideLockUiRunnable);
        if (lockedStatusBadge != null) {
            lockedStatusBadge.animate().cancel();
            lockedStatusBadge.setAlpha(1f);
            lockedStatusBadge.setVisibility(View.VISIBLE);
        }
        if (swipeUnlockPill != null) {
            swipeUnlockPill.animate().cancel();
            swipeUnlockPill.setAlpha(1f);
            swipeUnlockPill.setVisibility(View.VISIBLE);
        }
        handler.postDelayed(hideLockUiRunnable, delayMs);
    }

    public void lock() {
        isScreenLocked = true;
        if (layoutLockedOverlay != null) {
            layoutLockedOverlay.setVisibility(View.VISIBLE);
        }
        if (swipeUnlockPill != null) {
            swipeUnlockPill.setTranslationY(0f);
        }
        showLockUiTemp(2500);
        CustomToast.showInfo(context, "Экран заблокирован");

        if (lockStateChangeListener != null) {
            lockStateChangeListener.onLockStateChanged(true);
        }
    }

    public void unlock() {
        isScreenLocked = false;
        handler.removeCallbacks(hideLockUiRunnable);
        if (lockedStatusBadge != null) {
            lockedStatusBadge.animate().cancel();
            lockedStatusBadge.setAlpha(1f);
        }
        if (swipeUnlockPill != null) {
            swipeUnlockPill.animate().cancel();
            swipeUnlockPill.setTranslationY(0f);
            swipeUnlockPill.setAlpha(1f);
        }
        if (layoutLockedOverlay != null) {
            layoutLockedOverlay.setVisibility(View.GONE);
        }
        CustomToast.showSuccess(context, "Экран разблокирован");

        if (lockStateChangeListener != null) {
            lockStateChangeListener.onLockStateChanged(false);
        }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (lockedStatusBadge != null) lockedStatusBadge.animate().cancel();
        if (swipeUnlockPill != null) swipeUnlockPill.animate().cancel();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupLockOverlayGestures() {
        if (layoutLockedOverlay == null) return;

        layoutLockedOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float downY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getRawY();
                        showLockUiTemp(2500);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        handler.removeCallbacks(hideLockUiRunnable);
                        if (swipeUnlockPill != null) {
                            swipeUnlockPill.animate().cancel();
                            swipeUnlockPill.setAlpha(1f);
                            swipeUnlockPill.setVisibility(View.VISIBLE);
                            float dy = downY - event.getRawY();
                            if (dy > 0) {
                                swipeUnlockPill.setTranslationY(-dy);
                            } else {
                                swipeUnlockPill.setTranslationY(0f);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float deltaY = downY - event.getRawY();
                        if (deltaY > 120) {
                            unlock();
                        } else {
                            if (swipeUnlockPill != null) {
                                swipeUnlockPill.animate().translationY(0f).setDuration(220).start();
                            }
                            showLockUiTemp(2500);
                        }
                        return true;
                }
                return true;
            }
        });
    }
}
