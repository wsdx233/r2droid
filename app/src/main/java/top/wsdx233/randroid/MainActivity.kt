package top.wsdx233.randroid
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.wsdx233.randroid.screen.debug.DebugScreen
import top.wsdx233.randroid.screen.debug.DebugViewModel
import top.wsdx233.randroid.screen.install.InstallScreen
import top.wsdx233.randroid.ui.theme.RandroidTheme
import top.wsdx233.randroid.util.R2Installer
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动应用时检查并安装
        // 注意：这里只是触发安装，状态变化会通过 StateFlow 通知 UI
        lifecycleScope.launch {
            R2Installer.checkAndInstall(applicationContext)
        }
        enableEdgeToEdge()
        setContent {
            RandroidTheme {
                // 监听全局安装状态
                val installState by R2Installer.installState.collectAsState()
                if (installState.isInstalling) {
                    // 如果正在安装，显示安装界面
                    InstallScreen(installState = installState)
                } else {
                    // 如果安装完成或不需要安装，显示主界面
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("Randroid") },
                                actions = {
                                    IconButton(onClick = {
                                        val intent = Intent(this@MainActivity, TerminalActivity::class.java)
                                        startActivity(intent)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Terminal,
                                            contentDescription = "Terminal"
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        val debugViewModel: DebugViewModel = viewModel()
                        DebugScreen(modifier = Modifier.padding(innerPadding), viewModel = debugViewModel)
                    }
                }
            }
        }
    }
}
