package net.eggc.ryoikumemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.ui.feature.settings.PrivacyPolicyScreen
import net.eggc.ryoikumemo.ui.feature.settings.TermsScreen
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme
import java.util.UUID

class AuthActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LINK_GOOGLE_ACCOUNT = "extra_link_google_account"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var isLinkGoogleAccountMode: Boolean = false
    private var blockingErrorMessage by mutableStateOf<String?>(null)
    private var onBlockingErrorOk: (() -> Unit)? = null

    private enum class AuthDestinations {
        AUTH,
        TERMS,
        PRIVACY_POLICY,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        isLinkGoogleAccountMode = intent.getBooleanExtra(EXTRA_LINK_GOOGLE_ACCOUNT, false)
        credentialManager = CredentialManager.create(this)

        setContent {
            RyoikumemoTheme {
                var currentDestination by remember { mutableStateOf(AuthDestinations.AUTH) }

                val errorMessage = blockingErrorMessage
                if (errorMessage != null) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("連携エラー") },
                        text = { Text(errorMessage) },
                        confirmButton = {
                            Button(onClick = {
                                val action = onBlockingErrorOk
                                blockingErrorMessage = null
                                onBlockingErrorOk = null
                                action?.invoke()
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }

                when (currentDestination) {
                    AuthDestinations.AUTH -> {
                        AuthScreen(
                            onGoogleLoginClick = {
                                lifecycleScope.launch {
                                    signIn()
                                }
                            },
                            onAnonymousLoginClick = {
                                signInAnonymouslyAndStartMain()
                            },
                            onTermsClick = { currentDestination = AuthDestinations.TERMS },
                            onPrivacyPolicyClick = { currentDestination = AuthDestinations.PRIVACY_POLICY },
                            isLinkGoogleAccountMode = isLinkGoogleAccountMode,
                            onBackClick = {
                                finish()
                            },
                        )
                    }

                    AuthDestinations.TERMS -> {
                        TermsScreen(onNavigateUp = { currentDestination = AuthDestinations.AUTH })
                    }

                    AuthDestinations.PRIVACY_POLICY -> {
                        PrivacyPolicyScreen(onNavigateUp = { currentDestination = AuthDestinations.AUTH })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        val shouldStayOnAuthForLink = isLinkGoogleAccountMode && currentUser?.isAnonymous == true
        if (currentUser != null && !shouldStayOnAuthForLink) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun signInAnonymouslyAndStartMain() {
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.w("AuthActivity", "signInAnonymously:failure", task.exception)
                    Toast.makeText(this, "匿名ログインに失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private suspend fun signIn() {
        val nonce = UUID.randomUUID().toString()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(this, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.data)
                    authenticateWithGoogle(googleIdTokenCredential.idToken)
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("AuthActivity", "Google ID token parsing failed", e)
                    Toast.makeText(this, "Googleサインインに失敗しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("AuthActivity", "Google ID token parsing failed", e)
                    Toast.makeText(this, "Googleサインインに失敗しました", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("AuthActivity", "Unexpected credential type: ${credential.type}")
                Toast.makeText(this, "Googleサインインに失敗しました", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("AuthActivity", "Sign-in failed", e)
            Toast.makeText(this, "Googleサインインに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun authenticateWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        val authTask = if (isLinkGoogleAccountMode && currentUser?.isAnonymous == true) {
            currentUser.linkWithCredential(credential)
        } else {
            auth.signInWithCredential(credential)
        }

        authTask.addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Log.w("AuthActivity", "authenticateWithGoogle:failure", task.exception)
                if (isLinkGoogleAccountMode && task.exception is FirebaseAuthUserCollisionException) {
                    blockingErrorMessage = "すでにアカウントがあるため連携できませんでした。匿名アカウントからログアウトして再度ログインしてください。"
                    onBlockingErrorOk = { finish() }
                    return@addOnCompleteListener
                }

                val message = if (isLinkGoogleAccountMode) {
                    "Googleアカウント連携に失敗しました"
                } else {
                    "認証に失敗しました"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onGoogleLoginClick: () -> Unit,
    onAnonymousLoginClick: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    isLinkGoogleAccountMode: Boolean,
    onBackClick: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(false) }

    LaunchedEffect(isLinkGoogleAccountMode) {
        if (isLinkGoogleAccountMode) {
            agreed = true
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("ご利用にあたっての注意事項") },
            text = {
                Column {
                    Text("ログインせずに利用した場合、匿名アカウントでクラウドにデータが保存されます。そのため、以下の点にご注意ください。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("・端末変更時も匿名アカウントのままでは復元できないため、設定からGoogleアカウント連携をおすすめします。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("・ログアウトすると匿名アカウントに再ログインできず、データへアクセスできなくなる場合があります。")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    onAnonymousLoginClick()
                }) {
                    Text("利用を続ける")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLinkGoogleAccountMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBackClick) {
                    Text("戻る")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val linkStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
            val annotatedString = buildAnnotatedString {
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = "TERMS",
                        styles = TextLinkStyles(style = linkStyle),
                        linkInteractionListener = { onTermsClick() }
                    )
                )
                append("利用規約")
                pop()
                append("と")
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = "POLICY",
                        styles = TextLinkStyles(style = linkStyle),
                        linkInteractionListener = { onPrivacyPolicyClick() }
                    )
                )
                append("プライバシーポリシー")
                pop()
                append("に同意の上、ご利用ください。")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it }, enabled = !isLinkGoogleAccountMode)
                Text(
                    text = annotatedString,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Button(onClick = onGoogleLoginClick, enabled = agreed) {
                Text(if (isLinkGoogleAccountMode) "Googleアカウントと連携" else "Googleでログイン")
            }

            if (!isLinkGoogleAccountMode) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { showDialog = true }, enabled = agreed) {
                    Text("ログインせずに利用する")
                }
            }
        }
    }
}
