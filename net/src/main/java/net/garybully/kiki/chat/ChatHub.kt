package net.garybully.kiki.chat

import net.garybully.kiki.transferproto.fileexplore.FileExplore
import net.garybully.kiki.transferproto.fileexplore.requestMsgSuspend
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the live FileExplore connections used for chat: one per direct contact,
 * N per group (creator is the hub and fans messages out to every member).
 *
 * v1 hub model: creator connects out (FileExplore client) to each member, so
 * members must be in receive mode. Members see each other only through the hub.
 */
class ChatHub {

    private val connections = ConcurrentHashMap<String, FileExplore>()

    /** msgIds already processed — prevents fan-out loops and duplicates. */
    private val seenMsgIds: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    /** groupId -> member device ids (creator first). */
    private val groups = ConcurrentHashMap<String, MutableSet<String>>()

    fun put(deviceId: String, explore: FileExplore) {
        connections[deviceId] = explore
    }

    fun remove(deviceId: String) {
        connections.remove(deviceId)
        groups.values.forEach { it.remove(deviceId) }
    }

    fun get(deviceId: String): FileExplore? = connections[deviceId]

    fun deviceIds(): Set<String> = connections.keys

    fun isConnected(deviceId: String): Boolean = connections.containsKey(deviceId)

    /** true the first time a msgId is seen, false afterwards (dedup). */
    fun markSeen(msgId: String): Boolean = seenMsgIds.add(msgId)

    // ---------- groups ----------

    fun createGroup(memberIds: List<String>): String {
        val groupId = UUID.randomUUID().toString()
        groups[groupId] = memberIds.toMutableSet()
        return groupId
    }

    fun groupMembers(groupId: String): Set<String> = groups[groupId]?.toSet() ?: emptySet()

    fun groupsOf(deviceId: String): List<String> =
        groups.filterValues { deviceId in it }.keys.toList()

    fun removeGroup(groupId: String) {
        groups.remove(groupId)
    }

    // ---------- fan-out ----------

    /**
     * Send [msg] to every connected member of its group except [excludeId].
     * Direct messages (groupId == null) go to the single peer.
     */
    suspend fun fanOut(msg: ChatMessage, excludeId: String? = null) {
        if (msg.groupId == null) {
            val target = excludeId?.let { connections[it] } ?: return
            runCatching { target.requestMsgSuspend(ChatEnvelope.encode(msg)) }
            return
        }
        val members = groups[msg.groupId] ?: return
        for (memberId in members) {
            if (memberId == excludeId) continue
            val explore = connections[memberId] ?: continue
            runCatching { explore.requestMsgSuspend(ChatEnvelope.encode(msg)) }
        }
    }

    /** Send a raw message to one device (direct chat). */
    suspend fun sendTo(deviceId: String, msg: ChatMessage) {
        val explore = connections[deviceId] ?: return
        runCatching { explore.requestMsgSuspend(ChatEnvelope.encode(msg)) }
    }

    fun closeAll() {
        connections.values.forEach { runCatching { it.closeConnectionIfActive() } }
        connections.clear()
        groups.clear()
        seenMsgIds.clear()
    }
}
