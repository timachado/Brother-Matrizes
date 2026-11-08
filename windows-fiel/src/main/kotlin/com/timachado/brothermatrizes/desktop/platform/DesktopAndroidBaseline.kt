package com.timachado.brothermatrizes.desktop.platform

/**
 * Version of the official Android source compiled into this Windows branch.
 *
 * This is NOT a claim of full functional parity. The Windows release remains
 * QA until every Android feature passes its own desktop acceptance tests.
 * DesktopAndroidBaselineTest refuses mismatches against app/build.gradle.kts.
 */
internal object DesktopAndroidBaseline {
    const val VERSION_CODE: Int = 196
    const val VERSION_NAME: String = "1.11.9"
    const val WINDOWS_QA_LABEL: String = "1.11.9 (Windows QA)"
}
