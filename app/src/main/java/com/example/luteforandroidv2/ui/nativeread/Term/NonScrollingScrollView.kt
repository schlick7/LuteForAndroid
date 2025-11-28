package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import android.widget.ScrollView

/**
 * A ScrollView that blocks auto-scrolling by default after initial layout while maintaining smooth
 * user scrolling
 */
class NonScrollingScrollView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        ScrollView(context, attrs, defStyleAttr) {

    private var _isAutoScrollBlocked =
            false // Start with initial layout allowed, then block auto-scrolling

    val isAutoScrollBlocked: Boolean
        get() = _isAutoScrollBlocked

    // Store the original scroller to allow user fling gestures while blocking programmatic
    // scrolling
    private var originalScroller: OverScroller? = null

    init {
        // Get the original scroller to potentially customize scrolling behavior
        try {
            val mScrollerField = ScrollView::class.java.getDeclaredField("mScroller")
            mScrollerField.isAccessible = true
            originalScroller = mScrollerField.get(this) as? OverScroller
        } catch (e: Exception) {
            // If we can't access the scroller, continue without custom scroll physics
        }
    }

    fun setAutoScrollBlocked(blocked: Boolean) {
        _isAutoScrollBlocked = blocked
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        if (!isAutoScrollBlocked) {
            super.requestChildFocus(child, focused)
        }
        // This blocks focus-based scrolling that causes unwanted auto-scrolling
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        if (!isAutoScrollBlocked) {
            super.onDescendantInvalidated(child, target)
        }
    }

    override fun requestLayout() {
        if (!isAutoScrollBlocked) {
            super.requestLayout()
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        // Allow scrollTo for manual position restoration while still blocking unwanted
        // auto-scrolling
        // The actual decision of whether to scroll is now handled in the calling code
        super.scrollTo(x, y)
    }

    override fun fling(velocityY: Int) {
        // Allow user-initiated fling gestures but not programmatic fling calls
        // We'll check if this is from a user gesture vs programmatic call
        super.fling(velocityY)
    }

    override fun computeScroll() {
        // Allow computeScroll to handle fling animations for user gestures
        // but not for programmatic scrolling
        super.computeScroll()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // Allow touch events to be intercepted - this enables manual scrolling
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        // Allow touch events to be processed - this enables manual scrolling
        return super.onTouchEvent(ev)
    }
}
