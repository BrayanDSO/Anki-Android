/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.commit
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NavigationBarActivity
import com.ichi2.anki.R
import com.ichi2.anki.utils.getTimestamp
import com.ichi2.libanki.utils.TimeManager

class StatisticsActivity : NavigationBarActivity(), ServerProvider {
    private lateinit var ankiServer: AnkiServer
    private val fragment = StatisticsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.statistics_activity)

        ankiServer = AnkiServer(this)
        ankiServer.start()

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_export_stats)?.title = CollectionManager.TR.statisticsSavePdf()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_export_stats) {
            exportWebViewContentAsPDF()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Prepares and initiates a printing task for the content displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document.
     **/
    private fun exportWebViewContentAsPDF() {
        val printManager = getSystemService(this, PrintManager::class.java)
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter = fragment.webView.createPrintDocumentAdapter(jobName)
        printManager?.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
    }

    override fun baseUrl(): String {
        return ankiServer.baseUrl()
    }
}

interface ServerProvider {
    fun baseUrl(): String
}

class StatisticsFragment : PageFragment() {
    override val title
        get() = resources.getString(R.string.statistics)
    override val pageName = "graphs"
    override var webViewClient = PageWebViewClient()
    override var webChromeClient = PageChromeClient()
}
