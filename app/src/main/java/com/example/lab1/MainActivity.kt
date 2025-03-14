package com.example.lab1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View

class MainActivity : AppCompatActivity() {

    // Объявляем adapter как поле класса
    private lateinit var adapter: ItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Используем MutableList вместо List
        val items = mutableListOf(
            Item(1, "Item 1", "Description 1"),
            Item(2, "Item 2", "Description 2"),
            Item(3, "Item 3", "Description 3")
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Инициализируем adapter
        adapter = ItemAdapter(
            items,
            onItemClick = { item ->
                Toast.makeText(this, "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { item ->
                // Удаляем элемент из списка
                val position = items.indexOf(item)
                items.removeAt(position)
                // Уведомляем адаптер об удалении элемента
                adapter.notifyItemRemoved(position)
                Toast.makeText(this, "Removed: ${item.title}", Toast.LENGTH_SHORT).show()
            }
        )

        recyclerView.adapter = adapter
    }
}

data class Item(
    val id: Int,
    val title: String,
    val description: String
) {
    override fun toString(): String {
        return "Item: $title, Description: $description"
    }
}

class ItemAdapter(
    private val items: MutableList<Item>, // Используем MutableList
    private val onItemClick: (Item) -> Unit,
    private val onItemLongClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)

        fun bind(item: Item) {
            titleTextView.text = item.title
            descriptionTextView.text = item.description

            itemView.setOnClickListener {
                onItemClick(item)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
