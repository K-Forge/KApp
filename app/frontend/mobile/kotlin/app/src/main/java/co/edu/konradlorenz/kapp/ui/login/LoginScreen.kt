package co.edu.konradlorenz.kapp.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.DomainSuffix
import co.edu.konradlorenz.kapp.ui.theme.InProgress
import co.edu.konradlorenz.kapp.ui.theme.KAppTheme
import co.edu.konradlorenz.kapp.ui.theme.OnBrandSoft
import co.edu.konradlorenz.kapp.ui.theme.Placeholder
import co.edu.konradlorenz.kapp.ui.theme.Subject

// Every measurement below is read off docs/design/mobile/LoginAndroid.dc.html, which is drawn on a
// 360x800 canvas. The pixels there are dp here.
private val BandHeight = 356.dp
private val SheetTop = 328.dp
private val FieldHeight = 52.dp
private val FieldShape = RoundedCornerShape(12.dp)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Sign-in.
 *
 * The screen does not talk to anything yet: [onSignIn] fires as soon as both fields have content.
 * The credential check, and with it the four cases drawn in
 * docs/design/mobile/EstadosLogin.dc.html, arrive with POST /auth/login.
 */
@Composable
fun LoginScreen(
    onSignIn: () -> Unit,
    onUseInvitationCode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(),
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
                .background(MaterialTheme.colorScheme.surface)
                // With the keyboard open the sheet lifts and its contents scroll, so the Ingresar
                // button is never covered. The mockup cannot show this; the design note says in so
                // many words that it is solved in code.
                .imePadding(),
        ) {
            TricolourStripe()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                EmailField(
                    value = viewModel.emailLocalPart,
                    onValueChange = viewModel::onEmailChange,
                )
                PasswordField(
                    value = viewModel.password,
                    onValueChange = viewModel::onPasswordChange,
                    visible = viewModel.passwordVisible,
                    onVisibilityToggle = viewModel::onPasswordVisibilityToggle,
                )
                KeepSignedInRow(
                    checked = viewModel.keepSignedIn,
                    onCheckedChange = viewModel::onKeepSignedInChange,
                )
                SignInButton(enabled = viewModel.canSubmit, onClick = onSignIn)
                InvitationRow(onClick = onUseInvitationCode)
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

/**
 * Caption, then the filled box that every field shares on both platforms.
 *
 * [endPadding] exists because the password field puts a 48 dp button against that edge and needs
 * less padding of its own to keep the glyph level with everything else.
 */
@Composable
private fun Field(
    label: String,
    endPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .clip(FieldShape)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FieldShape)
                .padding(start = 16.dp, end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * The student types "pepito.perez" and the domain is printed after it in grey, unedited and
 * unerasable. The student code that used to sit beside this field is gone: institutional e-mail
 * only.
 */
@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit) {
    Field(label = stringResource(R.string.login_email_label)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // fill = false lets the field shrink to what was typed, so the domain sits right
            // against it instead of being pushed to the far edge.
            modifier = Modifier.weight(1f, fill = false),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.login_email_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Placeholder,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = INSTITUTIONAL_DOMAIN,
            style = MaterialTheme.typography.bodyLarge,
            color = DomainSuffix,
            maxLines = 1,
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
) {
    // 3 dp of field padding plus half the slack inside the 48 dp button puts the glyph 16 dp from
    // the edge, level with every other field.
    Field(label = stringResource(R.string.login_password_label), endPadding = 3.dp) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = if (visible) {
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                // The dots are set larger and spread apart, as the mockup draws them.
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                )
            },
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )
        // 48 dp is the Android touch target from Tokens.dc.html; the glyph itself stays at 22 dp.
        IconButton(onClick = onVisibilityToggle, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(
                    if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye,
                ),
                contentDescription = stringResource(
                    if (visible) R.string.login_password_hide else R.string.login_password_show,
                ),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeepSignedInRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.login_keep_signed_in),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun SignInButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(FieldHeight),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.login_submit),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
        LoginScreen(onSignIn = {}, onUseInvitationCode = {})
    }
}
