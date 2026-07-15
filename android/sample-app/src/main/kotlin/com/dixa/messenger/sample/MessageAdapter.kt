package com.dixa.messenger.sample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dixa.messenger.Author
import com.dixa.messenger.Message

/** Simple RecyclerView adapter rendering chat messages. */
class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()

    fun add(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.author.text = if (message.author == Author.AGENT) "Agent" else "You"
        holder.text.text = message.text
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val author: TextView = view.findViewById(R.id.author)
        val text: TextView = view.findViewById(R.id.text)
    }
}
