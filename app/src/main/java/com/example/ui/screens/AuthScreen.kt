package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.GoogleAuthHelper
import com.example.ui.theme.GothamHairline
import com.example.ui.theme.GothamHairlineStrong
import com.example.ui.theme.GothamObsidian
import com.example.ui.theme.GothamPearl
import com.example.ui.theme.GothamPureWhite
import com.example.ui.theme.GothamSilver
import com.example.ui.theme.GothamSlate
import com.example.ui.theme.GothamSurface
import com.example.ui.theme.GothamVoid

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GothamObsidian, GothamVoid, GothamVoid)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, GothamHairlineStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_alfred_emblem_1794796240073),
                        contentDescription = "Emblema do Alfred",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = GothamSurface,
                    border = BorderStroke(1.dp, GothamHairline),
                ) {
                    Text(
                        text = "WAYNE PERSONAL LEDGER",
                        style = MaterialTheme.typography.labelSmall,
                        color = GothamSilver,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Alfred",
                    style = MaterialTheme.typography.displayLarge,
                    color = GothamPureWhite,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Suas finanças pessoais e o caixa da sua empresa organizados em silêncio e precisão.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GothamSilver,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuthFeatureRow(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "Finanças & Empresa separados",
                    subtitle = "Saldos, cartões e metas isolados por contexto.",
                )
                AuthFeatureRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Assistente por voz, texto e foto",
                    subtitle = "Fale como no Tony — o Alfred registra e analisa.",
                )
                AuthFeatureRow(
                    icon = Icons.Rounded.Lock,
                    title = "Cofre privado na nuvem Google",
                    subtitle = "Backup automático exclusivo para a sua conta.",
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = GothamSurface,
                        border = BorderStroke(1.dp, GothamHairlineStrong),
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GothamPearl,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        GoogleAuthHelper.signInWithGoogleInteractive(
                            context = context,
                            scope = scope,
                            onSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onCancelled = {
                                isLoading = false
                            },
                            onError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GothamPureWhite,
                        contentColor = GothamVoid,
                        disabledContainerColor = GothamSilver,
                        disabledContentColor = GothamVoid,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = GothamVoid,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Entrar com Google",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Uso estritamente pessoal • Seus dados ficam vinculados apenas a você.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GothamSlate,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GothamSurface,
        border = BorderStroke(1.dp, GothamHairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = GothamPureWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GothamPureWhite,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GothamSilver,
                )
            }
        }
    }
}
