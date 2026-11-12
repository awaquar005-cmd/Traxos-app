package app.traxos.finance

import app.traxos.finance.domain.NavigationLayout as Layout
import org.junit.Assert.*
import org.junit.Test

class NavigationLayoutTest {
    @Test
    fun everySlotCanChangeBySwappingWithoutDuplicates() {
        for (index in Layout.defaults.indices) for (destination in Layout.implemented) {
            val changed = Layout.replace(Layout.defaults, index, destination)
            assertEquals(destination, changed[index])
            assertEquals(4, changed.distinct().size)
            assertTrue(changed.all { it in Layout.implemented })
        }
    }

    @Test
    fun reorderingIsStableAndSerializable() {
        val changed = Layout.move(Layout.defaults, 3, 0)
        assertEquals(listOf("settings", "home", "transactions", "accounts"), changed)
        assertEquals(changed, Layout.decode(Layout.encode(changed)))
    }

    @Test
    fun invalidAndUnimplementedDestinationsAreRejected() {
        listOf(
                listOf("home"),
                listOf("home", "home", "accounts", "settings"),
                listOf("goals", "transactions", "accounts", "settings"),
            )
            .forEach { slots ->
                assertThrows(IllegalArgumentException::class.java) { Layout.validate(slots) }
            }
        assertThrows(IllegalArgumentException::class.java) {
            Layout.replace(Layout.defaults, 0, "groups")
        }
    }

    @Test
    fun corruptSavedConfigurationHasSafeFallback() {
        assertEquals(Layout.defaults, Layout.decode("goals,debts,groups,cards"))
        assertEquals(Layout.defaults, Layout.decode(""))
    }

    @Test
    fun boundsAreValidatedAndSameDestinationIsANoOp() {
        assertThrows(IllegalArgumentException::class.java) { Layout.move(Layout.defaults, 0, 4) }
        assertEquals(Layout.defaults, Layout.replace(Layout.defaults, 0, "home"))
    }
}
