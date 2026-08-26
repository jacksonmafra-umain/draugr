package com.umain.draugr.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class AssetRoutingTest {

    private val token = "cafebabecafebabe"
    private val payload = ByteArray(2048) { (it % 251).toByte() }

    private fun fakeProvider(): AssetProvider {
        val fs = FakeFileSystem()
        val root = "/images".toPath()
        fs.createDirectories(root)
        fs.write(root / "disk.img") { write(payload) }
        return PrefixAssetProvider(
            listOf(
                "images" to FileAssetProvider(fs, root),
                "" to BundledAssetProvider { path ->
                    when (path) {
                        "host.html" -> "<html><body>DRAUGR</body></html>".encodeToByteArray()
                        "bridge.js" -> "window.DRAUGR = {};".encodeToByteArray()
                        else -> null
                    }
                },
            ),
        )
    }

    @Test
    fun serves_isolation_headers_on_every_response() = runTest {
        testApplication {
            application { draugrAssetModule(token, fakeProvider()) }
            val response = client.get("/$token/host.html")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("same-origin", response.headers["Cross-Origin-Opener-Policy"])
            assertEquals("require-corp", response.headers["Cross-Origin-Embedder-Policy"])
            assertEquals("same-origin", response.headers["Cross-Origin-Resource-Policy"])
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        }
    }

    @Test
    fun serves_wasm_content_type() = runTest {
        testApplication {
            application {
                draugrAssetModule(token, BundledAssetProvider { ByteArray(4) })
            }
            val response = client.get("/$token/v86.wasm")
            assertEquals("application/wasm", response.headers[HttpHeaders.ContentType])
        }
    }

    @Test
    fun slices_a_byte_range_into_206() = runTest {
        testApplication {
            application { draugrAssetModule(token, fakeProvider()) }
            val response = client.get("/$token/images/disk.img") {
                header(HttpHeaders.Range, "bytes=0-511")
            }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes 0-511/2048", response.headers[HttpHeaders.ContentRange])
            val body = response.bodyAsBytes()
            assertEquals(512, body.size)
            assertEquals(payload.take(512), body.toList())
        }
    }

    @Test
    fun honours_an_open_ended_range() = runTest {
        testApplication {
            application { draugrAssetModule(token, fakeProvider()) }
            val response = client.get("/$token/images/disk.img") {
                header(HttpHeaders.Range, "bytes=2040-")
            }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes 2040-2047/2048", response.headers[HttpHeaders.ContentRange])
            assertEquals(8, response.bodyAsBytes().size)
        }
    }

    @Test
    fun rejects_an_unsatisfiable_range() = runTest {
        testApplication {
            application { draugrAssetModule(token, fakeProvider()) }
            val response = client.get("/$token/images/disk.img") {
                header(HttpHeaders.Range, "bytes=9000-9100")
            }
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
            assertEquals("bytes */2048", response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun serves_a_payload_larger_than_one_chunk_intact() = runTest {
        // A 64KB chunking bug only shows up above the chunk size, which is why this is 300KB.
        val large = ByteArray(300_000) { (it % 253).toByte() }
        testApplication {
            application {
                draugrAssetModule(token, BundledAssetProvider { if (it == "big.js") large else null })
            }
            val response = client.get("/$token/big.js")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsBytes()
            assertEquals(large.size, body.size)
            assertEquals(large.last(), body.last())
        }
    }

    @Test
    fun slices_a_range_that_spans_chunks() = runTest {
        val large = ByteArray(300_000) { (it % 253).toByte() }
        testApplication {
            application {
                draugrAssetModule(token, BundledAssetProvider { if (it == "big.js") large else null })
            }
            val response = client.get("/$token/big.js") {
                header(HttpHeaders.Range, "bytes=1000-200999")
            }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            val body = response.bodyAsBytes()
            assertEquals(200_000, body.size)
            assertEquals(large[1000], body.first())
            assertEquals(large[200_999], body.last())
        }
    }

    @Test
    fun the_wrong_token_sees_nothing() = runTest {
        testApplication {
            application { draugrAssetModule(token, fakeProvider()) }
            assertEquals(HttpStatusCode.NotFound, client.get("/deadbeef/host.html").status)
        }
    }

    @Test
    fun path_traversal_is_refused() = runTest {
        testApplication {
            application { draugrAssetModule(token, fakeProvider()) }
            assertEquals(
                HttpStatusCode.NotFound,
                client.get("/$token/images/../../etc/passwd").status,
            )
        }
    }

    @Test
    fun suffix_range_returns_the_tail() {
        val parsed = parseRange("bytes=-16", 2048)
        assertEquals(RangeRequest(2032, 2047), parsed)
        assertEquals(16, parsed!!.length)
    }

    @Test
    fun multi_range_is_refused() {
        assertNull(parseRange("bytes=0-1,4-5", 2048))
    }
}
