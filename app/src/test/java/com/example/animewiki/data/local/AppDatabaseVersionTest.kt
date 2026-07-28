package com.example.animewiki.data.local

import androidx.room.RoomOpenDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDatabaseVersionTest {
    @Test
    fun `provider boundary uses destructive Room version three`() {
        val createOpenDelegate = AppDatabase_Impl::class.java
            .getDeclaredMethod("createOpenDelegate")
            .apply { isAccessible = true }
        val delegate = createOpenDelegate.invoke(AppDatabase_Impl()) as RoomOpenDelegate

        assertEquals(3, delegate.version)
    }
}
