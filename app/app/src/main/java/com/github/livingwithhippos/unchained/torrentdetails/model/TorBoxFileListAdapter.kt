package com.github.livingwithhippos.unchained.torrentdetails.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxFile
import com.github.livingwithhippos.unchained.databinding.ItemTorboxFileBinding
import com.github.livingwithhippos.unchained.utilities.extension.getFileSizeString

class TorBoxFileListAdapter(private val listener: TorBoxFileListener) :
    ListAdapter<TorBoxFile, TorBoxFileListAdapter.FileViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding =
            ItemTorboxFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    class FileViewHolder(private val binding: ItemTorboxFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: TorBoxFile, listener: TorBoxFileListener) {
            binding.tvFileName.text =
                file.shortName
                    ?: file.name
                    ?: binding.root.context.getString(R.string.torbox_unknown_file)
            binding.tvFileSize.text = getFileSizeString(binding.root.context, file.size ?: 0L)
            binding.bFileLink.setOnClickListener { listener.onFileLinkClick(file) }
            binding.fileCard.setOnClickListener { listener.onFileLinkClick(file) }
        }
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<TorBoxFile>() {
                override fun areItemsTheSame(oldItem: TorBoxFile, newItem: TorBoxFile): Boolean =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: TorBoxFile, newItem: TorBoxFile): Boolean =
                    oldItem == newItem
            }
    }
}

interface TorBoxFileListener {
    fun onFileLinkClick(file: TorBoxFile)
}
