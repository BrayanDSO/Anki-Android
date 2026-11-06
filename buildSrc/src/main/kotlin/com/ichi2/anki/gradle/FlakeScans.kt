// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.tasks.testing.Test

/** GitHub Actions events which repeat the test suite to find flaky tests. */
private val flakeScanEvents = setOf("schedule", "workflow_dispatch")

/**
 * Executes tests during flake scans instead of reusing up-to-date or cached results:
 * a reused result would make every iteration of the scan report the same outcome.
 */
fun Test.alwaysRunDuringFlakeScans(gitHubEventName: String?) {
    if (gitHubEventName !in flakeScanEvents) return
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
