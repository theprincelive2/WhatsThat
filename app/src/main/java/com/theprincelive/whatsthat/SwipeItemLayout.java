package com.theprincelive.whatsthat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SwipeItemLayout extends FrameLayout {
    private static SwipeItemLayout activeLayout;

    private View foregroundView;
    private LinearLayout leftActionsLayout;
    private LinearLayout rightActionsLayout;

    private float startX;
    private float startY;
    private float initialTranslationX;
    private boolean isSwiping = false;
    private boolean preventSwipe = false;
    private int touchSlop;
    private VelocityTracker velocityTracker;

    private int leftActionsWidth = 0;
    private int rightActionsWidth = 0;
    private float currentTranslationX = 0;

    private OnSwipeActionListener actionListener;
    private SavedMessage messageItem;
    private boolean swipeEnabled = true;

    public interface OnSwipeActionListener {
        void onDelete(SavedMessage msg);
        void onHide(SavedMessage msg);
        void onToggleRead(SavedMessage msg);
    }

    public SwipeItemLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setSwipeEnabled(boolean enabled) {
        this.swipeEnabled = enabled;
    }

    public void setUpViews(View foreground, SavedMessage msg, OnSwipeActionListener listener) {
        this.messageItem = msg;
        this.actionListener = listener;

        // Reset previous state
        removeAllViews();
        currentTranslationX = 0;
        isSwiping = false;

        this.foregroundView = foreground;
        this.foregroundView.setTranslationX(0);

        // Create Background Action Layouts
        createLeftActions();
        createRightActions();

        // Add views in correct Z-order (backgrounds first, then foreground)
        if (leftActionsLayout != null) {
            addView(leftActionsLayout, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START));
            leftActionsLayout.setVisibility(View.INVISIBLE);
        }
        if (rightActionsLayout != null) {
            addView(rightActionsLayout, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END));
            rightActionsLayout.setVisibility(View.INVISIBLE);
        }
        addView(foregroundView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void createLeftActions() {
        leftActionsLayout = new LinearLayout(getContext());
        leftActionsLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView hideBtn = new TextView(getContext());
        hideBtn.setText("Hide");
        hideBtn.setTextColor(Color.WHITE);
        hideBtn.setTextSize(14);
        hideBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hideBtn.setGravity(Gravity.CENTER);
        hideBtn.setBackgroundColor(Color.parseColor("#5856D6"));
        int btnWidth = dp(76);
        hideBtn.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onHide(messageItem);
            snapTo(0, null);
        });

        TextView readBtn = new TextView(getContext());
        boolean isUnread = messageItem.unreadCount > 0 || !messageItem.read;
        readBtn.setText(isUnread ? "Read" : "Unread");
        readBtn.setTextColor(Color.WHITE);
        readBtn.setTextSize(14);
        readBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        readBtn.setGravity(Gravity.CENTER);
        readBtn.setBackgroundColor(Color.parseColor(isUnread ? "#34C759" : "#8E8E93"));
        readBtn.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onToggleRead(messageItem);
            snapTo(0, null);
        });

        leftActionsLayout.addView(hideBtn, new LinearLayout.LayoutParams(btnWidth, LayoutParams.MATCH_PARENT));
        leftActionsLayout.addView(readBtn, new LinearLayout.LayoutParams(btnWidth, LayoutParams.MATCH_PARENT));
        leftActionsWidth = btnWidth * 2;
    }

    private void createRightActions() {
        rightActionsLayout = new LinearLayout(getContext());
        rightActionsLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView deleteBtn = new TextView(getContext());
        deleteBtn.setText("Delete");
        deleteBtn.setTextColor(Color.WHITE);
        deleteBtn.setTextSize(14);
        deleteBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        deleteBtn.setGravity(Gravity.CENTER);
        deleteBtn.setBackgroundColor(Color.parseColor("#FF3B30"));
        int btnWidth = dp(80);
        deleteBtn.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(messageItem);
            snapTo(0, null);
        });

        rightActionsLayout.addView(deleteBtn, new LinearLayout.LayoutParams(btnWidth, LayoutParams.MATCH_PARENT));
        rightActionsWidth = btnWidth;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!swipeEnabled) return false;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                initialTranslationX = currentTranslationX;
                isSwiping = false;
                preventSwipe = false;

                // Close other swiped items on touch
                if (activeLayout != null) {
                    if (activeLayout != this) {
                        activeLayout.snapTo(0, null);
                        preventSwipe = true;
                        return true; // Intercept and consume touch to dismiss the other open item
                    } else {
                        // Touch is on the open item itself
                        boolean touchOnForeground = true;
                        if (currentTranslationX > 0) {
                            touchOnForeground = ev.getX() >= currentTranslationX;
                        } else if (currentTranslationX < 0) {
                            touchOnForeground = ev.getX() <= getWidth() + currentTranslationX;
                        }

                        if (touchOnForeground) {
                            // Touch is on the foreground, intercept to close it
                            return true;
                        } else {
                            // Touch is on background actions, do NOT intercept, let the buttons handle click instantly
                            preventSwipe = true;
                        }
                    }
                }

                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                if (preventSwipe) return false;
                float dx = ev.getX() - startX;
                float dy = ev.getY() - startY;
                // Use touchSlop * 2 to reduce accidental swipe triggers on fast taps
                if (Math.abs(dx) > touchSlop * 2 && Math.abs(dx) > Math.abs(dy)) {
                    isSwiping = true;
                    if (activeLayout != null && activeLayout != this) {
                        activeLayout.snapTo(0, null);
                    }
                    activeLayout = this;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isSwiping = false;
                break;
        }
        return isSwiping;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!swipeEnabled) return false;
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                initialTranslationX = currentTranslationX;
                preventSwipe = false;
                if (activeLayout != null) {
                    if (activeLayout != this) {
                        preventSwipe = true;
                    } else {
                        boolean touchOnForeground = true;
                        if (currentTranslationX > 0) {
                            touchOnForeground = ev.getX() >= currentTranslationX;
                        } else if (currentTranslationX < 0) {
                            touchOnForeground = ev.getX() <= getWidth() + currentTranslationX;
                        }
                        if (!touchOnForeground) {
                            preventSwipe = true;
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (preventSwipe) return true;
                float dx = ev.getX() - startX;
                float dy = ev.getY() - startY;

                if (!isSwiping) {
                    // Use touchSlop * 2 to reduce sensitivity
                    if (Math.abs(dx) > touchSlop * 2 && Math.abs(dx) > Math.abs(dy)) {
                        isSwiping = true;
                        startX = ev.getX(); // Reset to avoid jump
                        initialTranslationX = currentTranslationX;
                        if (activeLayout != null && activeLayout != this) {
                            activeLayout.snapTo(0, null);
                        }
                        activeLayout = this;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }

                if (isSwiping) {
                    float newTranslationX = initialTranslationX + (ev.getX() - startX);

                    // Restrict swipes
                    if (newTranslationX > 0) {
                        // Swiping right, reveal left actions
                        if (leftActionsLayout != null) {
                            leftActionsLayout.setVisibility(View.VISIBLE);
                        }
                        if (rightActionsLayout != null) {
                            rightActionsLayout.setVisibility(View.INVISIBLE);
                        }
                        if (newTranslationX > leftActionsWidth + dp(20)) {
                            newTranslationX = leftActionsWidth + dp(20) + (newTranslationX - leftActionsWidth - dp(20)) * 0.3f;
                        }
                    } else if (newTranslationX < 0) {
                        // Swiping left, reveal right actions
                        if (rightActionsLayout != null) {
                            rightActionsLayout.setVisibility(View.VISIBLE);
                        }
                        if (leftActionsLayout != null) {
                            leftActionsLayout.setVisibility(View.INVISIBLE);
                        }
                        if (newTranslationX < -rightActionsWidth - dp(20)) {
                            newTranslationX = -rightActionsWidth - dp(20) + (newTranslationX + rightActionsWidth + dp(20)) * 0.3f;
                        }
                    }

                    currentTranslationX = newTranslationX;
                    foregroundView.setTranslationX(currentTranslationX);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float targetTranslationX = 0;
                if (isSwiping && !preventSwipe) {
                    velocityTracker.computeCurrentVelocity(1000);
                    float xVelocity = velocityTracker.getXVelocity();
                    float minSwipeDistance = dp(24); // Minimum swipe distance required to snap open

                    if (currentTranslationX > 0) {
                        // Swiped right
                        if (currentTranslationX >= minSwipeDistance && (xVelocity > 500 || currentTranslationX > leftActionsWidth * 0.5f)) {
                            targetTranslationX = leftActionsWidth;
                        }
                    } else if (currentTranslationX < 0) {
                        // Swiped left
                        if (Math.abs(currentTranslationX) >= minSwipeDistance && (xVelocity < -500 || currentTranslationX < -rightActionsWidth * 0.5f)) {
                            targetTranslationX = -rightActionsWidth;
                        }
                    }
                } else {
                    // Tap event. If it was open, close it.
                    targetTranslationX = 0;
                }

                isSwiping = false;
                preventSwipe = false;
                snapTo(targetTranslationX, null);
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                break;
        }
        return true;
    }

    private void snapTo(float targetX, Runnable onEnd) {
        if (targetX == 0 && activeLayout == this) {
            activeLayout = null;
        } else if (targetX != 0) {
            if (activeLayout != null && activeLayout != this) {
                activeLayout.snapTo(0, null);
            }
            activeLayout = this;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(currentTranslationX, targetX);
        animator.setDuration(250);
        animator.addUpdateListener(animation -> {
            currentTranslationX = (float) animation.getAnimatedValue();
            foregroundView.setTranslationX(currentTranslationX);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentTranslationX == 0) {
                    if (leftActionsLayout != null) leftActionsLayout.setVisibility(View.INVISIBLE);
                    if (rightActionsLayout != null) rightActionsLayout.setVisibility(View.INVISIBLE);
                }
                if (onEnd != null) onEnd.run();
            }
        });
        animator.start();
    }

    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
