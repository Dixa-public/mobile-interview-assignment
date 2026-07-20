package com.dixa.messenger.sample

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dixa.messenger.ConnectionState
import com.dixa.messenger.Message
import com.dixa.messenger.Messenger
import com.dixa.messenger.MessengerConfig
import com.dixa.messenger.MessengerListener
import com.dixa.messenger.sample.databinding.ActivityChatBinding

/**
 * Minimal chat host app. Embeds the Messenger SDK, connects to the shared mock
 * server at ws://localhost:8080, and shows incoming/outgoing messages.
 *
 * SDK callbacks may arrive off the main thread, so UI updates are marshalled with
 * runOnUiThread.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val adapter = MessageAdapter()
    private lateinit var messenger: Messenger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.messageList.layoutManager = LinearLayoutManager(this)
        binding.messageList.adapter = adapter

        val config = MessengerConfig.Builder()
            .serverUrl("ws://localhost:8080")
            .loggingEnabled(true)
            .build()

        messenger = Messenger(config)
        messenger.setListener(object : MessengerListener {
            override fun onMessage(message: Message) = runOnUiThread {
                adapter.add(message)
                binding.messageList.scrollToPosition(adapter.itemCount - 1)
            }

            override fun onConnectionStateChanged(state: ConnectionState) = runOnUiThread {
                binding.connectionBanner.text = "Connection: $state"
            }

            override fun onTypingChanged(isTyping: Boolean) = runOnUiThread {
                binding.typingBubble.visibility = if (isTyping) View.VISIBLE else View.GONE
            }
        })

        binding.sendButton.setOnClickListener {
            val text = binding.input.text.toString().trim()
            if (text.isNotEmpty()) {
                messenger.send(text)
                binding.input.setText("")
            }
        }

        messenger.connect()
    }

    override fun onDestroy() {
        messenger.disconnect()
        super.onDestroy()
    }
}
