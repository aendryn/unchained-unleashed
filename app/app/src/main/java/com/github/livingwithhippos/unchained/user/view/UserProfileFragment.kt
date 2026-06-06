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
import coil.load
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.authentication.viewmodel.TorBoxAuthResult
import com.github.livingwithhippos.unchained.authentication.viewmodel.TorBoxAuthViewModel
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.model.User
import com.github.livingwithhippos.unchained.databinding.FragmentUserProfileBinding
import com.github.livingwithhippos.unchained.settings.view.SettingsActivity
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_REFERRAL_ASKED
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_REFERRAL_USE
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationEvent
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationState
import com.github.livingwithhippos.unchained.utilities.ACCOUNT_LINK
import com.github.livingwithhippos.unchained.utilities.REFERRAL_LINK
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
                // the validated TorBox key entry lives in Settings; opening it here avoids the auth
                // screen bouncing back when the user is already signed in to another service
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
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
        // Real-Debrid profile views are only meaningful when RD is connected
        val rdVisibility = if (realDebridConnected) View.VISIBLE else View.GONE
        b.tvName.visibility = rdVisibility
        b.tvMail.visibility = rdVisibility
        b.ivProfilePic.visibility = rdVisibility
        b.cvPremium.visibility = rdVisibility
        b.tvLoginDescription.visibility = rdVisibility
        b.divider.visibility = rdVisibility
        b.tvDescription.visibility = rdVisibility
        b.bAccount.visibility = rdVisibility
        b.bConnectRealDebrid.visibility = if (realDebridConnected) View.GONE else View.VISIBLE

        // TorBox row
        val torBoxConnected = torBoxAuthViewModel.isAuthenticated()
        b.tvTorBoxStatus.text =
            if (torBoxConnected)
                getString(R.string.torbox_connected_summary, torBoxAuthViewModel.getMaskedKey())
            else getString(R.string.torbox_not_connected_summary)
        b.bTorBox.text =
            if (torBoxConnected) getString(R.string.torbox_disconnect_title)
            else getString(R.string.torbox_connect_button)
    }

    fun populateUserView(user: User?) {
        user?.let {
            binding.tvName.text = it.username
            binding.tvMail.text = it.email
            // todo: check https://coil-kt.github.io/coil/image_loaders/#caching
            binding.ivProfilePic.load(it.avatar) { crossfade(true) }
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
