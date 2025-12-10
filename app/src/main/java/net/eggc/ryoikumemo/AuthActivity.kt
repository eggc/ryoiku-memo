package net.eggc.ryoikumemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme
import java.util.UUID

class AuthActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        credentialManager = CredentialManager.create(this)

        setContent {
            RyoikumemoTheme {
                AuthScreen(
                    onLoginClick = {
                        lifecycleScope.launch {
                            signIn()
                        }
                    },
                    onSkipLoginClick = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                } catch (e: GoogleIdTokenParsingException) {
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

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.w("AuthActivity", "firebaseAuthWithGoogle:failure", task.exception)
                    Toast.makeText(this, "認証に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

@Composable
fun AuthScreen(onLoginClick: () -> Unit, onSkipLoginClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("ご利用にあたっての注意事項") },
            text = {
                Column {
                    Text("ログインせずに利用した場合、データはお使いのスマートフォン内に保存されます。そのため、以下の点にご注意ください。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("・機種変更など、お使いのスマートフォンを変更する際にデータを引き継ぐことはできません。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("・後からログインした場合でも、ログインせずに作成したデータを引き継ぐことはできません。")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    onSkipLoginClick()
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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onLoginClick) {
            Text("Googleでログイン")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { showDialog = true }) {
            Text("ログインせずに利用する")
        }
    }
}
