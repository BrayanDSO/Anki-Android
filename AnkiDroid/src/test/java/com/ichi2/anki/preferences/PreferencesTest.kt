/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import android.app.LocaleConfig
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.preferences.Preferences.Companion.getDayOffset
import com.ichi2.preferences.HeaderPreference
import com.ichi2.testutils.getJavaMethodAsAccessible
import com.ichi2.utils.LanguageUtil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertContentEquals

@RunWith(AndroidJUnit4::class)
class PreferencesTest : RobolectricTest() {
    private lateinit var preferences: Preferences

    @Before
    override fun setUp() {
        super.setUp()
        preferences = Preferences()
        val attachBaseContext = getJavaMethodAsAccessible(
            AppCompatActivity::class.java,
            "attachBaseContext",
            Context::class.java
        )
        attachBaseContext.invoke(preferences, targetContext)
    }

    @Test
    fun testDayOffsetExhaustive() {
        for (i in 0..23) {
            preferences.setDayOffset(i)
            assertThat(getDayOffset(col), equalTo(i))
        }
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun testDayOffsetExhaustiveV2() {
        col.changeSchedulerVer(2)
        for (i in 0..23) {
            preferences.setDayOffset(i)
            assertThat(getDayOffset(col), equalTo(i))
        }
    }

    /** checks if any of the Preferences fragments throws while being created */
    @Test
    fun fragmentsDoNotThrowOnCreation() {
        val activityScenario = ActivityScenario.launch(Preferences::class.java)

        activityScenario.onActivity { activity ->
            PreferenceUtils.getAllPreferencesFragments(activity).forEach {
                activity.supportFragmentManager.commitNow {
                    add(R.id.settings_container, it)
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun buildHeaderSummary_RTL_Test() {
        assertThat(HeaderPreference.buildHeaderSummary("حساب أنكي ويب", "مزامنة تلقائية"), equalTo("مزامنة تلقائية • حساب أنكي ويب"))
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun setDayOffsetSetsConfig() {
        col.changeSchedulerVer(2)
        val offset = getDayOffset(col)
        assertThat("Default offset should be 4", offset, equalTo(4))
        preferences.setDayOffset(2)
        assertThat("rollover config should be set to new value", col.get_config("rollover", 4.toInt()), equalTo(2))
    }

    @Test
    // guarantee the same languages before and after Android 13
    fun `locale_config values are the same of APP_LANGUAGES`() {
        val xrp = targetContext.resources.getXml(R.xml.locale_config).apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
        }
        val locales = mutableListOf<String>()
        while (xrp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xrp.eventType == XmlPullParser.START_TAG && xrp.name == LocaleConfig.TAG_LOCALE) {
                locales.add(xrp.getAttributeValue(AnkiDroidApp.ANDROID_NAMESPACE, "name"))
            }
            xrp.next()
        }
        assertContentEquals(locales.toTypedArray(), LanguageUtil.APP_LANGUAGES)
    }
}
