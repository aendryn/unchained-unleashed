package com.github.livingwithhippos.unchained.lists.view

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.data.model.DebridService
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrent
import com.github.livingwithhippos.unchained.data.model.UnifiedTorrentStatus
import com.github.livingwithhippos.unchained.databinding.ItemListTorrentBinding
import com.github.livingwithhippos.unchained.utilities.extension.getFileSizeString
import com.github.livingwithhippos.unchained.utilities.extension.getStatusTranslation

class TorrentListPagingAdapter(private val listener: TorrentListListener) :
    PagingDataAdapter<UnifiedTorrent, TorrentViewHolder>(DiffCallback()) {

    var tracker: SelectionTracker<UnifiedTorrent>? = null

    class DiffCallback : DiffUtil.ItemCallback<UnifiedTorrent>() {
        override fun areItemsTheSame(oldItem: UnifiedTorrent, newItem: UnifiedTorrent): Boolean =
            oldItem.unifiedId == newItem.unifiedId

        override fun areContentsTheSame(oldItem: UnifiedTorrent, newItem: UnifiedTorrent): Boolean {
            return oldItem.progress == newItem.progress &&
                oldItem.status == newItem.status &&
                oldItem.bytes == newItem.bytes
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
        val binding =
            ItemListTorrentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TorrentViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bindCell(item, tracker?.isSelected(item) ?: false)
        }
    }

    override fun getItemViewType(position: Int) = R.layout.item_list_torrent

    fun getTorrentItem(position: Int): UnifiedTorrent? {
        return super.getItem(position)
    }

    fun getPosition(unifiedId: String) = snapshot().indexOfFirst { it?.unifiedId == unifiedId }
}

class TorrentViewHolder(
    private val binding: ItemListTorrentBinding,
    private val listener: TorrentListListener,
) : RecyclerView.ViewHolder(binding.root) {

    var mItem: UnifiedTorrent? = null

    fun bindCell(item: UnifiedTorrent, selected: Boolean) {
        mItem = item
        binding.selectionIndicator.visibility = if (selected) View.VISIBLE else View.GONE

        binding.tvTitle.text =
            if (item.status == UnifiedTorrentStatus.READY)
                // "ready" makes it clearer the torrent is NOT downloaded on the phone
                binding.root.context.getStatusTranslation("ready")
            else binding.root.context.getStatusTranslation(item.rawStatus)

        if (item.progress >= 0 && item.progress < 100) {
            binding.tvProgress.text =
                itemView.context.getString(R.string.percent_format, item.progress)
            binding.tvProgress.visibility = View.VISIBLE
        } else {
            binding.tvProgress.visibility = View.GONE
        }
        binding.tvName.text = item.name
        binding.tvSize.text = getFileSizeString(itemView.context, item.bytes)

        // service badge
        binding.ivService.setImageResource(
            when (item.service) {
                DebridService.REAL_DEBRID -> R.drawable.ic_service_realdebrid
                DebridService.TORBOX -> R.drawable.ic_service_torbox
            }
        )
        binding.ivService.contentDescription =
            when (item.service) {
                DebridService.REAL_DEBRID -> binding.root.context.getString(R.string.real_debrid)
                DebridService.TORBOX -> binding.root.context.getString(R.string.torbox)
            }

        binding.cvTorrent.setOnClickListener { listener.onClick(item) }
    }

    fun getItemDetails(): ItemDetailsLookup.ItemDetails<UnifiedTorrent> =
        object : ItemDetailsLookup.ItemDetails<UnifiedTorrent>() {
            override fun getPosition(): Int = layoutPosition

            override fun getSelectionKey(): UnifiedTorrent? = mItem
        }
}

class TorrentDetailsLookup(private val recyclerView: RecyclerView) :
    ItemDetailsLookup<UnifiedTorrent>() {
    override fun getItemDetails(event: MotionEvent): ItemDetails<UnifiedTorrent>? {
        val view = recyclerView.findChildViewUnder(event.x, event.y)
        if (view != null) {
            return (recyclerView.getChildViewHolder(view) as TorrentViewHolder).getItemDetails()
        }
        return null
    }
}

interface TorrentListListener {
    fun onClick(item: UnifiedTorrent)
}

class TorrentKeyProvider(private val adapter: TorrentListPagingAdapter) :
    ItemKeyProvider<UnifiedTorrent>(SCOPE_MAPPED) {
    override fun getKey(position: Int): UnifiedTorrent? {
        return adapter.getTorrentItem(position)
    }

    override fun getPosition(key: UnifiedTorrent): Int {
        return adapter.getPosition(key.unifiedId)
    }
}
