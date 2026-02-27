package top.wsdx233.r2droid.feature.about

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.wsdx233.r2droid.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    TypingText(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.about_back_desc)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Geeky Matrix/Grid Background
            GeekyBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.1f),
                color = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                // Header Animation
                var showHeader by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    showHeader = true
                }

                AnimatedVisibility(
                    visible = showHeader,
                    enter = fadeIn(animationSpec = tween(500)) + 
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                ) {
                    AppHeader()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Staggered Items as Code Blocks
                StaggeredCodeCard(
                    index = 0,
                    variableName = "author",
                    value = "\"${stringResource(R.string.about_author_name)}\"",
                    comment = stringResource(R.string.about_author_comment),
                    icon = Icons.Rounded.Person,
                    onClick = {
                        uriHandler.openUri("https://github.com/wsdx233")
                    }
                )

                StaggeredCodeCard(
                    index = 1,
                    variableName = "contact",
                    value = stringResource(R.string.about_contact_value),
                    comment = stringResource(R.string.about_contact_comment),
                    icon = Icons.Rounded.Commit, // Use distinct icon
                    onClick = {
                        uriHandler.openUri("https://qm.qq.com/q/WUGtRZbDiO")
                    }
                )

                StaggeredCodeCard(
                    index = 2,
                    variableName = "repo",
                    value = stringResource(R.string.about_repo_value),
                    comment = stringResource(R.string.about_repo_comment),
                    icon = Icons.Rounded.Code,
                    onClick = {
                        uriHandler.openUri("https://github.com/wsdx233/r2droid")
                    }
                )

                StaggeredCodeCard(
                    index = 3,
                    variableName = "core",
                    value = stringResource(R.string.about_core_value),
                    comment = stringResource(R.string.about_core_comment),
                    icon = Icons.Rounded.DataObject,
                    onClick = {}
                )
                
                Spacer(modifier = Modifier.height(50.dp))
                
                // Footer
                Text(
                    text = stringResource(R.string.about_footer),
                    modifier = Modifier.align(Alignment.CenterHorizontally).alpha(0.5f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                )
                 Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun getAppVersion(): String {
    val context = LocalContext.current
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

@Preview
@Composable
fun AppHeader() {
    val r2DroidText = stringResource(R.string.about_r2droid)

    // Rotating gradient animation
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_rotation"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary
                        )
                    )
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                 contentAlignment = Alignment.Center
            ) {
                 Image(
                     painter = painterResource(id = R.drawable.icon),
                    contentDescription = stringResource(R.string.about_logo_desc),
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TypingText(
            text = r2DroidText,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            ),
            cursor = "_"
        )

        Spacer(modifier = Modifier.height(8.dp))

        val context = LocalContext.current
        // version
        Text(text = "v${getAppVersion()}", style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace).copy(Color.Gray))

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.about_motto),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun StaggeredCodeCard(
    index: Int,
    variableName: String,
    value: String,
    comment: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(index * 150L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + 
                slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
    ) {
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "scale"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .padding(vertical = 6.dp)
                .clickable { 
                    isPressed = true
                    onClick()
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat look
        ) {
            LaunchedEffect(isPressed) {
                if (isPressed) {
                    delay(100)
                    isPressed = false
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header of the "window" or code block
                Row(
                   verticalAlignment = Alignment.CenterVertically,
                   horizontalArrangement = Arrangement.SpaceBetween,
                   modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodySmall.copy(
                             fontFamily = FontFamily.Monospace,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Code content
                val codeText = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)) {
                        append("val ")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(variableName)
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(" = ")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                        append(value)
                    }
                }

                Text(
                    text = codeText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

@Composable
fun TypingText(
    text: String,
    style: TextStyle,
    cursor: String = "_",
    typingSpeed: Long = 100
) {
    var displayedText by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(typingSpeed)
        }
    }

    // Blinking cursor logic
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = displayedText,
            style = style,
            color = MaterialTheme.colorScheme.onSurface
        )
        // Show cursor if text is fully typed (or during typing if preferred, here only at end)
        // Actually best to show cursor always?
        // Let's fade in/out only the cursor.
        if (displayedText.isNotEmpty()) {
             Text(
                text = cursor,
                style = style,
                color = MaterialTheme.colorScheme.primary, // Cursor color
                modifier = Modifier.alpha(cursorAlpha)
            )
        }
    }
}

@Composable
fun GeekyBackground(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition()
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "background_offset"
    )

    Canvas(modifier = modifier) {
        val step = 40.dp.toPx()
        val width = size.width
        val height = size.height
        
        // Vertical scanning lines
        for (x in 0..((width / step).toInt() + 1)) {
            val xPos = x * step
            drawLine(
                color = color.copy(alpha = 0.1f),
                start = Offset(xPos, 0f),
                end = Offset(xPos, height),
                strokeWidth = 1f
            )
        }
        
        // Horizontal scanning lines with movement
        val currentOffset = offset * step
        for (y in 0..((height / step).toInt() + 1)) {
            val yPos = (y * step + currentOffset) % height
            drawLine(
                color = color.copy(alpha = 0.1f),
                start = Offset(0f, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1f
            )
        }
    }
}
