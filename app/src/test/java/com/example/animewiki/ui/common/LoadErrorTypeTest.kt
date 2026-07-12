package com.example.animewiki.ui.common

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LoadErrorTypeTest {

    @Test
    fun `IOException is treated as no connection`() {
        assertEquals(LoadErrorType.NO_CONNECTION, IOException("boom").toLoadErrorType())
    }

    @Test
    fun `UnknownHostException is treated as no connection`() {
        assertEquals(
            LoadErrorType.NO_CONNECTION,
            UnknownHostException("api.jikan.moe").toLoadErrorType()
        )
    }

    @Test
    fun `SocketTimeoutException is treated as no connection`() {
        assertEquals(
            LoadErrorType.NO_CONNECTION,
            SocketTimeoutException("timeout").toLoadErrorType()
        )
    }

    @Test
    fun `serialization failure is treated as a server error`() {
        assertEquals(
            LoadErrorType.SERVER,
            SerializationException("bad json").toLoadErrorType()
        )
    }

    @Test
    fun `generic runtime failure is treated as a server error`() {
        assertEquals(LoadErrorType.SERVER, RuntimeException("500").toLoadErrorType())
    }
}
