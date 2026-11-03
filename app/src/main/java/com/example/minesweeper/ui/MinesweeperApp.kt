package com.example.minesweeper.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.minesweeper.model.Difficulty
import com.example.minesweeper.model.GameStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MinesweeperApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("game/{difficulty}?resume={resume}") { backStackEntry ->
            val diffStr = backStackEntry.arguments?.getString("difficulty") ?: "NORMAL"
            val resume = backStackEntry.arguments?.getString("resume")?.toBoolean() ?: false
            val difficulty = Difficulty.valueOf(diffStr)
            GameScreen(navController, difficulty, resume)
        }
        composable("records") { RecordsScreen(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = viewModel()) {
    var showDifficultyDialog by remember { mutableStateOf(false) }
    val saveState by viewModel.saveState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkSaveState()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (saveState != null) {
                Button(
                    onClick = { navController.navigate("game/${saveState!!.difficulty}?resume=true") },
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("続きから再開")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(onClick = { showDifficultyDialog = true }, modifier = Modifier.width(200.dp)) {
                Text("新規ゲーム開始")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("records") }, modifier = Modifier.width(200.dp)) {
                Text("クリア記録を見る")
            }
        }

        if (showDifficultyDialog) {
            AlertDialog(
                onDismissRequest = { showDifficultyDialog = false },
                title = { Text("難易度を選択") },
                text = {
                    Column {
                        Difficulty.values().forEach { diff ->
                            Button(
                                onClick = {
                                    showDifficultyDialog = false
                                    navController.navigate("game/${diff.name}")
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text("${diff.name} (${(diff.mineProbability * 100).toInt()}%)")
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(navController: NavHostController, difficulty: Difficulty, resume: Boolean, viewModel: GameViewModel = viewModel()) {
    val gameState by viewModel.gameState.collectAsState()
    val gameStatus by viewModel.gameStatus.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isFlagMode by viewModel.isFlagMode.collectAsState()
    val toggleUpdate by viewModel.toggleUpdate.collectAsState()
    val restartCount by viewModel.restartCount.collectAsState()
    val isGameEnded = gameStatus == GameStatus.WON || gameStatus == GameStatus.LOST
    // 決着後に盤面（踏んだ地雷や間違った旗）を確認するため、結果ダイアログを閉じられるようにする
    var resultDismissed by remember(restartCount) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (resume) {
            val db = com.example.minesweeper.model.AppDatabase.getDatabase(navController.context)
            val saved = db.gameRecordDao().getSaveState()
            if (saved != null) {
                viewModel.resumeGame(saved)
            } else {
                viewModel.startNewGame(difficulty)
            }
        } else {
            viewModel.startNewGame(difficulty)
        }
    }

    // アプリがバックグラウンドに回る際などに保存するためのDisposableEffect
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseTimer()
                    viewModel.saveGame()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.resumeTimer()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            // Main Game View
            key(restartCount) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        MinesweeperView(context).apply {
                            setOnCellClickListener { x, y -> viewModel.onCellClicked(x, y) }
                            setOnCellLongClickListener { x, y -> viewModel.onCellLongClicked(x, y) }
                            setOnQuickOpenClickListener { x, y -> viewModel.onQuickOpenClicked(x, y) }
                        }
                    },
                    update = { view -> 
                        @Suppress("UNUSED_VARIABLE")
                        val trigger = toggleUpdate
                        view.setGameEngine(gameState)
                        view.setFlagMode(isFlagMode)
                        if (gameState.isFirstTap) {
                            view.resetView()
                        } else {
                            view.invalidate()
                        }
                    }
                )
            }

            // Floating HUD - Top
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                viewModel.saveGame()
                                navController.popBackStack() 
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                        Text("💣 ${gameState.mineCount - gameState.flagCount}", 
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("🕒 ${elapsedTime}s", 
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Floating HUD - Bottom (Toggle Button)
            if (isGameEnded && resultDismissed) {
                Button(
                    onClick = { resultDismissed = false },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                ) {
                    Text("結果を表示")
                }
            } else Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    color = if (isFlagMode) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f) 
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = CircleShape,
                    tonalElevation = 6.dp,
                    onClick = { viewModel.toggleMode() }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isFlagMode) "🚩" else "⛏️",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp)
                        )
                    }
                }
            }

            // Custom Game Over / Win Overlay
            AnimatedVisibility(
                visible = isGameEnded && !resultDismissed,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (gameStatus == GameStatus.WON) "VICTORY!" else "GAME OVER",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = if (gameStatus == GameStatus.WON) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                            
                            Text(
                                text = "Time: ${elapsedTime} seconds",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = { viewModel.startNewGame(difficulty) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("RETRY", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { resultDismissed = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("盤面を見る")
                            }
                            
                            OutlinedButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("HOME")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(navController: NavHostController, viewModel: RecordsViewModel = viewModel()) {
    val records by viewModel.allRecords.collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("クリア記録") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(records) { record ->
                ListItem(
                    headlineContent = { Text("${record.difficulty} - ${record.timeSeconds}秒") },
                    supportingContent = { Text(dateFormat.format(Date(record.dateTimestamp))) }
                )
                HorizontalDivider()
            }
        }
    }
}
