package net.eggc.ryoikumemo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    var privacyPolicyText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val inputStream = context.assets.open("privacy_policy.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            privacyPolicyText = reader.readText()
        } catch (e: Exception) {
            // Handle exception
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("プライバシーポリシー") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "プライバシーポリシー",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            item {
                val annotatedString = buildAnnotatedString {
                    append(privacyPolicyText)
                    val urlPattern = "https://[^\\s]+".toRegex()
                    urlPattern.findAll(privacyPolicyText).forEach { matchResult ->
                        addStringAnnotation(
                            tag = "URL",
                            annotation = matchResult.value,
                            start = matchResult.range.first,
                            end = matchResult.range.last + 1
                        )
                        addStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = matchResult.range.first,
                            end = matchResult.range.last + 1
                        )
                    }
                }

                ClickableText(
                    text = annotatedString,
                    onClick = {
                        annotatedString.getStringAnnotations("URL", it, it)
                            .firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    }
}
