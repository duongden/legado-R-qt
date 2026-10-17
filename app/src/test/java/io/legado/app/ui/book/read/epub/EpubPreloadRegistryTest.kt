package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubPreloadRegistryTest {
    @Test fun pendingNativeResourcesCountAgainstTheSameBudgetAsReadyOnes() {
        val registry = EpubPreloadRegistry<Any, Any>({ 2 }) { fail("unexpected eviction") }
        registry.loading["loading"] = Any()
        registry.scheduled["scheduled"] = Any()
        assertFalse(registry.hasCapacity())
        assertTrue(registry.owns("loading"))
        assertTrue(registry.owns("scheduled"))
        assertFalse(registry.owns("missing"))
    }

    @Test fun invalidPreloadIsRemovedAndDestroyedOnlyOnce() {
        val destroyed = mutableListOf<Any>()
        val registry = EpubPreloadRegistry<Any, Any>({ 2 }, destroyed::add)
        val view = Any()
        registry.loading["chapter"] = view
        assertNull(registry.takeLoading("chapter") { false })
        assertNull(registry.takeLoading("chapter") { true })
        assertEquals(listOf(view), destroyed)
        assertFalse(registry.owns("chapter"))
    }

    @Test fun trimmingRetainedPreloadsPreservesAnActiveSurface() {
        val destroyed = mutableListOf<Any>()
        val registry = EpubPreloadRegistry<Any, Any>({ 3 }, destroyed::add)
        val ready = Any(); val pending = Any(); val active = Any()
        registry.ready.put("ready", ready)
        registry.loading["pending"] = pending
        registry.loading["active"] = active
        registry.clearRetained { it === active }
        assertEquals(listOf(ready, pending), destroyed)
        assertFalse(destroyed.contains(active))
        assertTrue(registry.hasCapacity())
    }
}
