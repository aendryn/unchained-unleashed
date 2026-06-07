package com.github.livingwithhippos.unchained.user.view

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.edit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.authentication.viewmodel.TorBoxAuthResult
import com.github.livingwithhippos.unchained.authentication.viewmodel.TorBoxAuthViewModel
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.model.User
import com.github.livingwithhippos.unchained.data.model.torbox.TorBoxUser
import com.github.livingwithhippos.unchained.databinding.FragmentUserProfileBinding
import com.github.livingwithhippos.unchained.settings.view.SettingsActivity
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_REFERRAL_ASKED
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_REFERRAL_USE
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationEvent
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationState
import com.github.livingwithhippos.unchained.utilities.ACCOUNT_LINK
import com.github.livingwithhippos.unchained.utilities.REFERRAL_LINK
import com.github.livingwithhippos.unchained.utilities.TORBOX_ACCOUNT_LINK
import com.github.livingwithhippos.unchained.utilities.extension.getThemeColor
import com.github.livingwithhippos.unchained.utilities.extension.openExternalWebPage
import com.github.livingwithhippos.unchained.utilities.extension.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A simple [UnchainedFragment] subclass. Shows a user profile details. */
@AndroidEntryPoint
class UserProfileFragment : UnchainedFragment() {

    @Inject lateinit var preferences: SharedPreferences

    private val torBoxAuthViewModel: TorBoxAuthViewModel by viewModels()

    private var _binding: FragmentUserProfileBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        val view = binding.root

        lifecycleScope.launch {
            val realDebridConnected = activityViewModel.isRealDebridConnected()
            if (realDebridConnected) {
                val user: User? = activityViewModel.getCachedUser()
                if (user == null) {
                    activityViewModel.fetchUser()
                } else {
                    populateUserView(user)
                }
                binding.tvLoginDescription.text =
                    if (activityViewModel.isTokenPrivate()) getString(R.string.login_type_private)
                    else getString(R.string.login_type_open)
            }
            updateAccountsUi(realDebridConnected)
            // mirror RD: pull the TorBox account details (plan, expiry) for its card
            if (torBoxAuthViewModel.isAuthenticated()) torBoxAuthViewModel.fetchUser()
        }

        // open the TorBox account page (mirrors Real-Debrid's "Account Page")
        binding.bTorBoxAccount.setOnClickListener {
            context?.openExternalWebPage(TORBOX_ACCOUNT_LINK)
        }

        torBoxAuthViewModel.userLiveData.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { user -> populateTorBoxView(user) }
        }

        // connect Real-Debrid (shown only when it isn't connected): open the login screen without
        // dropping the existing TorBox session
        binding.bConnectRealDebrid.setOnClickListener {
            activityViewModel.transitionAuthenticationMachine(
                FSMAuthenticationEvent.OnConnectService
            )
        }

        // connect or disconnect TorBox
        binding.bTorBox.setOnClickListener {
            if (torBoxAuthViewModel.isAuthenticated()) {
                torBoxAuthViewModel.disconnect()
            } else {
                // open the auth screen focused on the TorBox key entry; it keeps the existing
                // Real-Debrid session and no longer bounces back to the hub
                safeNavigate(UserProfileFragmentDirections.actionUserToAuthenticationFragment())
            }
        }

        torBoxAuthViewModel.authResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                if (result is TorBoxAuthResult.Disconnected) {
                    context?.showToast(R.string.torbox_disconnected)
                    lifecycleScope.launch {
                        if (activityViewModel.isRealDebridConnected()) {
                            // still signed in to Real-Debrid: just refresh the hub
                            updateAccountsUi(realDebridConnected = true)
                        } else {
                            // no services left: go back to the login screen
                            activityViewModel.transitionAuthenticationMachine(
                                FSMAuthenticationEvent.OnLogout
                            )
                        }
                    }
                }
            }
        }

        activityViewModel.userLiveData.observe(viewLifecycleOwner) {
            populateUserView(it.peekContent())
            lifecycleScope.launch {
                if (activityViewModel.isTokenPrivate()) {
                    binding.tvLoginDescription.text = getString(R.string.login_type_private)
                } else {
                    binding.tvLoginDescription.text = getString(R.string.login_type_open)
                }
            }
        }

        binding.bAccount.setOnClickListener {
            // if we never asked, show a dialog
            if (!preferences.getBoolean(KEY_REFERRAL_ASKED, false)) {
                // set asked as true
                preferences.edit { putBoolean(KEY_REFERRAL_ASKED, true) }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.referral))
                    .setMessage(getString(R.string.referral_proposal))
                    .setNegativeButton(getString(R.string.decline)) { _, _ ->
                        preferences.edit { putBoolean(KEY_REFERRAL_USE, false) }
                        context?.openExternalWebPage(ACCOUNT_LINK)
                    }
                    .setPositiveButton(getString(R.string.accept)) { _, _ ->
                        preferences.edit { putBoolean(KEY_REFERRAL_USE, true) }
                        context?.openExternalWebPage(REFERRAL_LINK)
                    }
                    .show()
            } else {
                if (preferences.getBoolean(KEY_REFERRAL_USE, false))
                    context?.openExternalWebPage(REFERRAL_LINK)
                else context?.openExternalWebPage(ACCOUNT_LINK)
            }
        }

        activityViewModel.fsmAuthenticationState.observe(viewLifecycleOwner) {
            if (it != null) {
                when (it.peekContent()) {
                    is FSMAuthenticationState.WaitingUserAction -> {
                        // an error occurred, check it and eventually go back to the start fragment
                        val action = UserProfileFragmentDirections.actionUserToStartFragment()
                        safeNavigate(action)
                    }

                    FSMAuthenticationState.StartNewLogin -> {
                        // the user reset the login, go to the auth fragment
                        val action =
                            UserProfileFragmentDirections.actionUserToAuthenticationFragment()
                        safeNavigate(action)
                    }

                    FSMAuthenticationState.AuthenticatedOpenToken,
                    FSMAuthenticationState.AuthenticatedPrivateToken,
                    FSMAuthenticationState.AuthenticatedTorBox,
                    FSMAuthenticationState.RefreshingOpenToken -> {
                        // managed by activity
                    }

                    FSMAuthenticationState.CheckCredentials -> {
                        // shouldn't matter
                    }

                    FSMAuthenticationState.Start,
                    FSMAuthenticationState.WaitingToken,
                    FSMAuthenticationState.WaitingUserConfirmation -> {
                        // shouldn't happen
                    }
                }
            }
        }

        binding.bSettings.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            activityViewModel.requireNotificationPermissions()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateAccountsUi(realDebridConnected: Boolean) {
        val b = _binding ?: return

        // Real-Debrid card: the profile body only makes sense when connected; otherwise prompt.
        b.rdConnectedBody.visibility = if (realDebridConnected) View.VISIBLE else View.GONE
        b.bConnectRealDebrid.visibility = if (realDebridConnected) View.GONE else View.VISIBLE
        setStatusChip(b.tvRdStatusChip, realDebridConnected)

        // TorBox card
        val torBoxConnected = torBoxAuthViewModel.isAuthenticated()
        setStatusChip(b.tvTbStatusChip, torBoxConnected)
        b.tbConnectedBody.visibility = if (torBoxConnected) View.VISIBLE else View.GONE
        b.bTorBoxAccount.visibility = if (torBoxConnected) View.VISIBLE else View.GONE
        b.tvTorBoxStatus.text =
            if (torBoxConnected)
                getString(R.string.torbox_key_format, torBoxAuthViewModel.getMaskedKey())
            else getString(R.string.torbox_not_connected_summary)
        b.bTorBox.text =
            if (torBoxConnected) getString(R.string.torbox_disconnect_title)
            else getString(R.string.torbox_connect_button)
    }

    /** Sets the small header chip's "Connected"/"Not connected" text and tints it accordingly. */
    private fun setStatusChip(chip: android.widget.TextView, connected: Boolean) {
        chip.text =
            getString(
                if (connected) R.string.account_status_connected
                else R.string.account_status_not_connected
            )
        val attr =
            if (connected) android.R.attr.colorPrimary
            else com.google.android.material.R.attr.colorOnSurfaceVariant
        chip.setTextColor(requireContext().getThemeColor(attr))
    }

    /** Fills the TorBox card with account details (email, plan, expiry), mirroring the RD card. */
    private fun populateTorBoxView(user: TorBoxUser?) {
        val b = _binding ?: return
        if (user == null) {
            // keep whatever is shown; just don't surface partial/empty account info
            b.tvTorBoxEmail.visibility = View.GONE
            b.tvTorBoxPlan.visibility = View.GONE
            b.tvTorBoxExpiry.visibility = View.GONE
            return
        }
        b.tvTorBoxEmail.text = user.email
        b.tvTorBoxEmail.visibility = if (user.email.isNullOrBlank()) View.GONE else View.VISIBLE

        b.tvTorBoxPlan.text = torBoxPlanName(user.plan)
        b.tvTorBoxPlan.visibility = if (user.plan == null) View.GONE else View.VISIBLE

        val expiry = user.premiumExpiresAt?.take(10)
        if (expiry.isNullOrBlank()) {
            b.tvTorBoxExpiry.visibility = View.GONE
        } else {
            b.tvTorBoxExpiry.text = getString(R.string.torbox_premium_expires_format, expiry)
            b.tvTorBoxExpiry.visibility = View.VISIBLE
        }
    }

    private fun torBoxPlanName(plan: Int?): String =
        when (plan) {
            0 -> getString(R.string.torbox_plan_free)
            1 -> getString(R.string.torbox_plan_essential)
            2 -> getString(R.string.torbox_plan_pro)
            3 -> getString(R.string.torbox_plan_standard)
            null -> ""
            else -> getString(R.string.torbox_plan_unknown_format, plan)
        }

    fun populateUserView(user: User?) {
        user?.let {
            binding.tvName.text = it.username
            binding.tvMail.text = it.email
            if (it.premium > 0) {
                binding.tvPremium.text = getString(R.string.premium)
            } else {
                binding.tvPremium.text = getString(R.string.not_premium)
            }
            binding.tvPremiumDays.text =
                getString(R.string.premium_days_format, it.premium / 60 / 60 / 24)
            binding.tvPoints.text = getString(R.string.premium_points_format, it.points)
            binding.pointsBar.setProgressCompat(it.points, true)
        }
    }
}
