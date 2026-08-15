package com.example.animewiki.data.local

import androidx.room.RoomOpenDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDatabaseVersionTest {
    @Test
    fun `database is at migration-safe version four`() {
        val createOpenDelegate = AppDatabase_Impl::class.java
            .getDeclaredMethod("createOpenDelegate")
            .apply { isAccessible = true }
        val delegate = createOpenDelegate.invoke(AppDatabase_Impl()) as RoomOpenDelegate

        assertEquals(4, delegate.version)
    }
}
