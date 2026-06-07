package com.github.livingwithhippos.unchained.torrentdetails.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxFile
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxTorrent
import com.github.livingwithhippos.unchained.databinding.FragmentTorboxDetailsBinding
import com.github.livingwithhippos.unchained.lists.view.ListState
import com.github.livingwithhippos.unchained.torrentdetails.model.TorBoxFileListAdapter
import com.github.livingwithhippos.unchained.torrentdetails.model.TorBoxFileListener
import com.github.livingwithhippos.unchained.torrentdetails.viewmodel.TorBoxActionResult
import com.github.livingwithhippos.unchained.torrentdetails.viewmodel.TorBoxDetailsViewModel
import com.github.livingwithhippos.unchained.torrentdetails.viewmodel.TorBoxLinkResult
import com.github.livingwithhippos.unchained.utilities.extension.copyToClipboard
import com.github.livingwithhippos.unchained.utilities.extension.openExternalWebPage
import com.github.livingwithhippos.unchained.utilities.extension.openInExternalPlayer
import com.github.livingwithhippos.unchained.utilities.extension.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

/** Shows a TorBox torrent: its files (with per-file link resolution via requestdl) and controls. */
@AndroidEntryPoint
class TorBoxDetailsFragment : UnchainedFragment(), TorBoxFileListener {

    private val args: TorBoxDetailsFragmentArgs by navArgs()
    private val viewModel: TorBoxDetailsViewModel by viewModels()

    private var _binding: FragmentTorboxDetailsBinding? = null
    private val binding
        get() = _binding!!

    private var torrentId: Long = -1L
    private var isActive: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTorboxDetailsBinding.inflate(inflater, container, false)

        val torrent = args.torrent
        torrentId = torrent.rawId.toLongOrNull() ?: -1L

        binding.tvName.text = torrent.name
        binding.tvStatus.text = torrent.rawStatus
        binding.progressBar.progress = torrent.progress.toInt()

        val adapter = TorBoxFileListAdapter(this)
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter

        binding.bGetZip.setOnClickListener {
            if (torrentId < 0) return@setOnClickListener
            context?.showToast(R.string.torbox_resolving_link)
            viewModel.resolveZipLink(torrentId, binding.tvName.text.toString())
        }

        binding.bPauseResume.setOnClickListener {
            if (torrentId < 0) return@setOnClickListener
            if (isActive) viewModel.pauseTorrent(torrentId) else viewModel.resumeTorrent(torrentId)
        }

        binding.bDelete.setOnClickListener {
            if (torrentId < 0) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_removal)
                .setMessage(R.string.confirm_torrent_removal_description)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove) { _, _ -> viewModel.deleteTorrent(torrentId) }
                .show()
        }

        viewModel.torrentLiveData.observe(viewLifecycleOwner) { event ->
            if (!event.hasBeenHandled) populate(event.getContentIfNotHandled(), adapter)
        }

        viewModel.linkLiveData.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    is TorBoxLinkResult.Resolved -> showLinkDialog(result)
                    TorBoxLinkResult.Error -> context?.showToast(R.string.torbox_link_error)
                }
            }
        }

        viewModel.actionLiveData.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    TorBoxActionResult.Deleted -> {
                        activityViewModel.setListState(ListState.UpdateTorrent)
                        context?.showToast(R.string.torbox_torrent_deleted)
                        findNavController().popBackStack()
                    }
                    TorBoxActionResult.Paused -> {
                        context?.showToast(R.string.torbox_paused)
                        viewModel.loadTorrent(torrentId)
                    }
                    TorBoxActionResult.Resumed -> {
                        context?.showToast(R.string.torbox_resumed)
                        viewModel.loadTorrent(torrentId)
                    }
                    TorBoxActionResult.DownloadSaved -> {
                        context?.showToast(R.string.torbox_download_added)
                        activityViewModel.setListState(ListState.UpdateDownload)
                        findNavController().popBackStack()
                    }
                    TorBoxActionResult.Error -> context?.showToast(R.string.torbox_action_error)
                }
            }
        }

        if (torrentId >= 0) viewModel.loadTorrent(torrentId)

        return binding.root
    }

    private fun populate(torrent: TorBoxTorrent?, adapter: TorBoxFileListAdapter) {
        if (torrent == null) {
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        torrent.name?.let { binding.tvName.text = it }
        torrent.progress?.let { binding.progressBar.progress = (it * 100).toInt() }
        torrent.downloadState?.let { binding.tvStatus.text = it }
        isActive = torrent.active == true
        binding.bPauseResume.text =
            getString(if (isActive) R.string.torbox_pause else R.string.torbox_resume)

        val files = torrent.files.orEmpty()
        binding.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(files)
    }

    private fun showLinkDialog(resolved: TorBoxLinkResult.Resolved) {
        // The three actions a resolved link supports. "Download" sends it to the Downloads tab
        // (like
        // Real-Debrid unrestricted links), where the actual device download is started; the others
        // just open or copy the link.
        val actions =
            arrayOf(
                getString(R.string.download),
                getString(R.string.send_to_player),
                getString(R.string.torbox_open_link),
                getString(R.string.torbox_copy_link),
            )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.torbox_link_dialog_title, resolved.name))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> viewModel.saveDownload(resolved)
                    1 ->
                        openInExternalPlayer(
                            resolved.url,
                            viewModel.getDefaultPlayer(),
                            viewModel.getCustomPlayerPreference(),
                        )
                    2 -> context?.openExternalWebPage(resolved.url)
                    3 -> {
                        copyToClipboard("TorBox", resolved.url)
                        context?.showToast(R.string.link_copied)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onFileLinkClick(file: TorBoxFile) {
        if (torrentId < 0) return
        context?.showToast(R.string.torbox_resolving_link)
        viewModel.resolveFileLink(torrentId, file)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
