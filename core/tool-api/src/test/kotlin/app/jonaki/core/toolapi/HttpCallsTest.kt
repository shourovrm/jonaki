package app.jonaki.core.toolapi

import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCallsTest {
    private val client = OkHttpClient()

    @Test
    fun awaitReturnsTheResponse() = runBlocking {
        ServerSocket(0).use { server ->
            thread {
                server.accept().use { socket ->
                    socket.getInputStream().read(ByteArray(1024))
                    val reply = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello"
                    socket.getOutputStream().write(reply.toByteArray())
                }
            }
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val body = client.newCall(request).await().use { response -> response.body!!.string() }
            assertEquals("hello", body)
        }
    }

    @Test
    fun cancellingTheCoroutineCancelsTheCall() = runBlocking {
        ServerSocket(0).use { server ->
            // The server accepts but never answers, like a hung search backend.
            thread {
                runCatching {
                    server.accept().getInputStream().read(ByteArray(1024))
                    Thread.sleep(5_000)
                }
            }
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val call = client.newCall(request)
            val result = withTimeoutOrNull(300) { call.await() }
            assertNull(result)
            assertTrue(call.isCanceled())
        }
    }
}
