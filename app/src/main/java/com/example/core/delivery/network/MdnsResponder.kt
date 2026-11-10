package com.example.core.delivery.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.core.log.AppLog
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Answers "where is jrexpress.local?" on the delivery Wi-Fi (multicast DNS), so clients can type a
 * name instead of 192.168.49.1. A real DNS needs root; mDNS does not. iPhone, Mac and Windows resolve
 * .local names; some Android phones don't, which is why the QR codes keep the numeric address.
 */
class MdnsResponder(private val context: Context, private val host: String, private val ip: String) {
    @Volatile private var socket: MulticastSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    val fullName: String get() = "$host.local"

    /** True when it is listening. False (and logged) when the phone did not allow it. */
    fun start(): Boolean = runCatching {
        lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("jrlink:mdns").apply { setReferenceCounted(false); acquire() }
        val s = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(PORT))
            timeToLive = 255
        }
        val iface = NetworkInterface.getNetworkInterfaces()?.toList()?.firstOrNull { ni -> ni.inetAddresses.toList().any { it.hostAddress == ip } }
        if (iface != null) s.joinGroup(InetSocketAddress(GROUP, PORT), iface) else s.joinGroup(InetAddress.getByName(GROUP))
        iface?.let { s.networkInterface = it }
        socket = s
        Thread({ loop(s) }, "entrega-mdns").apply { isDaemon = true }.start()
        // Announce twice, as the standard asks, so caches learn the name right away.
        repeat(2) { send(s, MdnsPacket.answer(fullName, ip)); Thread.sleep(250) }
        AppLog.i("ENTREGA", "Nome $fullName respondendo em $ip")
        true
    }.getOrElse {
        AppLog.w("ENTREGA", "Nome .local indisponível neste celular", it)
        stop()
        false
    }

    fun stop() {
        socket?.let { runCatching { it.close() } }
        socket = null
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    private fun loop(s: MulticastSocket) {
        val buf = ByteArray(1500)
        while (!s.isClosed) {
            val packet = DatagramPacket(buf, buf.size)
            try { s.receive(packet) } catch (_: Exception) { break }
            val asked = MdnsPacket.asksFor(buf.copyOf(packet.length), fullName) ?: continue
            val reply = MdnsPacket.answer(fullName, ip)
            // A "unicast response" question gets the answer straight back; the rest go to the group.
            runCatching {
                if (asked.unicast) s.send(DatagramPacket(reply, reply.size, packet.socketAddress)) else send(s, reply)
            }
        }
    }

    private fun send(s: MulticastSocket, data: ByteArray) {
        s.send(DatagramPacket(data, data.size, InetSocketAddress(GROUP, PORT)))
    }

    companion object {
        const val PORT = 5353
        const val GROUP = "224.0.0.251"

        /** "JR Fotos!" → "jr-fotos": letters, digits and hyphens only. */
        fun hostName(typed: String, fallback: String): String =
            java.text.Normalizer.normalize(typed.trim().lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).ifEmpty { fallback }
    }
}

/** The two bits of DNS the responder needs: read a question, write an answer. */
object MdnsPacket {
    data class Question(val name: String, val type: Int, val unicast: Boolean)

    /** The question in [packet] for [name] (type A or ANY), or null when it asks for something else. */
    fun asksFor(packet: ByteArray, name: String): Question? = runCatching {
        if (packet.size < 12) return null
        val flags = u16(packet, 2)
        if (flags and 0x8000 != 0) return null // a response, not a question
        val count = u16(packet, 4)
        var at = 12
        repeat(count) {
            val (qname, next) = readName(packet, at)
            val type = u16(packet, next)
            val klass = u16(packet, next + 2)
            at = next + 4
            if (qname.equals(name, ignoreCase = true) && (type == 1 || type == 255)) {
                return Question(qname, type, unicast = klass and 0x8000 != 0)
            }
        }
        null
    }.getOrNull()

    /** An authoritative answer: [name] is at [ip] (IPv4), for 2 minutes. */
    fun answer(name: String, ip: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        u16(0); u16(0x8400); u16(0); u16(1); u16(0); u16(0)
        name.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray()
            out.write(bytes.size); out.write(bytes)
        }
        out.write(0)
        u16(1) // A
        u16(0x8001) // IN, cache flush
        u16(0); u16(120) // TTL
        u16(4)
        ip.split('.').forEach { out.write(it.toInt()) }
        return out.toByteArray()
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF shl 8) or (b[at + 1].toInt() and 0xFF)

    /** A name at [start] (following compression pointers) and where the record goes on after it. */
    private fun readName(b: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var at = start
        var end = -1
        var jumps = 0
        while (true) {
            val len = b[at].toInt() and 0xFF
            when {
                len == 0 -> { if (end < 0) end = at + 1; break }
                len and 0xC0 == 0xC0 -> {
                    if (end < 0) end = at + 2
                    at = ((len and 0x3F) shl 8) or (b[at + 1].toInt() and 0xFF)
                    if (++jumps > 10) error("loop")
                }
                else -> { labels += String(b, at + 1, len); at += len + 1 }
            }
        }
        return labels.joinToString(".") to end
    }
}
