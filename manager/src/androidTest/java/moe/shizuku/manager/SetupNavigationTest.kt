package moe.shizuku.manager

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Basic visible UI smoke test; no claim of physical e-ink waveform verification. */
@RunWith(AndroidJUnit4::class)
class SetupNavigationTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test fun initializationShowsControls() {
        onView(withId(R.id.inkServiceTitle)).check(matches(isDisplayed()))
        onView(withId(R.id.inkToggleButton)).check(matches(isDisplayed()))
        onView(withId(R.id.inkEffectCard)).check(matches(isDisplayed()))
        onView(withId(R.id.inkBottomNav)).check(matches(isDisplayed()))
    }

    @Test fun animationTabShowsBothModes() {
        onView(withId(R.id.inkNavEffects)).perform(click())
        onView(withText("拂风纹")).check(matches(isDisplayed()))
        onView(withText("垂幕纹")).check(matches(isDisplayed()))
    }
}
