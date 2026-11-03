package com.hmx.arena.core.util

import com.hmx.arena.core.model.Tournament

enum class RegistrationBlocker {
    AlreadyRegistered,
    GameAccountRequired,
    TeamRequired,
    RegistrationClosed,
}

/**
 * Pure eligibility rules for tournament registration. The server remains the
 * source of truth; this keeps the client from sending a request that is known
 * to fail and is directly unit-tested.
 */
object RegistrationRules {
    fun blocker(
        tournament: Tournament,
        hasGameAccount: Boolean,
        hasTeam: Boolean,
        alreadyRegistered: Boolean,
    ): RegistrationBlocker? = when {
        alreadyRegistered -> RegistrationBlocker.AlreadyRegistered
        !hasGameAccount -> RegistrationBlocker.GameAccountRequired
        tournament.maxTeamSize > 1 && !hasTeam -> RegistrationBlocker.TeamRequired
        tournament.status != "registration_open" -> RegistrationBlocker.RegistrationClosed
        else -> null
    }

    fun message(blocker: RegistrationBlocker): String = when (blocker) {
        RegistrationBlocker.AlreadyRegistered -> "You are already registered for this tournament."
        RegistrationBlocker.GameAccountRequired -> "Add your Free Fire IGN and UID before registering."
        RegistrationBlocker.TeamRequired -> "Create or join a team before registering for this tournament."
        RegistrationBlocker.RegistrationClosed -> "Registration is not open right now."
    }
}
