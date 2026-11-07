package io.legado.app.feature.lanshare

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.utils.QRCodeUtils

/**
 * 局域网分享二维码（阅读Max）：手机/平板扫码直接打开本机的网页服务，省去手打 IP。
 * 二维码只是 http://<本机局域网 IP>:<端口>，对方必须和本机连在同一个 WiFi 下才能打开。
 */
@Composable
fun LanShareQrDialog(
    address: String,
    onDismiss: () -> Unit,
) {
    val base = address.trim().trimEnd('/')
    val valid = base.startsWith("http://") || base.startsWith("https://")
    var shareListMode by remember { mutableStateOf(true) }
    val url = if (shareListMode) "$base/share" else base
    val qr = remember(url) {
        if (valid) QRCodeUtils.createQRCode(url, 720)?.asImageBitmap() else null
    }
    Dialog(onDismissRequest = onDismiss) {
        NormalCard(
            cornerRadius = 24.dp,
            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "扫码打开",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LegadoTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shareListMode,
                        onClick = { shareListMode = true },
                        label = { Text("书籍分享页") },
                    )
                    FilterChip(
                        selected = !shareListMode,
                        onClick = { shareListMode = false },
                        label = { Text("网页服务首页") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "二维码：$url",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "当前没有可用的局域网地址，请先连接 WiFi",
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    url,
                    fontSize = 14.sp,
                    color = LegadoTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "需要和本机连同一个 WiFi",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LegadoTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "用另一台设备的相机或浏览器扫一扫即可打开，无需手动输入 IP。",
                    fontSize = 12.sp,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        }
    }
}
