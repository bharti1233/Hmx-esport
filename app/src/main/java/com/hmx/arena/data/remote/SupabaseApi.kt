package com.hmx.arena.data.remote

import com.hmx.arena.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.io.IOException
import java.util.concurrent.TimeUnit

object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val key: String = BuildConfig.SUPABASE_KEY
    val restUrl: String = "$url/"
    const val AUTH_REDIRECT = "hmxarena://auth/reset"
    const val RESULT_EVIDENCE_BUCKET = "result-evidence"
    const val AVATARS_BUCKET = "avatars"
    const val TEAM_LOGOS_BUCKET = "team-logos"
    const val TOURNAMENT_BANNERS_BUCKET = "tournament-banners"
    const val CATEGORY_LOGOS_BUCKET = "category-logos"
    const val MATCH_ASSETS_BUCKET = "match-assets"
}

interface AuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement

    @POST("auth/v1/token")
    suspend fun token(
        @Query("grant_type") grantType: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonElement

    @POST("auth/v1/verify")
    suspend fun verify(@Body body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement

    @POST("auth/v1/recover")
    suspend fun recover(
        @Body body: kotlinx.serialization.json.JsonObject,
        @Query("redirect_to") redirectTo: String? = null,
    ): retrofit2.Response<Unit>

    @POST("auth/v1/resend")
    suspend fun resend(@Body body: kotlinx.serialization.json.JsonObject): retrofit2.Response<Unit>

    @POST("auth/v1/logout")
    suspend fun logout(): retrofit2.Response<Unit>

    @GET("auth/v1/user")
    suspend fun me(
        @Header("Authorization") authorization: String? = null,
    ): kotlinx.serialization.json.JsonElement

    @PUT("auth/v1/user")
    suspend fun updateUser(@Body body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement
}

interface RestApi {
    @GET("rest/v1/{table}")
    suspend fun select(
        @Path("table") table: String,
        @QueryMap params: Map<String, String>,
    ): kotlinx.serialization.json.JsonElement

    @POST("rest/v1/rpc/{fn}")
    suspend fun rpc(
        @Path("fn") fn: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonElement

    /**
     * Void RPCs answer 204 No Content. Keeping the response untyped avoids the
     * empty-body deserialization failure that used to surface as a generic
     * error after a successful cancellation.
     */
    @POST("rest/v1/rpc/{fn}")
    suspend fun rpcVoid(
        @Path("fn") fn: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): retrofit2.Response<Unit>

    @PATCH("rest/v1/{table}")
    suspend fun update(
        @Path("table") table: String,
        @QueryMap params: Map<String, String>,
        @Body body: kotlinx.serialization.json.JsonObject,
        // return=representation makes PostgREST perform SELECT * on the table.
        // Column-level grants (0007) intentionally hide contact columns from
        // authenticated users, so that produced a spurious 42501. Callers that
        // need the row read it back through the appropriate view or RPC.
        // A minimal response is 204 with no body, hence Response<Unit>.
        @Header("Prefer") prefer: String = "return=minimal",
    ): retrofit2.Response<Unit>

    @HEAD("rest/v1/{table}")
    suspend fun head(
        @Path("table") table: String,
        @QueryMap params: Map<String, String>,
        @Header("Prefer") prefer: String = "count=exact",
    ): retrofit2.Response<Unit>

    @POST("rest/v1/{table}")
    suspend fun insert(
        @Path("table") table: String,
        @Body body: kotlinx.serialization.json.JsonElement,
        @Header("Prefer") prefer: String = "return=minimal",
    ): retrofit2.Response<Unit>

    @DELETE("rest/v1/{table}")
    suspend fun delete(
        @Path("table") table: String,
        @QueryMap params: Map<String, String>,
        @Header("Prefer") prefer: String = "return=minimal",
    ): retrofit2.Response<Unit>
}

interface StorageApi {
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun upload(
        @Path("bucket") bucket: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: RequestBody,
        @Header("Content-Type") contentType: String,
        @Header("x-upsert") upsert: String = "true",
    ): retrofit2.Response<Unit>

    @POST("storage/v1/object/sign/{bucket}/{path}")
    suspend fun sign(
        @Path("bucket") bucket: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonElement
}

object Query {
    fun eq(column: String, value: Any?): Pair<String, String> = column to "eq.$value"
    fun neq(column: String, value: Any?): Pair<String, String> = column to "neq.$value"
    fun inList(column: String, values: List<String>): Pair<String, String> =
        column to "in.(${values.joinToString(",")})"
    fun ilike(column: String, value: String): Pair<String, String> = column to "ilike.*$value*"
    fun gte(column: String, value: String): Pair<String, String> = column to "gte.$value"
    fun lt(column: String, value: String): Pair<String, String> = column to "lt.$value"
    fun isNull(column: String): Pair<String, String> = column to "is.null"
    fun order(column: String, ascending: Boolean = true): Pair<String, String> =
        "order" to "$column.${if (ascending) "asc" else "desc"}"
    fun limit(n: Int): Pair<String, String> = "limit" to n.toString()
    fun offset(n: Int): Pair<String, String> = "offset" to n.toString()
    fun select(columns: String = "*"): Pair<String, String> = "select" to columns
}

class ApiException(
    val userMessage: String,
    val technical: String? = null,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

private val DB_MESSAGES = mapOf(
    "not_authenticated" to "Please sign in again to continue.",
    "registration_closed" to "Registration for this tournament has closed.",
    "registration_window_closed" to "Registration is not open right now.",
    "tournament_not_found" to "This tournament could not be found.",
    "already_registered" to "You are already registered for this tournament.",
    "tournament_full" to "This tournament is full.",
    "paid_tournaments_disabled" to "Paid tournaments are not available yet.",
    "game_account_required" to "Add your Free Fire details before joining.",
    "eligibility_points" to "You need more leaderboard points to enter.",
    "eligibility_level" to "Your in-game level does not meet the requirement.",
    "eligibility_region" to "This tournament is not available in your region.",
    "team_required" to "This tournament requires a team.",
    "solo_only_tournament" to "This tournament is for solo players only.",
    "team_too_small" to "Your team roster is below the minimum size.",
    "team_too_large" to "Your team roster is above the maximum size.",
    "not_team_member" to "You are not a member of this team.",
    "team_game_mismatch" to "This team is registered for a different game.",
    "not_match_participant" to "Room details are only available to registered players.",
    "match_not_started" to "Room details are not released yet.",
    "result_already_finalized" to "This result has already been finalised.",
    "not_match_participant_result" to "You are not a participant in this match.",
    "result_not_found" to "This result could not be found.",
    "result_finalized" to "This result is already final and cannot be disputed.",
    "dispute_not_found" to "This dispute could not be found.",
    "already_in_team" to "You are already in a team.",
    "team_not_found" to "This team could not be found.",
    "team_full" to "This team already has the maximum number of players.",
    "not_team_owner" to "Only the team owner can do that.",
    "owner_must_transfer" to "Transfer ownership before leaving the team.",
    "invalid_referral_code" to "That referral code is not valid.",
    "already_referred" to "You have already used a referral code.",
    "self_referral" to "You cannot use your own referral code.",
    "referral_window_expired" to "Referral codes can only be claimed in your first 7 days.",
    "referrals_disabled" to "Referrals are temporarily unavailable.",
    "player_not_found" to "No player with that username.",
    "player_already_in_team" to "That player is already in a team.",
    "cannot_invite_self" to "You cannot invite yourself.",
    "invitation_not_found" to "This invitation is no longer available.",
    "invalid_placement" to "Placement must be 1 or higher.",
    "invalid_reason" to "Please describe the issue in at least 10 characters.",
    "invalid_evidence_path" to "The evidence upload could not be verified.",
    "not_admin" to "You do not have permission to do that.",
    "immutable_record" to "This record cannot be modified.",
    "account_deleted" to "This account has been deleted.",
    "invalid_category" to "Choose a valid category.",
    "invalid_subject" to "Please enter a subject.",
    "invalid_message" to "Please enter a message.",
    "invalid_team_name" to "Team name must be at least 3 characters.",
    "invalid_team_tag" to "Team tag must be 2-5 characters.",
    "invalid_game" to "Choose a supported game.",
    "invalid_display_name" to "Display name must be 2-30 characters.",
    "invalid_bio" to "Your bio must be 160 characters or fewer.",
    "invalid_region" to "Choose a valid region.",
    "invalid_ign" to "Enter your in-game name (2-24 characters).",
    "invalid_game_uid" to "Enter the UID shown in your Free Fire profile.",
    "invalid_level" to "Enter a level between 1 and 100.",
    "invalid_game_identity" to "That game account does not belong to you.",
    "profile_not_found" to "Your profile could not be found. Sign in again.",
    "match_not_found" to "This match could not be found.",
    "match_not_available" to "This match is not open for registration.",
    "match_registration_closed" to "Registration for this match has closed.",
    "match_registration_not_found" to "This match registration could not be found.",
    "match_full" to "This match is full.",
    "already_registered" to "You are already registered.",
    "capacity_below_registrations" to "Capacity cannot be lower than the number of registered players.",
    "category_unavailable" to "That category is inactive. Choose another category.",
    "game_mode_unavailable" to "That game mode is inactive. Choose another game mode.",
    "tournament_type_unavailable" to "That tournament type is inactive. Choose another type.",
    "category_in_use" to "This category is assigned to tournaments or matches. Deactivate it instead.",
    "game_mode_in_use" to "This game mode is assigned to tournaments or matches. Deactivate it instead.",
    "tournament_type_in_use" to "This tournament type is assigned to tournaments or matches. Deactivate it instead.",
    "slug_taken" to "That identifier is already in use.",
    "name_required" to "Enter a name.",
    "title_required" to "Enter a title.",
    "schedule_required" to "Choose a date and time.",
    "start_required" to "Choose a start date and time.",
    "registration_close_required" to "Choose when registration closes.",
    "invalid_registration_window" to "Registration must close after it opens.",
    "start_before_registration_close" to "The start time must be after registration closes.",
    "end_before_start" to "The end time must be after the start time.",
    "match_locked_confirmation_required" to "This match has started. Confirm the change before saving.",
    "completed_match_is_locked" to "A completed match cannot be reopened.",
    "completed_tournament_is_locked" to "A completed tournament cannot be reopened.",
    "tournament_has_registrations" to "This tournament has registrations and cannot be deleted.",
    "only_draft_tournaments_can_be_deleted" to "Only draft tournaments can be deleted.",
    "match_has_registrations" to "This match has registrations and cannot be deleted.",
    "archived_tournament_must_be_draft" to "Archive a tournament only while it is a draft.",
    "tournament_type_not_found" to "This tournament type could not be found.",
    "category_not_found" to "This category could not be found.",
    "game_mode_not_found" to "This game mode could not be found.",
)

fun mapDbMessage(raw: String?): String {
    if (raw.isNullOrBlank()) return "Something went wrong. Please try again."
    val cleaned = raw.trim().removePrefix("ERROR:").trim()
    DB_MESSAGES[cleaned]?.let { return it }
    DB_MESSAGES.entries.firstOrNull { cleaned.contains(it.key) }?.let { return it.value }
    return when {
        cleaned.contains("duplicate key") && cleaned.contains("game_uid") ->
            "That in-game UID is already linked to another account."
        cleaned.contains("duplicate key") && cleaned.contains("username") ->
            "That username is already taken."
        cleaned.contains("duplicate key") ->
            "You have already done that."
        cleaned.contains("permission denied") ->
            "You do not have permission to do that."
        cleaned.contains("password") && cleaned.contains("6") ->
            "Password must be at least 6 characters."
        cleaned.contains("Invalid login credentials") ->
            "Incorrect email or password."
        cleaned.contains("Email not confirmed") ->
            "Verify your email before signing in."
        cleaned.contains("User already registered") ->
            "An account with this email already exists."
        cleaned.contains("rate limit") || cleaned.contains("over_email_send_rate_limit") ->
            "Too many attempts. Wait a moment and try again."
        cleaned.contains("JWT") ->
            "Your session expired. Please sign in again."
        else -> "Something went wrong. Please try again."
    }
}

suspend fun <T> guardApi(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: ApiException) {
        throw e
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val message = body?.let { parseErrorMessage(it) }
        throw ApiException(mapDbMessage(message), body ?: e.message(), e)
    } catch (e: IOException) {
        throw ApiException(
            "You appear to be offline. Check your connection and try again.",
            e.message,
            e,
        )
    } catch (e: Exception) {
        throw ApiException("Something went wrong. Please try again.", e.message, e)
    }
}

fun parseErrorMessage(body: String): String? {
    val json = runCatching {
        Json.parseToJsonElement(body).let { el ->
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return@let null
            (obj["message"] ?: obj["error_description"] ?: obj["msg"] ?: obj["error"])?.toString()?.trim('"')
        }
    }.getOrNull()
    return json
}

object Http {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    val mediaType = "application/json".toMediaType()

    fun bareClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
