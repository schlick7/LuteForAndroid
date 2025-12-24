package com.example.luteforandroidv2.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.animation.Transformation
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.example.luteforandroidv2.R

class CollapsibleSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerLayout: LinearLayout
    private val sectionTitle: TextView
    private val sectionArrow: ImageView
    private val sectionContent: LinearLayout

    private var isExpanded = false
    private var animationDuration = 300L

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.collapsible_section, this, true)

        headerLayout = findViewById(R.id.section_header)
        sectionTitle = findViewById(R.id.section_title)
        sectionArrow = findViewById(R.id.section_arrow)
        sectionContent = findViewById(R.id.section_content)

        headerLayout.setOnClickListener {
            toggle()
        }
    }

    fun setTitle(title: String) {
        sectionTitle.text = title
    }

    fun setContent(contentView: View) {
        sectionContent.removeAllViews()
        sectionContent.addView(contentView)
    }

    fun expand() {
        if (!isExpanded) {
            isExpanded = true
            sectionContent.visibility = View.VISIBLE
            rotateArrow(0f, 180f)
            expandSection()
        }
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            rotateArrow(180f, 0f)
            collapseSection()
        }
    }

    fun toggle() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    private fun rotateArrow(from: Float, to: Float) {
        val rotateAnimation = RotateAnimation(
            from,
            to,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        rotateAnimation.duration = animationDuration
        rotateAnimation.fillAfter = true
        sectionArrow.startAnimation(rotateAnimation)
    }

    private fun expandSection() {
        val targetHeight = measureContentHeight()
        sectionContent.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val actualHeight = sectionContent.measuredHeight

        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                sectionContent.layoutParams.height = if (interpolatedTime == 1f) {
                    LinearLayout.LayoutParams.WRAP_CONTENT
                } else {
                    (actualHeight * interpolatedTime).toInt()
                }
                sectionContent.requestLayout()
            }

            override fun willChangeBounds(): Boolean = true
        }
        animation.duration = animationDuration
        sectionContent.startAnimation(animation)
    }

    private fun collapseSection() {
        val initialHeight = sectionContent.measuredHeight
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                if (interpolatedTime == 1f) {
                    sectionContent.visibility = View.GONE
                } else {
                    sectionContent.layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                    sectionContent.requestLayout()
                }
            }

            override fun willChangeBounds(): Boolean = true
        }
        animation.duration = animationDuration
        sectionContent.startAnimation(animation)
    }

    private fun measureContentHeight(): Int {
        var totalHeight = 0
        for (i in 0 until sectionContent.childCount) {
            val child = sectionContent.getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            totalHeight += child.measuredHeight
        }
        return totalHeight
    }
}
