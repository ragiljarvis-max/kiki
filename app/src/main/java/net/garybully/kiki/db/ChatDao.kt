package net.garybully.kiki.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ---------- messages ----------

    @Query("SELECT * FROM chat_message WHERE threadId = :threadId ORDER BY sendTime ASC")
    fun observeThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message ORDER BY sendTime DESC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message ORDER BY sendTime DESC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity)

    @Query("UPDATE chat_message SET filePath = :path, transferState = :state WHERE msgId = :msgId")
    suspend fun updateFileState(msgId: String, path: String?, state: String)

    @Query("DELETE FROM chat_message WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    // ---------- contacts ----------

    @Query("SELECT * FROM contact WHERE deviceId = :deviceId")
    suspend fun getContact(deviceId: String): ContactEntity?

    @Query("SELECT * FROM contact ORDER BY lastSeenTime DESC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Upsert
    suspend fun upsertContact(contact: ContactEntity)

    @Query("UPDATE contact SET alias = :alias WHERE deviceId = :deviceId")
    suspend fun renameContact(deviceId: String, alias: String)

    @Query("DELETE FROM contact WHERE deviceId = :deviceId")
    suspend fun deleteContact(deviceId: String)

    // ---------- groups ----------

    @Query("SELECT * FROM chat_group ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("UPDATE chat_group SET name = :name WHERE groupId = :groupId")
    suspend fun renameGroup(groupId: String, name: String)

    @Query("DELETE FROM chat_group WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM group_member WHERE groupId = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_member WHERE groupId = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Upsert
    suspend fun upsertMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_member WHERE groupId = :groupId")
    suspend fun deleteMembers(groupId: String)
}
