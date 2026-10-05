package io.legado.app.help.update

import org.junit.Assert.*
import org.junit.Test

class RqtReleasePolicyTest {
    @Test fun comparesNumericVersions() {
        assertTrue(RqtReleasePolicy.compareVersions("3.26.1005.10", "3.26.1005.2") > 0)
        assertTrue(RqtReleasePolicy.compareVersions("v3.26.1005.1", "3.26.1004.1") > 0)
        assertEquals(0, RqtReleasePolicy.compareVersions("3.26.1005.1", "3.26.1005.1"))
    }
    @Test fun onlyAcceptsRqtReleaseApks() {
        assertTrue(RqtReleasePolicy.isReleaseApk("legado-R-qt_app_3.26.1005.1_12027.apk"))
        assertFalse(RqtReleasePolicy.isReleaseApk("legado_app_3.26.1005_99999.apk"))
        assertFalse(RqtReleasePolicy.isReleaseApk("legado-R-qt-debug.apk"))
        assertFalse(RqtReleasePolicy.isReleaseApk("legado-R-qt.zip"))
    }
    @Test fun readsVersionCodeFromPublishedFileName() {
        val info = AppReleaseInfo(AppVariant.OFFICIAL, 0L, "", "legado-R-qt_app_3.26.1005.1_12027.apk", "", "")
        assertEquals(12027L, info.versionCode)
        assertEquals("3.26.1005.1", info.versionName)
    }
}
