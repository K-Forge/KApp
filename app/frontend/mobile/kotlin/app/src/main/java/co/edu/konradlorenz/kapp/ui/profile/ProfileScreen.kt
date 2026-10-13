package co.edu.konradlorenz.kapp.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.data.network.AcademicInfo
import co.edu.konradlorenz.kapp.data.network.UserProfile
import co.edu.konradlorenz.kapp.data.profile.ProfileState
import co.edu.konradlorenz.kapp.data.session.ProfileRole
import co.edu.konradlorenz.kapp.ui.common.BandContentHeight
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.home.toStudent
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination
import co.edu.konradlorenz.kapp.ui.navigation.MainShell
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.KAppTheme
import co.edu.konradlorenz.kapp.ui.theme.Person
import coil3.compose.SubcomposeAsyncImage

private val CardShape = RoundedCornerShape(18.dp)
private val AvatarSize = 88.dp

/**
 * Perfil, from `GET /api/users/me` (issue #47).
 *
 * No mockup covers it, so it is built from Inicio's parts: the band, a card climbing 24 dp into it,
 * the K's colour for people. What is on it is what user 1.0.0 has - name, address, profile role
 * and, for a student, the academic block - and nothing it removed: no document, no phone, no
 * student code.
 *
 * Nothing on the card is editable but the picture, which is what the contract allows: names come
 * from Microsoft, the address from the Auth API and the academic block from SINU. The note under
 * the card says so, so the person knows where to change them.
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.profile.collectAsState()
    ProfileContent(
        state = state,
        removingAvatar = viewModel.removingAvatar,
        avatarError = viewModel.avatarError,
        signingOut = viewModel.signingOut,
        onRetry = viewModel::retry,
        onRemoveAvatar = viewModel::removeAvatar,
        onSignOut = viewModel::signOut,
    )
}

@Composable
private fun ProfileContent(
    state: ProfileState,
    removingAvatar: Boolean,
    avatarError: Boolean,
    signingOut: Boolean,
    onRetry: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSignOut: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrandBand(title = stringResource(R.string.home_shortcut_profile))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = BandContentHeight - 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = BarContentPadding)
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, CardShape, ambientColor = Brand, spotColor = Brand)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    ProfileState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 40.dp),
                        color = Brand,
                    )
                    ProfileState.Failed -> FailedCard(onRetry)
                    is ProfileState.Ready -> ProfileCard(
                        profile = state.profile,
                        removingAvatar = removingAvatar,
                        avatarError = avatarError,
                        onRemoveAvatar = onRemoveAvatar,
                    )
                }
            }

            if (state is ProfileState.Ready) {
                Text(
                    text = stringResource(
                        if (state.profile.academic != null) {
                            R.string.profile_where_from_student
                        } else {
                            R.string.profile_where_from
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }

            // Always there, even when the profile could not be read: signing out must never depend
            // on the server answering.
            OutlinedButton(
                onClick = onSignOut,
                enabled = !signingOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(text = stringResource(R.string.sign_out))
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    removingAvatar: Boolean,
    avatarError: Boolean,
    onRemoveAvatar: () -> Unit,
) {
    Avatar(profile)
    Spacer(Modifier.height(12.dp))
    Text(
        text = "${profile.firstName} ${profile.lastName}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = profile.email,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    roleLabel(profile.roles)?.let { label ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(label),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Person.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Person,
        )
    }

    profile.academic?.let { academic ->
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AcademicRows(academic)
    }

    // Only removing: user 1.0.0 takes a picture as a URL, and there is no endpoint yet to upload
    // one to. Picking a photo on the phone waits for that endpoint.
    if (profile.avatarUrl != null) {
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRemoveAvatar, enabled = !removingAvatar) {
            Text(stringResource(R.string.profile_remove_avatar))
        }
    }
    if (avatarError) {
        Text(
            text = stringResource(R.string.profile_remove_avatar_failed),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The picture when there is one and it loads; the initials, as on Inicio, when there is none or
 * it does not. The mocks' picture lives on a host that does not exist, so a debug build always
 * shows the initials.
 */
@Composable
private fun Avatar(profile: UserProfile) {
    val initials = @Composable {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(Person.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profile.toStudent().initials,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Person,
            )
        }
    }
    val url = profile.avatarUrl
    if (url == null) {
        initials()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = stringResource(R.string.profile_avatar_description),
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            loading = { initials() },
            error = { initials() },
        )
    }
}

/** The academic block, read from SINU: a student's only. */
@Composable
private fun AcademicRows(academic: AcademicInfo) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        InfoRow(R.string.profile_programme, academic.programName)
        InfoRow(R.string.profile_level, academic.currentLevel.toString())
        InfoRow(R.string.profile_pensum, academic.pensumCode)
    }
}

@Composable
private fun InfoRow(@StringRes label: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FailedCard(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.profile_failed),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onRetry) {
        Text(stringResource(R.string.profile_retry))
    }
}

/** The profile role in words. Permissions open the admin portal and are not shown here. */
@StringRes
private fun roleLabel(roles: List<String>): Int? = when (ProfileRole.of(roles)) {
    ProfileRole.Student -> R.string.profile_role_student
    ProfileRole.Professor -> R.string.profile_role_professor
    ProfileRole.Staff -> R.string.profile_role_staff
    null -> null
}

private val PreviewStudent = UserProfile(
    id = "3f8a1c2e-7b4d-4e5a-9c6f-2d1b8e0a4c73",
    email = "pepito.perez@konradlorenz.edu.co",
    firstName = "Pepito",
    lastName = "Perez Gomez",
    avatarUrl = null,
    roles = listOf("ROLE_STUDENT"),
    active = true,
    academic = AcademicInfo(
        programCode = "506",
        programName = "Ingeniería de Sistemas",
        pensumCode = "1015",
        currentLevel = 8,
    ),
)

@Composable
private fun ProfilePreview(state: ProfileState) {
    KAppTheme {
        MainShell(current = KAppDestination.Profile, onSelect = {}) {
            ProfileContent(
                state = state,
                removingAvatar = false,
                avatarError = false,
                signingOut = false,
                onRetry = {},
                onRemoveAvatar = {},
                onSignOut = {},
            )
        }
    }
}

@Preview(name = "Perfil · estudiante", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileStudentPreview() {
    ProfilePreview(ProfileState.Ready(PreviewStudent))
}

@Preview(name = "Perfil · sin conexión", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileFailedPreview() {
    ProfilePreview(ProfileState.Failed)
}
