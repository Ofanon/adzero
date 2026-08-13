package com.adzero.app

/**
 * Just enough IPv4/UDP/DNS to read a query out of the tunnel and write an
 * answer back into it.
 *
 * Only the DNS server address is routed into the VPN, so everything arriving
 * here is a UDP datagram bound for port 53. No TCP stack needed.
 */
object Packets {

    const val IP_HEADER = 20
    const val UDP_HEADER = 8

    class Query(
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val sourcePort: Int,
        val destPort: Int,
        val payload: ByteArray,
    )

    /** null when the packet is not usable IPv4/UDP. */
    fun readUdp(buf: ByteArray, size: Int): Query? {
        if (size < IP_HEADER + UDP_HEADER) return null
        val version = (buf[0].toInt() shr 4) and 0xF
        if (version != 4) return null

        val ihl = (buf[0].toInt() and 0xF) * 4
        if (ihl < IP_HEADER || size < ihl + UDP_HEADER) return null
        if ((buf[9].toInt() and 0xFF) != 17) return null // 17 = UDP

        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        val udpLength = ((buf[ihl + 4].toInt() and 0xFF) shl 8) or (buf[ihl + 5].toInt() and 0xFF)

        val start = ihl + UDP_HEADER
        val end = minOf(size, ihl + udpLength)
        if (end <= start) return null

        return Query(srcIp, dstIp, srcPort, dstPort, buf.copyOfRange(start, end))
    }

    /**
     * The queried name, or null when the query is unreadable. Only the first
     * question is read — SDKs never send a second one.
     */
    fun queriedName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val qdcount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
        if (qdcount < 1) return null

        val name = StringBuilder()
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break
            // 0xC0 marks a compression pointer, never present in a question
            if (len and 0xC0 != 0) return null
            if (i + 1 + len > dns.size) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(dns, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        return name.takeIf { it.isNotEmpty() }?.toString()
    }

    /**
     * Rebuilds a full IP/UDP packet carrying [payload] back to the app, with
     * source and destination swapped.
     *
     * The UDP checksum is left at zero: that is legal over IPv4 and saves
     * building a pseudo-header for nothing.
     */
    fun buildReply(query: Query, payload: ByteArray): ByteArray {
        val total = IP_HEADER + UDP_HEADER + payload.size
        val p = ByteArray(total)

        p[0] = 0x45                        // IPv4, 20-byte header
        p[1] = 0
        p[2] = (total shr 8).toByte()
        p[3] = total.toByte()
        p[4] = 0; p[5] = 0                 // identification
        p[6] = 0x40; p[7] = 0              // Don't Fragment
        p[8] = 64                          // TTL
        p[9] = 17                          // UDP
        // 10-11: header checksum, filled in below
        System.arraycopy(query.destIp, 0, p, 12, 4)    // from the tunnel's DNS
        System.arraycopy(query.sourceIp, 0, p, 16, 4)  // to the app

        val sum = checksum(p, 0, IP_HEADER)
        p[10] = (sum shr 8).toByte()
        p[11] = sum.toByte()

        val u = IP_HEADER
        p[u] = (query.destPort shr 8).toByte()
        p[u + 1] = query.destPort.toByte()
        p[u + 2] = (query.sourcePort shr 8).toByte()
        p[u + 3] = query.sourcePort.toByte()
        val udpLen = UDP_HEADER + payload.size
        p[u + 4] = (udpLen shr 8).toByte()
        p[u + 5] = udpLen.toByte()
        p[u + 6] = 0; p[u + 7] = 0         // UDP checksum: optional over IPv4

        System.arraycopy(payload, 0, p, IP_HEADER + UDP_HEADER, payload.size)
        return p
    }

    private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += (((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (length % 2 == 1) sum += ((buf[offset + length - 1].toInt() and 0xFF) shl 8).toLong()
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
