package com.hmx.arena.data.repository

import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.Team
import com.hmx.arena.core.model.TeamInvitation
import com.hmx.arena.core.model.TeamMember
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.remote.RemoteDataSource
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.SupabaseConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class TeamDetail(
    val team: Team,
    val members: List<TeamMember>,
    val profiles: List<Profile>,
) {
    fun profileFor(userId: String): Profile? = profiles.firstOrNull { it.id == userId }
    val owner: Profile? get() = profileFor(team.ownerId)
}

@Singleton
class TeamRepository @Inject constructor(
    private val remote: RemoteDataSource,
    private val session: SessionManager,
) {
    suspend fun myTeam(): TeamDetail? {
        val membership = remote.selectOne(
            "team_members",
            mapOf("user_id" to "eq.${uid()}"),
            TeamMember.serializer(),
        ) ?: return null
        return teamDetail(membership.teamId)
    }

    suspend fun teamDetail(teamId: String): TeamDetail? {
        // The team row and its member list are independent reads; the profile
        // lookup below still depends on the member ids.
        return coroutineScope {
            val teamDeferred = async {
                remote.selectOne("teams", mapOf("id" to "eq.$teamId"), Team.serializer())
            }
            val membersDeferred = async {
                remote.select(
                    "team_members",
                    mapOf("team_id" to "eq.$teamId", "order" to "role.asc"),
                    TeamMember.serializer(),
                )
            }
            val team = teamDeferred.await() ?: return@coroutineScope null
            val members = membersDeferred.await()
            val profiles = if (members.isEmpty()) {
                emptyList()
            } else {
                remote.select(
                    "profiles",
                    mapOf("id" to "in.(${members.joinToString(",") { it.userId }})"),
                    Profile.serializer(),
                )
            }
            TeamDetail(team, members, profiles)
        }
    }

    suspend fun createTeam(name: String, tag: String, game: String = "free_fire"): Team = remote.rpc(
        "create_team",
        buildJsonObject {
            put("p_name", name)
            put("p_tag", tag)
            put("p_game", game)
        },
        Team.serializer(),
    )

    suspend fun updateTeam(teamId: String, name: String, tag: String) {
        remote.update(
            "teams",
            mapOf("id" to "eq.$teamId"),
            buildJsonObject {
                put("name", name)
                put("tag", tag)
            },
        )
    }

    suspend fun invite(teamId: String, username: String): TeamInvitation = remote.rpc(
        "invite_team_member",
        buildJsonObject {
            put("p_team_id", teamId)
            put("p_username", username)
        },
        TeamInvitation.serializer(),
    )

    suspend fun myInvitations(): List<TeamInvitation> = remote.select(
        "team_invitations",
        mapOf("invited_user_id" to "eq.${uid()}", "status" to "eq.pending", "order" to "created_at.desc"),
        TeamInvitation.serializer(),
    )

    suspend fun respond(invitationId: String, accept: Boolean) {
        remote.rpcUnit(
            "respond_team_invitation",
            buildJsonObject {
                put("p_invitation_id", invitationId)
                put("p_accept", accept)
            },
        )
    }

    suspend fun leave(teamId: String) {
        remote.rpcUnit("leave_team", buildJsonObject { put("p_team_id", teamId) })
    }

    suspend fun transferOwnership(teamId: String, newOwnerId: String) {
        remote.rpcUnit(
            "transfer_team_ownership",
            buildJsonObject {
                put("p_team_id", teamId)
                put("p_new_owner", newOwnerId)
            },
        )
    }

    suspend fun deleteTeam(teamId: String) {
        remote.delete("teams", mapOf("id" to "eq.$teamId"))
    }

    suspend fun uploadLogo(teamId: String, bytes: ByteArray, mime: String): String {
        val path = "$teamId/logo-${System.currentTimeMillis()}.jpg"
        remote.upload(SupabaseConfig.TEAM_LOGOS_BUCKET, path, bytes, mime)
        val url = remote.publicUrl(SupabaseConfig.TEAM_LOGOS_BUCKET, path)
        remote.update("teams", mapOf("id" to "eq.$teamId"), buildJsonObject { put("logo_url", url) })
        return url
    }

    suspend fun invitationsForTeam(teamId: String): List<TeamInvitation> = remote.select(
        "team_invitations",
        mapOf("team_id" to "eq.$teamId", "status" to "eq.pending"),
        TeamInvitation.serializer(),
    )

    private fun uid(): String =
        session.snapshotUserId() ?: throw ApiException("Please sign in again.")
}
