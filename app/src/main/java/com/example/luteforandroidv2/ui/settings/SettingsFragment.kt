package com.example.luteforandroidv2.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.luteforandroidv2.databinding.FragmentSettingsBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.math.abs

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding!!
    private var shouldAutoSwitchToAppSettings = false
    private var startX = 0f
    private var startY = 0f
    private val SWIPE_THRESHOLD = 50 // Minimum distance to be considered a swipe
    private val SWIPE_VELOCITY_THRESHOLD = 100 // Minimum velocity to be considered a swipe

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if we should auto-switch to app settings
        val sharedPref =
                requireContext().getSharedPreferences("navigation_flags", Context.MODE_PRIVATE)
        shouldAutoSwitchToAppSettings = sharedPref.getBoolean("auto_switch_to_app_settings", false)

        setupViewPager()

        // If we should auto-switch, set the initial tab directly instead of switching after
        // creation
        if (shouldAutoSwitchToAppSettings) {
            // Set the initial tab to App Settings (index 0) before the ViewPager is fully rendered
            binding.viewPager.currentItem = 0

            // Clear the flag so we don't auto-switch again
            with(sharedPref.edit()) {
                putBoolean("auto_switch_to_app_settings", false)
                apply()
            }
        }

        // Add touch interceptor to improve swipe sensitivity
        setupTouchInterceptor()
    }

    private fun setupTouchInterceptor() {
        // Add a touch listener to the ViewPager to improve swipe sensitivity
        binding.viewPager.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val endX = event.x
                    val endY = event.y
                    val diffX = startX - endX
                    val diffY = startY - endY

                    // If vertical movement is significantly greater than horizontal,
                    // disable ViewPager's touch handling to allow vertical scrolling
                    if (abs(diffY) > abs(diffX) * 1.5) {
                        binding.viewPager.isUserInputEnabled = false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Re-enable ViewPager touch handling
                    binding.viewPager.isUserInputEnabled = true
                }
            }
            false // Allow the event to be handled by ViewPager2 normally
        }
    }

    private fun setupViewPager() {
        val adapter = SettingsPagerAdapter(childFragmentManager, lifecycle)
        binding.viewPager.adapter = adapter

        // If we should auto-switch to app settings, set the initial page before attaching TabLayout
        if (shouldAutoSwitchToAppSettings) {
            binding.viewPager.currentItem = 0 // App Settings tab
        }

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                    when (position) {
                        0 -> tab.text = "App Settings"
                        1 -> tab.text = "Lute Settings"
                        2 -> tab.text = "Language Settings"
                    }
                }
                .attach()

        // Add page change listener to refresh language selection when AppSettings tab is shown
        binding.viewPager.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        if (position == 0) { // App Settings tab
                            // Refresh language selection by fetching from server
                            refreshAppSettingsLanguageSelection()
                        }
                    }
                }
        )

        // Reduce the swipe sensitivity by adjusting the touch slop
        try {
            val recyclerView = binding.viewPager.getChildAt(0)
            if (recyclerView is androidx.recyclerview.widget.RecyclerView) {
                val touchSlopField =
                        androidx.recyclerview.widget.RecyclerView::class.java.getDeclaredField(
                                "mTouchSlop"
                        )
                touchSlopField.isAccessible = true
                val touchSlop = touchSlopField.get(recyclerView) as Int
                touchSlopField.set(
                        recyclerView,
                        touchSlop * 3
                ) // Double the touch slop to make swiping less sensitive
            }
        } catch (e: Exception) {
            // Ignore if we can't access the field
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Method to refresh language selection in AppSettingsFragment
    fun refreshAppSettingsLanguageSelection() {
        try {
            // Get the AppSettingsFragment if it exists
            val appSettingsFragment =
                    childFragmentManager.findFragmentByTag("f2") as? AppSettingsFragment
            appSettingsFragment?.setupLanguageSelection()
        } catch (e: Exception) {
            android.util.Log.e(
                    "SettingsFragment",
                    "Error refreshing AppSettings language selection",
                    e
            )
        }
    }

    // ViewPager adapter
    private inner class SettingsPagerAdapter(
            fragmentManager: FragmentManager,
            lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AppSettingsFragment()
                1 -> LuteSettingsFragment()
                2 -> LanguageSettingsFragment()
                else -> AppSettingsFragment()
            }
        }
    }
}
