package com.realworldgta

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.util.concurrent.Executors

/**
 * Single-thread owner for LiteRT-LM. Model and conversation operations run off the UI thread.
 */
class LocalAiEngine(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(status: String, detail: String)
        fun onToken(token: String)
        fun onComplete()
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun loadModel(modelPath: String, systemInstruction: String, historyJson: String) {
        worker.execute {
            try {
                if (engine?.engineConfig?.modelPath != modelPath) {
                    closeConversation()
                    engine?.close()
                    engine = null

                    val candidate = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.CPU(),
                            cacheDir = appContext.cacheDir.absolutePath
                        )
                    )
                    // Do not retain an Engine until initialization has succeeded.
                    candidate.initialize()
                    engine = candidate
                }
                replaceConversation(systemInstruction, historyJson)
                postStatus("ready", "On-device AI is ready. Your chat stays on this phone.")
            } catch (error: Exception) {
                postError(error.message ?: "Could not load the local model.")
            }
        }
    }

    fun startConversation(systemInstruction: String, historyJson: String) {
        worker.execute {
            try {
                if (engine == null) {
                    postError("Download and load the on-device model first.")
                    return@execute
                }
                replaceConversation(systemInstruction, historyJson)
                postStatus("ready", "Character ready.")
            } catch (error: Exception) {
                postError(error.message ?: "Could not start this character.")
            }
        }
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank() || prompt.length > 8000) {
            postError("Message must contain 1–8,000 characters.")
            return
        }
        worker.execute {
            val activeConversation = conversation
            if (activeConversation == null) {
                postError("Load the on-device model before chatting.")
                return@execute
            }
            postStatus("generating", "Generating on this device…")
            try {
                runBlocking {
                    activeConversation.sendMessageAsync(\n                        prompt,\n                        extraContext = mapOf("enable_thinking" to false)\n                    ).collect { chunk ->
                        val token = chunk.toString()
                        mainHandler.post { listener.onToken(token) }
                    }
                }
                mainHandler.post { listener.onComplete() }
                postStatus("ready", "On-device AI is ready.")
            } catch (error: Exception) {
                postError(error.message ?: "Local generation failed.")
            }
        }
    }

    private fun replaceConversation(systemInstruction: String, historyJson: String) {
        closeConversation()
        val initialMessages = parseHistory(historyJson)
        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            initialMessages = initialMessages,
            samplerConfig = SamplerConfig(topK = 20, topP = 0.92, temperature = 0.85)
        )
        conversation = checkNotNull(engine).createConversation(config)
    }

    private fun parseHistory(json: String): List<Message> {
        val result = ArrayList<Message>()
        try {
            val items = JSONArray(json)
            val start = (items.length() - 12).coerceAtLeast(0)
            for (index in start until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val role = item.optString("role")
                val content = item.optString("content").take(4000)
                if (content.isBlank()) continue
                if (role == "user") result.add(Message.user(content))
                else if (role == "assistant") result.add(Message.model(content))
            }
        } catch (_: Exception) {
            // A corrupt or old conversation is treated as an empty history.
        }
        return result
    }

    private fun closeConversation() {
        conversation?.close()
        conversation = null
    }

    private fun postStatus(status: String, detail: String) {
        mainHandler.post { listener.onStatus(status, detail) }
    }

    private fun postError(message: String) {
        mainHandler.post { listener.onError(message) }
    }

    fun close() {
        worker.execute {
            try {
                closeConversation()
                engine?.close()
                engine = null
            } catch (_: Exception) {
                engine = null
                conversation = null
            } finally {
                worker.shutdown()
            }
        }
    }
}
