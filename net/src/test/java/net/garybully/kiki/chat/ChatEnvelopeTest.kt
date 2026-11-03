package net.garybully.kiki.chat

object ChatEnvelopeTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val m = ChatMessage.text("dev-1", "Pixel", "halo 😺", groupId = null)
        val raw = ChatEnvelope.encode(m)
        check(raw.startsWith("KIKI_CHAT\u0000")) { "envelope prefix missing" }
        check(ChatEnvelope.decode(raw) == m) { "round-trip mismatch: ${ChatEnvelope.decode(raw)}" }
        check(ChatEnvelope.decode("halo biasa") == null) { "plain message must not decode" }

        val f = ChatMessage.file("dev-2", "PC", "foto.png", 1234L, groupId = "g1")
        check(ChatEnvelope.decode(ChatEnvelope.encode(f)) == f) { "file msg round-trip" }
        check(f.isFile) { "file type flag" }
        println("ChatEnvelopeTest OK")
    }
}
