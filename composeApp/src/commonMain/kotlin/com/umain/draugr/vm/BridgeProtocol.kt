package com.umain.draugr.vm

import com.umain.draugr.catalog.MachineSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Wire format shared by both platforms. Kept in commonMain so Android and iOS cannot drift
 * apart, and so it is unit testable without a web view.
 */
object BridgeProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    /** Builds the `window.DRAUGR.boot(...)` argument for a machine. */
    fun bootConfig(
        spec: MachineSpec,
        serverOrigin: String,
        networkRelayUrl: String? = null,
    ): String {
        fun url(path: String?) = path?.let { "$serverOrigin/$it" }
        return buildJsonObject {
            put("id", spec.id)
            put("engine", spec.engine.name.lowercase())
            put("cpu", spec.cpu)
            put("memMb", spec.memMb)
            put("vgaMemMb", spec.vgaMemMb)
            put("ui", spec.ui.name)
            if (spec.engine == com.umain.draugr.catalog.Engine.TINYEMU) {
                put("configUrl", "$serverOrigin/${TinyEmuConfig.pathFor(spec)}")
                put("cmdline", TinyEmuConfig.defaultCmdline(spec))
            }
            url(spec.assets.bios)?.let { put("bios", it) }
            url(spec.assets.vgabios)?.let { put("vgabios", it) }
            url(spec.assets.kernel)?.let { put("kernel", it) }
            url(spec.assets.initrd)?.let { put("initrd", it) }
            url(spec.assets.hda)?.let { put("hda", it) }
            url(spec.assets.fda)?.let { put("fda", it) }
            url(spec.assets.cdrom)?.let { put("cdrom", it) }
            url(spec.assets.stateImage)?.let { put("stateImage", it) }
            spec.cmdline?.let { put("cmdline", it) }
            networkRelayUrl?.takeIf { it.isNotBlank() }?.let { put("networkRelayUrl", toWispUrl(it)) }
        }.toString()
    }

    fun sendKeysCall(codes: IntArray): String {
        val array = buildJsonObject {
            putJsonArray("codes") { codes.forEach { add(it) } }
        }["codes"].toString()
        return "window.DRAUGR.sendKeys($array);"
    }

    fun sendTextCall(text: String): String =
        "window.DRAUGR.sendText(${quote(text)});"

    fun bootCall(
        spec: MachineSpec,
        serverOrigin: String,
        networkRelayUrl: String? = null,
    ): String = "window.DRAUGR.boot(${bootConfig(spec, serverOrigin, networkRelayUrl)});"

    /** Translates one JSON event from the page into a [VmEvent], or null if unrecognised. */
    fun parseEvent(raw: String): VmEvent? {
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return VmEvent.Fault("UNPARSEABLE EVENT")
        return when (obj["type"]?.jsonPrimitive?.content) {
            "serial" -> VmEvent.Serial(obj["line"]?.jsonPrimitive?.content.orEmpty())

            "screen" -> VmEvent.ScreenResized(
                w = obj["width"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null,
                h = obj["height"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null,
                graphical = obj["graphical"]?.jsonPrimitive?.booleanOrNull ?: true,
            )

            "stats" -> VmEvent.Stats(
                instructionsPerSecond = obj["ips"]?.jsonPrimitive?.longOrNull ?: 0L,
                fetchedBytes = obj["fetchedBytes"]?.jsonPrimitive?.longOrNull ?: 0L,
            )

            "error" -> VmEvent.Fault(
                listOfNotNull(
                    obj["stage"]?.jsonPrimitive?.content,
                    obj["message"]?.jsonPrimitive?.content,
                ).joinToString(": "),
            )

            "state" -> when (obj["state"]?.jsonPrimitive?.content) {
                "idle" -> VmEvent.StateChanged(VmState.Idle)
                "fetching" -> VmEvent.StateChanged(
                    VmState.Fetching(
                        asset = obj["asset"]?.jsonPrimitive?.content.orEmpty(),
                        received = obj["received"]?.jsonPrimitive?.longOrNull ?: 0L,
                        total = obj["total"]?.jsonPrimitive?.longOrNull ?: 0L,
                    ),
                )
                "booting" -> VmEvent.StateChanged(VmState.Booting(elapsedMs = 0L))
                "running" -> VmEvent.StateChanged(VmState.Running(since = nowInstant()))
                "suspended" -> VmEvent.StateChanged(VmState.Suspended(SuspendReason.USER))
                "halted" -> VmEvent.StateChanged(
                    VmState.Halted(obj["message"]?.jsonPrimitive?.content),
                )
                else -> null
            }

            else -> null
        }
    }

    /**
     * iOS has a single message channel, so promise replies arrive as events. Returns the
     * request id and payload when [raw] is one of those, null when it is a normal event.
     */
    fun parseReplyOrNull(raw: String): Pair<Long, String?>? {
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        if (obj["type"]?.jsonPrimitive?.content != "reply") return null
        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val payload = obj["payload"]?.jsonPrimitive?.contentOrNull
        return id to payload
    }

    fun isolationFromCapabilities(raw: String): Boolean {
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return false
        return obj["crossOriginIsolated"]?.jsonPrimitive?.booleanOrNull == true
    }

    /**
     * v86 selects its network backend from the URL scheme. The relay speaks WISP over a plain
     * WebSocket, so a ws:// the user typed becomes wisp:// and wss:// becomes wisps://. A URL
     * already using a wisp scheme is left alone.
     */
    private fun toWispUrl(url: String): String = when {
        url.startsWith("wisp://") || url.startsWith("wisps://") -> url
        url.startsWith("wss://") -> "wisps://" + url.removePrefix("wss://")
        url.startsWith("ws://") -> "wisp://" + url.removePrefix("ws://")
        else -> url
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u" + c.code.toString(16).padStart(4, '0'))
                else append(c)
            }
        }
        append('"')
    }
}
