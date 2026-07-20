package com.rphub.server

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private var fileServer: FileServer? = null
    private var selectedFolderUri: Uri? = null

    // 选择文件夹的请求码
    companion object {
        private const val PICK_FOLDER_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF1976D2),
                    secondary = androidx.compose.ui.graphics.Color(0xFF42A5F5)
                )
            ) {
                MainScreen(
                    onPickFolder = { pickFolder() },
                    onStartServer = { startServer() },
                    onStopServer = { stopServer() }
                )
            }
        }
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_FOLDER_REQUEST)
    }

    @Deprecated("Use registerForActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FOLDER_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 持久化权限
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedFolderUri = uri
                Toast.makeText(this, "已选择文件夹", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startServer() {
        val uri = selectedFolderUri ?: run {
            Toast.makeText(this, "请先选择文件夹", Toast.LENGTH_SHORT).show()
            return
        }

        if (fileServer?.isRunning == true) {
            Toast.makeText(this, "服务器已在运行", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                fileServer = FileServer(uri, contentResolver)
                fileServer?.start()
                Toast.makeText(this@MainActivity, "服务器已启动: http://localhost:8080", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopServer() {
        lifecycleScope.launch {
            fileServer?.stop()
            fileServer = null
            Toast.makeText(this@MainActivity, "服务器已停止", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileServer?.stop()
        fileServer = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onPickFolder: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var folderPath by remember { mutableStateOf("未选择") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RP-Hub 本地服务器") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态图标
            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (isRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isRunning) "▶" else "⏹",
                        fontSize = 36.sp
                    )
                }
            }

            // 状态文字
            Text(
                text = if (isRunning) "服务器运行中" else "服务器已停止",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // 地址信息
            if (isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "本机地址",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "http://localhost:8080",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "局域网地址",
                            style = MaterialTheme.typography.labelMedium
                        )
                        val ip = getLocalIpAddress()
                        if (ip != null) {
                            Text(
                                text = "http://$ip:8080",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 文件夹选择
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "服务文件夹",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = folderPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onPickFolder()
                            folderPath = "已选择"
                        },
                        enabled = !isRunning
                    ) {
                        Text("选择文件夹")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 启动/停止按钮
            Button(
                onClick = {
                    if (isRunning) {
                        onStopServer()
                        isRunning = false
                    } else {
                        onStartServer()
                        isRunning = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isRunning) "停止服务器" else "启动服务器",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 使用说明
            Text(
                text = "在 Edge 浏览器中打开 http://localhost:8080 即可访问 RP-Hub",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 获取本机局域网 IP 地址
 */
private fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val ip = address.hostAddress ?: continue
                    // 过滤掉 0.0.0.0
                    if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                        return ip
                    }
                }
            }
        }
    } catch (e: Exception) {
        // 忽略
    }
    return null
}