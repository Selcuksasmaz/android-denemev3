package com.example.yuztanima

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PersonAdapter(
    private val onDeleteClick: (Person) -> Unit
) : ListAdapter<Person, PersonAdapter.PersonViewHolder>(PersonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = getItem(position)
        holder.bind(person)
    }

    inner class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPersonFace: ImageView = itemView.findViewById(R.id.ivPersonFace)
        private val tvPersonName: TextView = itemView.findViewById(R.id.tvPersonName)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(person: Person) {
            tvPersonName.text = person.name

            // İlk kayıtlı resmi (genellikle front) varsa göster
            val firstImagePath = person.faceImages["front"]
            if (firstImagePath != null) {
                try {
                    val file = File(firstImagePath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        ivPersonFace.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    ivPersonFace.setImageResource(R.drawable.ic_person)
                }
            } else {
                ivPersonFace.setImageResource(R.drawable.ic_person)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(person)
            }
        }
    }

    class PersonDiffCallback : DiffUtil.ItemCallback<Person>() {
        override fun areItemsTheSame(oldItem: Person, newItem: Person): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Person, newItem: Person): Boolean {
            return oldItem == newItem
        }
    }
}