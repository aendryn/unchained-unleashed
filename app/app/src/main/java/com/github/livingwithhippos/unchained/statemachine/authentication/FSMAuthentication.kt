package com.github.livingwithhippos.unchained.statemachine.authentication

import com.github.livingwithhippos.unchained.data.model.UserAction

sealed class FSMAuthenticationState {
    data object Start : FSMAuthenticationState()

    data object CheckCredentials : FSMAuthenticationState()

    data class WaitingUserAction(val action: UserAction?) : FSMAuthenticationState()

    data object StartNewLogin : FSMAuthenticationState()

    /**
     * No service is connected, but the user is shown the accounts hub (the User screen with a
     * "Connect" button per service) instead of being dropped straight into the sign-in flow. The
     * flow only starts when the user taps a Connect button.
     */
    data object AccountsHub : FSMAuthenticationState()

    data object WaitingUserConfirmation : FSMAuthenticationState()

    data object WaitingToken : FSMAuthenticationState()

    data object AuthenticatedOpenToken : FSMAuthenticationState()

    data object AuthenticatedPrivateToken : FSMAuthenticationState()

    data object AuthenticatedTorBox : FSMAuthenticationState()

    data object RefreshingOpenToken : FSMAuthenticationState()
}

sealed class FSMAuthenticationEvent {
    data object OnAvailableCredentials : FSMAuthenticationEvent()

    data object OnMissingCredentials : FSMAuthenticationEvent()

    data object OnTorBoxAuthenticated : FSMAuthenticationEvent()

    /** The user, while already signed in, asked to connect an additional service. */
    data object OnConnectService : FSMAuthenticationEvent()

    data object OnPrivateToken : FSMAuthenticationEvent()

    data object OnNotWorking : FSMAuthenticationEvent()

    data object OnAuthLoaded : FSMAuthenticationEvent()

    data class OnUserActionNeeded(val action: UserAction) : FSMAuthenticationEvent()

    data object OnUserActionRetry : FSMAuthenticationEvent()

    data object OnUserActionReset : FSMAuthenticationEvent()

    data object OnUserConfirmationLoaded : FSMAuthenticationEvent()

    data object OnUserConfirmationMissing : FSMAuthenticationEvent()

    data object OnUserConfirmationExpired : FSMAuthenticationEvent()

    data object OnOpenTokenLoaded : FSMAuthenticationEvent()

    data object OnWorkingPrivateToken : FSMAuthenticationEvent()

    data object OnWorkingOpenToken : FSMAuthenticationEvent()

    data object OnExpiredOpenToken : FSMAuthenticationEvent()

    data object OnRefreshed : FSMAuthenticationEvent()

    data object OnLogout : FSMAuthenticationEvent()

    data object OnAuthenticationError : FSMAuthenticationEvent()
}

sealed class FSMAuthenticationSideEffect {
    data object CheckingCredentials : FSMAuthenticationSideEffect()

    data object PostActionNeeded : FSMAuthenticationSideEffect()

    data object PostNewLogin : FSMAuthenticationSideEffect()

    data object PostAccountsHub : FSMAuthenticationSideEffect()

    data object ResetAuthentication : FSMAuthenticationSideEffect()

    data object PostWaitUserConfirmation : FSMAuthenticationSideEffect()

    data object PostWaitToken : FSMAuthenticationSideEffect()

    data object PostAuthenticatedOpen : FSMAuthenticationSideEffect()

    data object PostAuthenticatedPrivate : FSMAuthenticationSideEffect()

    data object PostAuthenticatedTorBox : FSMAuthenticationSideEffect()

    data object PostRefreshingToken : FSMAuthenticationSideEffect()
}

// support class
sealed class CurrentFSMAuthentication {
    // auth is ok
    data object Authenticated : CurrentFSMAuthentication()

    // auth may become ok
    data object Waiting : CurrentFSMAuthentication()

    // auth is not ok
    data object Unauthenticated : CurrentFSMAuthentication()
}
