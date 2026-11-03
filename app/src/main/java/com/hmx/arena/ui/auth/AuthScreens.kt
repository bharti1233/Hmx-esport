package com.hmx.arena.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassScreenBackground
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.ui.common.GlassPasswordField
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LegalText
import com.hmx.arena.ui.common.StatusPill
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn

private const val WELCOME = "welcome"
private const val LOGIN = "login"
private const val SIGNUP = "signup"
private const val VERIFY = "verify/{email}"
private const val FORGOT = "forgot"

@Composable
fun AuthFlow() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = WELCOME) {
        composable(WELCOME) {
            WelcomeScreen(
                onSignIn = { nav.navigate(LOGIN) },
                onSignUp = { nav.navigate(SIGNUP) },
            )
        }
        composable(LOGIN) {
            LoginScreen(
                onBack = { nav.popBackStack() },
                onForgot = { nav.navigate(FORGOT) },
                onSignUp = { nav.navigate(SIGNUP) },
            )
        }
        composable(SIGNUP) {
            SignUpScreen(
                onBack = { nav.popBackStack() },
                onSignIn = { nav.popBackStack() },
                onVerify = { email -> nav.navigate("verify/${android.net.Uri.encode(email)}") },
            )
        }
        composable(VERIFY) { entry ->
            val email = android.net.Uri.decode(entry.arguments?.getString("email").orEmpty())
            VerifyEmailScreen(
                email = email,
                onBack = { nav.popBackStack() },
            )
        }
        composable(FORGOT) {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(HmxShapes.small)
                .background(HmxColor.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = HmxColor.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "HMX ARENA",
            style = MaterialTheme.typography.labelMedium,
            color = HmxColor.Ink,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun AuthFrame(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GlassScreenBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    GlassIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                } else {
                    BrandMark()
                }
                Spacer(Modifier.weight(1f))
                if (onBack != null) BrandMark()
            }
            Spacer(Modifier.height(40.dp))
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = HmxColor.Ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PolicyDialog(kind: String, onDismiss: () -> Unit) {
    val (title, body) = LegalText.forKind(kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HmxColor.White,
        shape = HmxShapes.dialog,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = HmxColor.Ink) }
        },
    )
}

@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onSignUp: () -> Unit) {
    var policy by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        GlassScreenBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            BrandMark()
            Spacer(Modifier.height(52.dp))
            Text(
                "Compete on a\ncleaner stage.",
                style = MaterialTheme.typography.displayLarge,
                color = HmxColor.Ink,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Free Fire tournaments, verified results and a leaderboard that respects your grind.",
                style = MaterialTheme.typography.bodyLarge,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(32.dp))
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    FeatureLine("Tournaments with real slots", "Atomic registration, waitlists and eligibility enforced by the server.")
                    Spacer(Modifier.height(18.dp))
                    FeatureLine("Private room credentials", "Room ID and password unlock only for registered players.")
                    Spacer(Modifier.height(18.dp))
                    FeatureLine("Results with evidence", "Screenshot proof, dispute windows and an auditable ledger of decisions.")
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("White glass UI")
                StatusPill("Free to play")
                StatusPill("Fair play first")
            }
            Spacer(Modifier.height(32.dp))
            GlassButton(
                text = "Create account",
                onClick = onSignUp,
                variant = GlassButtonVariant.Ink,
            )
            Spacer(Modifier.height(12.dp))
            GlassButton(
                text = "I already have an account",
                onClick = onSignIn,
                variant = GlassButtonVariant.Secondary,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "By continuing you agree to the Terms and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Terms",
                    style = MaterialTheme.typography.labelMedium,
                    color = HmxColor.Ink,
                    modifier = Modifier
                        .clip(HmxShapes.pill)
                        .clickable { policy = "terms" }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Text(
                    "Privacy",
                    style = MaterialTheme.typography.labelMedium,
                    color = HmxColor.Ink,
                    modifier = Modifier
                        .clip(HmxShapes.pill)
                        .clickable { policy = "privacy" }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    policy?.let { PolicyDialog(it) { policy = null } }
}

@Composable
private fun FeatureLine(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(HmxColor.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = HmxColor.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
        }
    }
}

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onForgot: () -> Unit,
    onSignUp: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    AuthFrame(
        title = "Welcome back.",
        subtitle = "Sign in to pick up your next match.",
        onBack = onBack,
    ) {
        GlassTextField(
            value = ui.email,
            onValueChange = viewModel::setEmail,
            label = "Email",
            keyboardType = KeyboardType.Email,
            error = ui.emailError,
        )
        Spacer(Modifier.height(14.dp))
        GlassPasswordField(
            value = ui.password,
            onValueChange = viewModel::setPassword,
            label = "Password",
            error = ui.passwordError,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "Forgot password?",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.InkSecondary,
                modifier = Modifier
                    .clip(HmxShapes.pill)
                    .clickable(onClick = onForgot)
                    .padding(8.dp),
            )
        }
        if (ui.error != null) {
            Spacer(Modifier.height(8.dp))
            InlineError(ui.error!!)
        }
        Spacer(Modifier.height(18.dp))
        GlassButton(
            text = "Sign in",
            onClick = viewModel::submit,
            loading = ui.loading,
            variant = GlassButtonVariant.Ink,
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("New to HMX? ", style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkSecondary)
            Text(
                "Create account",
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.Ink,
                modifier = Modifier.clickable(onClick = onSignUp),
            )
        }
    }
}

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var policy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ui.verifyEmail) {
        ui.verifyEmail?.let { email ->
            viewModel.consumeVerifyEmail()
            onVerify(email)
        }
    }

    AuthFrame(
        title = "Create your identity.",
        subtitle = "One account. Every arena. Your stats travel with you.",
        onBack = onBack,
    ) {
        GlassTextField(
            value = ui.username,
            onValueChange = viewModel::setUsername,
            label = "Username",
            placeholder = "e.g. shadowstrike",
            error = ui.usernameError,
        )
        Spacer(Modifier.height(14.dp))
        GlassTextField(
            value = ui.email,
            onValueChange = viewModel::setEmail,
            label = "Email",
            keyboardType = KeyboardType.Email,
            error = ui.emailError,
        )
        Spacer(Modifier.height(14.dp))
        GlassPasswordField(
            value = ui.password,
            onValueChange = viewModel::setPassword,
            label = "Password",
            error = ui.passwordError,
        )
        Spacer(Modifier.height(14.dp))
        GlassPasswordField(
            value = ui.confirm,
            onValueChange = viewModel::setConfirm,
            label = "Confirm password",
            error = ui.confirmError,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.accepted,
                onCheckedChange = viewModel::setAccepted,
                colors = CheckboxDefaults.colors(
                    checkedColor = HmxColor.Ink,
                    uncheckedColor = HmxColor.HairlineStrong,
                    checkmarkColor = HmxColor.White,
                ),
            )
            Column {
                Text(
                    "I agree to the Terms and Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
                Row {
                    Text(
                        "Terms",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.Ink,
                        modifier = Modifier.clickable { policy = "terms" }.padding(end = 12.dp, top = 4.dp),
                    )
                    Text(
                        "Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.Ink,
                        modifier = Modifier.clickable { policy = "privacy" }.padding(top = 4.dp),
                    )
                }
            }
        }
        if (ui.error != null) {
            Spacer(Modifier.height(8.dp))
            InlineError(ui.error!!)
        }
        Spacer(Modifier.height(18.dp))
        GlassButton(
            text = "Create account",
            onClick = viewModel::submit,
            loading = ui.loading,
            variant = GlassButtonVariant.Ink,
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Already registered? ", style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkSecondary)
            Text(
                "Sign in",
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.Ink,
                modifier = Modifier.clickable(onClick = onSignIn),
            )
        }
    }
    policy?.let { PolicyDialog(it) { policy = null } }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    onBack: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(email) { viewModel.start(email) }

    AuthFrame(
        title = "Check your inbox.",
        subtitle = "We sent a 6-digit verification code to $email.",
        onBack = onBack,
    ) {
        GlassTextField(
            value = ui.token,
            onValueChange = viewModel::setToken,
            label = "Verification code",
            placeholder = "000000",
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            error = ui.tokenError,
            onImeAction = viewModel::submit,
        )
        if (ui.error != null) {
            Spacer(Modifier.height(10.dp))
            InlineError(ui.error!!)
        }
        if (ui.message != null) {
            Spacer(Modifier.height(10.dp))
            InlineSuccess(ui.message!!)
        }
        Spacer(Modifier.height(18.dp))
        GlassButton(
            text = "Verify email",
            onClick = viewModel::submit,
            loading = ui.loading,
            variant = GlassButtonVariant.Ink,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val label = if (ui.resendSeconds > 0) {
                "Resend code in ${ui.resendSeconds}s"
            } else {
                "Resend code"
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (ui.resendSeconds > 0) HmxColor.InkTertiary else HmxColor.Ink,
                modifier = Modifier
                    .clip(HmxShapes.pill)
                    .clickable(enabled = ui.resendSeconds == 0 && !ui.loading) { viewModel.resend() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The code can take a minute to arrive. Check spam if it does not.",
            style = MaterialTheme.typography.bodySmall,
            color = HmxColor.InkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    AuthFrame(
        title = if (ui.sent) "Reset link sent." else "Reset your password.",
        subtitle = if (ui.sent) {
            "Open the link from ${ui.email} on this device. It returns you to HMX Arena to choose a new password."
        } else {
            "Enter the email on your account. We will send a secure reset link."
        },
        onBack = onBack,
    ) {
        if (!ui.sent) {
            GlassTextField(
                value = ui.email,
                onValueChange = viewModel::setEmail,
                label = "Email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                error = ui.emailError,
                onImeAction = viewModel::submit,
            )
            if (ui.error != null) {
                Spacer(Modifier.height(10.dp))
                InlineError(ui.error!!)
            }
            Spacer(Modifier.height(18.dp))
            GlassButton(
                text = "Send reset link",
                onClick = viewModel::submit,
                loading = ui.loading,
                variant = GlassButtonVariant.Ink,
            )
        } else {
            InlineSuccess("If an account exists for that email, a reset link is on its way.")
            Spacer(Modifier.height(18.dp))
            GlassButton(
                text = "Back to sign in",
                onClick = onBack,
                variant = GlassButtonVariant.Ink,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = HmxColor.InkTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Reset links expire after one hour. Password reset never asks for your game password.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkTertiary,
            )
        }
    }
}

@Composable
fun RecoveryResetScreen(
    tokenHash: String?,
    onDone: () -> Unit,
    viewModel: RecoveryResetViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) { viewModel.prepare(tokenHash) }

    if (ui.preparing) {
        Box(Modifier.fillMaxSize()) {
            GlassScreenBackground()
            Text(
                "Preparing secure reset…",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = HmxColor.InkSecondary,
            )
        }
        return
    }

    if (ui.success) {
        Box(Modifier.fillMaxSize()) {
            GlassScreenBackground()
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark()
                Spacer(Modifier.height(40.dp))
                Text("Password updated.", style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sign in with your new password to return to the arena.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HmxColor.InkSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                GlassButton("Back to sign in", onDone, variant = GlassButtonVariant.Ink)
            }
        }
        return
    }

    AuthFrame(
        title = "Choose a new password.",
        subtitle = "Your reset link is single-use and expires after one hour.",
        onBack = onDone,
    ) {
        if (!ui.ready) {
            InlineError(ui.error ?: "This reset link is invalid or expired.")
            Spacer(Modifier.height(18.dp))
            GlassButton("Back to sign in", onDone, variant = GlassButtonVariant.Ink)
        } else {
            GlassPasswordField(
                value = ui.password,
                onValueChange = viewModel::setPassword,
                label = "New password",
                error = ui.passwordError,
            )
            Spacer(Modifier.height(14.dp))
            GlassPasswordField(
                value = ui.confirm,
                onValueChange = viewModel::setConfirm,
                label = "Confirm new password",
                imeAction = ImeAction.Done,
                onImeAction = viewModel::submit,
            )
            if (ui.error != null) {
                Spacer(Modifier.height(10.dp))
                InlineError(ui.error!!)
            }
            Spacer(Modifier.height(18.dp))
            GlassButton(
                text = "Update password",
                onClick = viewModel::submit,
                loading = ui.loading,
                variant = GlassButtonVariant.Ink,
            )
        }
    }
}
