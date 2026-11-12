package io.github.abuelgasimmohamedsaeed.focus;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Focus's Accessibility service. Today it only lets Focus draw the time line at the very top of the
 * screen, over the status bar; it reads nothing on screen. (App Time's block screen will use it later.)
 */
public class FocusAccessibility extends AccessibilityService {
    static volatile FocusAccessibility instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
        TopLine.reset();
        Sit s = Sit.load(this);
        if (s != null) Engine.refresh(this, s);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        TopLine.reset();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        TopLine.reset();
        super.onDestroy();
    }
}
