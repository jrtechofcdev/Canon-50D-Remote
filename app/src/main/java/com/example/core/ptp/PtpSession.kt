package com.example.core.ptp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Receives the data phase of a transaction as it streams in. */
interface PtpDataSink {
    fun begin(totalBytes: Long) {}
    fun write(buffer: ByteArray, offset: Int, length: Int)
}

class ByteArraySink : PtpDataSink {
    private val out = ByteArrayOutputStream()
    override fun write(buffer: ByteArray, offset: Int, length: Int) = out.write(buffer, offset, length)
    fun bytes(): ByteArray = out.toByteArray()
}

class StreamSink(
    private val out: OutputStream,
    private val onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
) : PtpDataSink {
    private var total = 0L
    private var received = 0L
    override fun begin(totalBytes: Long) {
        total = totalBytes
    }
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        out.write(buffer, offset, length)
        received += length
        onProgress(received, total)
    }
}

/**
 * One PTP session over a transport.
 *
 * Every transaction runs to completion (command, optional data phase, response) while holding the lock.
 * Leaving a response unread is what left the 50D stuck on its "communicating" screen in the old code,
 * so a failed read marks the session broken instead of carrying on out of sync.
 */
class PtpSession(
    private val transport: PtpTransport,
    private val log: (String) -> Unit = {},
) {
    private val lock = Mutex()
    private var nextTransactionId = 1
    private val buffer = ByteArray(transport.readBufferSize)

    @Volatile
    var isBroken = false
        private set

    suspend fun transact(
        op: Int,
        vararg params: Int,
        dataOut: ByteArray? = null,
        dataIn: PtpDataSink? = null,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): IntArray = lock.withLock {
        withContext(Dispatchers.IO) { transactBlocking(op, params, dataOut, dataIn, timeoutMs) }
    }

    /** Convenience for operations that return a dataset. */
    suspend fun transactForData(op: Int, vararg params: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): ByteArray {
        val sink = ByteArraySink()
        transact(op, *params, dataIn = sink, timeoutMs = timeoutMs)
        return sink.bytes()
    }

    suspend fun openSession(sessionId: Int = 1) = lock.withLock {
        withContext(Dispatchers.IO) {
            // The spec requires transaction id 0 for OpenSession; numbering restarts at 1 afterwards.
            nextTransactionId = 0
            try {
                transactBlocking(PtpOp.OPEN_SESSION, intArrayOf(sessionId), null, null, DEFAULT_TIMEOUT_MS)
            } catch (e: PtpResponseException) {
                if (e.code != PtpResponse.SESSION_ALREADY_OPEN) throw e
                log("Sessão já estava aberta, reaproveitando")
            }
        }
    }

    fun close() = transport.close()

    private fun transactBlocking(
        op: Int,
        params: IntArray,
        dataOut: ByteArray?,
        dataIn: PtpDataSink?,
        timeoutMs: Int,
    ): IntArray {
        if (isBroken) throw PtpException("Conexão com a câmera perdida")
        val tid = nextTransactionId++
        try {
            transport.write(PtpContainer.command(op, tid, params), timeoutMs)
            if (dataOut != null) transport.write(PtpContainer.data(op, tid, dataOut), timeoutMs)

            var header = readContainer(dataIn, timeoutMs)
            if (header.type == PtpContainer.TYPE_DATA) header = readContainer(null, timeoutMs)
            // A late response from an earlier, timed-out transaction can still be queued; skip it.
            var guard = 0
            while (header.type == PtpContainer.TYPE_RESPONSE && header.transactionId != tid && guard++ < 3) {
                log("Resposta antiga descartada (tid ${header.transactionId}, esperado $tid)")
                header = readContainer(dataIn, timeoutMs)
                if (header.type == PtpContainer.TYPE_DATA) header = readContainer(null, timeoutMs)
            }
            if (header.type != PtpContainer.TYPE_RESPONSE) {
                throw PtpProtocolException("Esperava resposta, recebeu tipo ${header.type}")
            }
            if (header.code != PtpResponse.OK) throw PtpResponseException(header.code, op)
            return lastResponseParams
        } catch (e: PtpResponseException) {
            throw e
        } catch (e: Exception) {
            isBroken = true
            log("Falha na operação 0x%04X: %s".format(op, e.message))
            throw if (e is PtpException) e else PtpException(e.message ?: "Erro de comunicação USB")
        }
    }

    private var lastResponseParams = IntArray(0)

    /** Reads one full container. Data payload goes to [sink]; response parameters are kept. */
    private fun readContainer(sink: PtpDataSink?, timeoutMs: Int): PtpContainer.Header {
        var n = transport.read(buffer, timeoutMs)
        var zeroPackets = 0
        while (n == 0 && zeroPackets++ < 3) n = transport.read(buffer, timeoutMs) // trailing ZLP
        val header = PtpContainer.parseHeader(buffer, n)

        when (header.type) {
            PtpContainer.TYPE_DATA -> {
                val payloadSize = header.length - PtpContainer.HEADER_SIZE
                sink?.begin(payloadSize)
                sink?.write(buffer, PtpContainer.HEADER_SIZE, n - PtpContainer.HEADER_SIZE)
                var received = n.toLong()
                while (received < header.length) {
                    val m = transport.read(buffer, timeoutMs)
                    if (m <= 0) throw PtpProtocolException("Transferência interrompida em $received/${header.length}")
                    sink?.write(buffer, 0, m)
                    received += m
                }
            }
            PtpContainer.TYPE_RESPONSE -> {
                val count = ((n - PtpContainer.HEADER_SIZE) / 4).coerceIn(0, 5)
                lastResponseParams = IntArray(count) { PtpDataReader.u32At(buffer, PtpContainer.HEADER_SIZE + it * 4) }
            }
        }
        return header
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
    }
}
