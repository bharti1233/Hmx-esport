package com.hmx.arena.ui.common

object LegalText {
    const val SUPPORT_EMAIL = "support@hmxarena.app"

    val terms = """
        HMX Arena Terms of Service
        Last updated: 3 November 2026

        1. The service
        HMX Arena is a competitive gaming platform for organising and scoring Free Fire
        tournaments. The app is operated by HMX Arena. By creating an account you agree
        to these terms and to the Fair Play policy.

        2. Eligibility
        You must be at least 13 years old and legally able to enter into these terms.
        One account per person. Account sharing, selling and boosting are prohibited.

        3. Accounts and game identity
        You must provide a valid email address and the in-game UID and in-game name used
        for the tournaments you enter. You are responsible for keeping your password
        secure. Notify us immediately if you believe your account is compromised.

        4. Paid features
        Paid tournaments, wallet funding, withdrawals, refunds and payouts are currently
        DISABLED. No payment is accepted in the app. When paid features are enabled after
        compliance approval, additional payment, KYC and refund terms will apply and will
        be presented before any payment is made.

        5. Tournament conduct
        You must follow each tournament's rules, check in on time and play with the
        account you registered. Results must be submitted with genuine evidence. False
        evidence, cheating, collusion or abusive behaviour can lead to disqualification,
        forfeiture of points and account suspension.

        6. Scores and disputes
        Match results are provisional until verified. You can raise a dispute within the
        window shown on the result. Our decision on a dispute is final for that match.

        7. Prizes
        Non-cash rewards and leaderboard points may be awarded. Cash prizes are not
        currently offered. Nothing in the app constitutes gambling or a lottery.

        8. Availability
        The service is provided on an "as is" basis. We may change, suspend or withdraw
        features, and we aim to give reasonable notice of material changes.

        9. Termination
        You may stop using the service at any time and request account deletion in
        Settings. We may suspend accounts that breach these terms or the Fair Play policy.

        10. Contact
        Questions: $SUPPORT_EMAIL
    """.trimIndent()

    val privacy = """
        HMX Arena Privacy Policy
        Last updated: 3 November 2026

        What we collect
        - Account details: email address, username, display name and password hash.
        - Game details: Free Fire in-game name, UID and level when you add them.
        - Competition data: registrations, team membership, match results and disputes.
        - Evidence you upload for a result (screenshots). Evidence is stored in a private
          bucket and only visible to you, the opposing participant and tournament staff.
        - Support messages and reports you send.

        What we do not collect
        No payment details are collected because payments are disabled. We do not request
        your game password and the app never asks you to share one.

        How we use data
        To operate tournaments, verify results, calculate leaderboards, prevent fraud and
        provide support. We do not sell personal data.

        Storage and security
        Data is stored in Supabase (managed Postgres and object storage) with row-level
        security, least-privilege database roles and private evidence buckets. Access
        tokens are kept in app-private storage; backup is disabled on the app.

        Your choices
        You can edit your profile, game account, notifications and avatar in the app.
        You can request account deletion in Settings, which soft-deletes the profile and
        revokes access. Where we must keep a record for fraud prevention, legal or audit
        purposes, limited records may be retained.

        Children
        The service is not directed to children under 13. Contact us if you believe a
        child under 13 has created an account.

        Contact
        Data requests: $SUPPORT_EMAIL
    """.trimIndent()

    val fairPlay = """
        HMX Arena Fair Play Policy

        1. Play on your own registered account. Account sharing is not allowed.
        2. No cheats, mods, scripts, emulators where mobile-only is specified, teaming,
           hacking or exploiting bugs.
        3. Submit genuine screenshots. Cropped, edited or borrowed evidence is a breach.
        4. Keep lobby chat and voice respectful. Harassment, hate speech and threats are
           prohibited.
        5. Follow check-in times. Missing check-in can free your slot for a waitlisted
           player.
        6. Disputes must be raised through the app with a clear reason. Abusive or
           repeated bad-faith disputes can lead to restrictions.

        Penalties include match forfeiture, point deduction, tournament disqualification
        and account suspension. Serious cases are reported to the game publisher where
        appropriate.
    """.trimIndent()

    val refunds = """
        HMX Arena Refund Policy

        Paid tournaments and wallet funding are currently disabled. No payment can be
        made in the app and no balance can be purchased.

        When paid entry is enabled after compliance approval:
        - Entry fees are charged only through the approved payment partner shown at
          checkout.
        - If a tournament is cancelled before it starts, entry fees are refunded to the
          original payment method.
        - If you withdraw before registration closes, the refund terms shown at checkout
          apply. After check-in, fees are generally non-refundable because the slot is
          committed.
        - Failed or double charges are investigated and refunded within the timelines
          required by the payment partner and applicable law.

        This policy does not limit rights available under applicable consumer law.
        Contact $SUPPORT_EMAIL for any payment question.
    """.trimIndent()

    fun forKind(kind: String): Pair<String, String> = when (kind) {
        "privacy" -> "Privacy Policy" to privacy
        "fair-play" -> "Fair Play" to fairPlay
        "refunds" -> "Refund Policy" to refunds
        else -> "Terms of Service" to terms
    }
}
