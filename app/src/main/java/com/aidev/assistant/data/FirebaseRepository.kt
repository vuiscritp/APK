package com.aidev.assistant.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository {

    private val db = FirebaseDatabase
        .getInstance("https://ai-api-project-1-default-rtdb.firebaseio.com/")
        .reference

    private val sessionsRef = db.child("sessions")
    private val messagesRef = db.child("messages")

    suspend fun createSession(title: String = "New Chat"): String {
        val id = UUID.randomUUID().toString()
        val session = ChatSession(id = id, title = title)
        sessionsRef.child(id).setValue(session).await()
        return id
    }

    suspend fun saveMessage(sessionId: String, message: ChatMessage) {
        val msgId = message.id.ifEmpty { UUID.randomUUID().toString() }
        val msg = message.copy(id = msgId)
        messagesRef.child(sessionId).child(msgId).setValue(msg).await()
        sessionsRef.child(sessionId).child("updatedAt").setValue(System.currentTimeMillis())
    }

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull {
                    it.getValue(ChatMessage::class.java)
                }.sortedBy { it.timestamp }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        messagesRef.child(sessionId).addValueEventListener(listener)
        awaitClose { messagesRef.child(sessionId).removeEventListener(listener) }
    }

    fun observeSessions(): Flow<List<ChatSession>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull {
                    it.getValue(ChatSession::class.java)
                }.sortedByDescending { it.updatedAt }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        sessionsRef.addValueEventListener(listener)
        awaitClose { sessionsRef.removeEventListener(listener) }
    }

    suspend fun deleteSession(sessionId: String) {
        sessionsRef.child(sessionId).removeValue().await()
        messagesRef.child(sessionId).removeValue().await()
    }

    /** Clears all messages in a session but keeps the session itself (used by the "clear chat" action). */
    suspend fun clearMessages(sessionId: String) {
        messagesRef.child(sessionId).removeValue().await()
    }
}
