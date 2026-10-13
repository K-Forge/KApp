package co.edu.konradlorenz.kapp.ui.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.BuildConfig
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.InProgress
import co.edu.konradlorenz.kapp.ui.theme.KAppTheme
import co.edu.konradlorenz.kapp.ui.theme.OnBrandSoft
import co.edu.konradlorenz.kapp.ui.theme.Placeholder
import co.edu.konradlorenz.kapp.ui.theme.Subject

// Every measurement below is read off docs/design/mobile/LoginAndroid.dc.html, which is drawn on a
// 360x800 canvas. The pixels there are dp here.
private val BandHeight = 356.dp
private val SheetTop = 328.dp
private val ButtonHeight = 52.dp
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** The only domain KApp accounts use. Not a string resource: one starting with "@" reads as a reference. */
const val INSTITUTIONAL_DOMAIN = "@konradlorenz.edu.co"

/**
 * Sign-in: one button that opens Microsoft's sign-in (issue #46).
 *
 * The band, the crest, the stripe and the footer are LoginAndroid.dc.html's. The fields it draws -
 * address, password, "Mantener la sesion iniciada" - are gone: the address and password are typed
 * into Microsoft's page, and the session now lasts 30 days from its last use for everybody, so the
 * switch had nothing left to switch. The mockup still draws them; updating it is pending in
 * docs/design/mobile/.
 *
 * The invitation code is development-only in auth 1.0.0 ("Do not build them into the product"), so
 * its link is drawn in debug builds alone.
 */
@Composable
fun LoginScreen(
    onSignIn: () -> Unit,
    onUseInvitationCode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val activity = LocalActivity.current
    LoginContent(
        signingIn = viewModel.signingIn,
        error = viewModel.error,
        onSubmit = { activity?.let { viewModel.signIn(it, onSignIn) } },
        mockProfile = viewModel.mockProfile.takeIf { viewModel.offersMockProfiles },
        onMockProfileChange = viewModel::onMockProfileChange,
        onUseInvitationCode = onUseInvitationCode.takeIf { BuildConfig.DEBUG },
        modifier = modifier,
    )
}

/**
 * The screen without its view model, so the previews can draw every state.
 *
 * [mockProfile] is `null` unless the sign-in is the debug build's fake, and [onUseInvitationCode]
 * is `null` outside debug builds; each row is drawn only when it has something to do.
 */
@Composable
private fun LoginContent(
    signingIn: Boolean,
    error: SignInError?,
    onSubmit: () -> Unit,
    mockProfile: MockProfile?,
    onMockProfileChange: (MockProfile) -> Unit,
    onUseInvitationCode: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // The band runs to the very top of the display. Nothing is drawn for the status bar: the
        // system paints it over this, which is what canvas.json asks for.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BandHeight)
                .background(Brand),
        )

        BrandMark(modifier = Modifier.align(Alignment.TopCenter))

        // The sheet overlaps the band by 28 dp, so its rounded corners sit on purple.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = SheetTop)
                .shadow(
                    elevation = 16.dp,
                    shape = SheetShape,
                    ambientColor = Brand,
                    spotColor = Brand,
                )
                .clip(SheetShape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            TricolourStripe()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Intro()
                SignInButton(signingIn = signingIn, onClick = onSubmit)
                if (error != null) ErrorMessage(error)
                if (mockProfile != null) {
                    MockProfileRow(selected = mockProfile, onSelect = onMockProfileChange)
                }
                if (onUseInvitationCode != null) InvitationRow(onClick = onUseInvitationCode)
            }

            Footer()
        }
    }
}

/** The crest, the product name and the university, centred 80 dp down the band. */
@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.konrad_logo),
                contentDescription = stringResource(R.string.app_logo_description),
                modifier = Modifier.size(60.dp),
                // The crest keeps its own colours.
                tint = Color.Unspecified,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = OnBrandSoft,
            )
        }
    }
}

/** Pink, green and blue in equal thirds along the top edge of the sheet. */
@Composable
private fun TricolourStripe() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxSize().background(MaterialTheme.colorScheme.primary))
        Box(Modifier.weight(1f).fillMaxSize().background(InProgress))
        Box(Modifier.weight(1f).fillMaxSize().background(Subject))
    }
}

/** What the button does, and with which account, above it. */
@Composable
private fun Intro() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.login_intro_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.login_intro_body, INSTITUTIONAL_DOMAIN),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The size and colours of the mockup's "Ingresar". While a sign-in is under way it says so and
 * cannot fire twice - EstadosLogin.dc.html's "Enviando".
 */
@Composable
private fun SignInButton(signingIn: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !signingIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
    ) {
        Text(
            text = stringResource(if (signingIn) R.string.login_signing_in else R.string.login_submit),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * One line under the button, in the error colour. TalkBack reads it out as soon as it appears,
 * since focus is still on the button it answers.
 */
@Composable
private fun ErrorMessage(error: SignInError) {
    Text(
        text = stringResource(
            when (error) {
                SignInError.Rejected -> R.string.login_error_rejected
                SignInError.Deactivated -> R.string.login_error_deactivated
                SignInError.Offline -> R.string.login_error_offline
                SignInError.NotConfigured -> R.string.login_error_not_configured
                SignInError.Failed -> R.string.login_error_failed
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Debug builds against the mocks only: which named example `GET /api/users/me` answers, and with
 * it which tabs appear.
 */
@Composable
private fun MockProfileRow(selected: MockProfile, onSelect: (MockProfile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.login_mock_profile_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockProfile.entries.forEach { profile ->
                FilterChip(
                    selected = profile == selected,
                    onClick = { onSelect(profile) },
                    label = {
                        Text(
                            stringResource(
                                when (profile) {
                                    MockProfile.Student -> R.string.login_mock_student
                                    MockProfile.Professor -> R.string.login_mock_professor
                                    MockProfile.StaffAdmin -> R.string.login_mock_staff
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun InvitationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.login_first_time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.login_invitation_link),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            // The padding sits inside the clickable, so it widens the tap area rather than only
            // the text.
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ColumnScope.Footer() {
    Text(
        text = stringResource(R.string.login_copyright),
        style = MaterialTheme.typography.labelSmall,
        color = Placeholder,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 28.dp),
    )
}

@Preview(name = "Login", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginScreenPreview() {
    KAppTheme {
        LoginContent(
            signingIn = false,
            error = null,
            onSubmit = {},
            mockProfile = null,
            onMockProfileChange = {},
            onUseInvitationCode = null,
        )
    }
}

@Preview(name = "Login · debug, sin conexión", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginDebugOfflinePreview() {
    KAppTheme {
        LoginContent(
            signingIn = false,
            error = SignInError.Offline,
            onSubmit = {},
            mockProfile = MockProfile.Professor,
            onMockProfileChange = {},
            onUseInvitationCode = {},
        )
    }
}
