package ir.pargartech.paymentrecorder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.pargartech.paymentrecorder.App
import ir.pargartech.paymentrecorder.core.parser.TextNormalizer
import ir.pargartech.paymentrecorder.notify.Notifier
import ir.pargartech.paymentrecorder.security.AppLock

private enum class Tab { HOME, ACCOUNTS, REVIEW, SETTINGS }

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    private val app get() = application as App
    private lateinit var vm: MainViewModel

    private var locked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var smsGranted by mutableStateOf(false)
    private var openReviewRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(
            this,
            viewModelFactory { initializer { MainViewModel(app.repo, app.prefs) } },
        )[MainViewModel::class.java]

        lockEnabled = app.prefs.lockEnabled
        // Not tied to savedInstanceState: a restore after process death must still ask to unlock.
        locked = AppLock.session.isLocked(lockEnabled)
        applySecureFlag()
        handleIntent(intent)

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked) LockScreen(onUnlock = ::unlock) else Root()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        smsGranted = hasPermission(Manifest.permission.READ_SMS) && hasPermission(Manifest.permission.RECEIVE_SMS)
        AppLock.session.onForeground(SystemClock.elapsedRealtime())
        locked = AppLock.session.isLocked(lockEnabled)
    }

    override fun onStop() {
        super.onStop()
        // Rotation stops and recreates the activity; that isn't going to the background.
        if (!isChangingConfigurations) AppLock.session.onBackground(SystemClock.elapsedRealtime())
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifier.EXTRA_OPEN_REVIEW, false) == true) openReviewRequest = true
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun unlock() = AppLock.authenticate(this) {
        AppLock.session.onUnlocked()
        locked = false
    }

    private fun setLock(enabled: Boolean) {
        if (enabled) {
            // Confirm the user can actually unlock before turning the lock on.
            AppLock.authenticate(this) {
                AppLock.session.onUnlocked()
                app.prefs.lockEnabled = true
                lockEnabled = true
                applySecureFlag()
            }
        } else {
            app.prefs.lockEnabled = false
            lockEnabled = false
            applySecureFlag()
        }
    }

    /** With the lock on, also hide content from the recent-apps preview and screenshots. */
    private fun applySecureFlag() {
        if (lockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Composable
    private fun Root() {
        var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
        var openTx by rememberSaveable { mutableStateOf<String?>(null) }
        val reviewCount by vm.reviewCount.collectAsState()

        LaunchedEffect(openReviewRequest) {
            if (openReviewRequest) {
                tab = Tab.REVIEW
                openTx = null
                openReviewRequest = false
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            smsGranted = hasPermission(Manifest.permission.READ_SMS) && hasPermission(Manifest.permission.RECEIVE_SMS)
        }
        val requestPermissions = {
            val perms = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(perms.toTypedArray())
        }

        BackHandler(enabled = openTx != null) { openTx = null }
        BackHandler(enabled = openTx == null && tab != Tab.HOME) { tab = Tab.HOME }

        Scaffold(
            // Each screen's TopAppBar handles the status bar; NavigationBar handles the nav bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (openTx == null) {
                    NavigationBar {
                        NavItem(tab, Tab.HOME, "خانه", { Icon(Icons.Filled.Home, null) }) { tab = it }
                        NavItem(tab, Tab.ACCOUNTS, "حساب‌ها", { Icon(Icons.Filled.Person, null) }) { tab = it }
                        NavItem(tab, Tab.REVIEW, "بررسی", {
                            BadgedBox(badge = {
                                if (reviewCount > 0) Badge { Text(TextNormalizer.toPersianDigits(reviewCount.toString())) }
                            }) { Icon(Icons.Filled.Warning, null) }
                        }) { tab = it }
                        NavItem(tab, Tab.SETTINGS, "تنظیمات", { Icon(Icons.Filled.Settings, null) }) { tab = it }
                    }
                }
            },
        ) { padding ->
            val insets = if (openTx != null) Modifier.navigationBarsPadding() else Modifier
            Box(Modifier.padding(padding).then(insets).imePadding()) {
                val txId = openTx
                when {
                    txId != null -> TransactionScreen(vm, txId, onBack = { openTx = null })
                    tab == Tab.HOME -> HomeScreen(vm, smsGranted, requestPermissions, onOpenTransaction = { openTx = it })
                    tab == Tab.ACCOUNTS -> AccountsScreen(vm)
                    tab == Tab.REVIEW -> ReviewScreen(vm)
                    tab == Tab.SETTINGS -> SettingsScreen(vm, lockEnabled, AppLock.isAvailable(this@MainActivity), ::setLock)
                }
            }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.NavItem(
        current: Tab,
        tab: Tab,
        label: String,
        icon: @Composable () -> Unit,
        onSelect: (Tab) -> Unit,
    ) {
        NavigationBarItem(selected = current == tab, onClick = { onSelect(tab) }, icon = icon, label = { Text(label) })
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Text("برنامه قفل است")
        Button(onClick = onUnlock) { Text("باز کردن") }
    }
}
