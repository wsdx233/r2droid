package top.wsdx233.r2droid.feature.types.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.data.model.FunctionVariable
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.ui.dialogs.FunctionInfoDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionInfoDialogCard
import top.wsdx233.r2droid.core.ui.dialogs.FunctionVariablesDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionVariablesDialogCard
import top.wsdx233.r2droid.feature.types.data.CallingConventionInfo
import top.wsdx233.r2droid.feature.types.data.EnumMember
import top.wsdx233.r2droid.feature.types.data.EnumTypeInfo
import top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot
import top.wsdx233.r2droid.feature.types.data.StructField
import top.wsdx233.r2droid.feature.types.data.StructTypeInfo
import top.wsdx233.r2droid.feature.types.data.TypeCategory
import top.wsdx233.r2droid.feature.types.data.TypeLinkInfo
import top.wsdx233.r2droid.feature.types.data.TypedefTypeInfo
import top.wsdx233.r2droid.feature.types.data.UnionTypeInfo
import top.wsdx233.r2droid.ui.theme.R2droidTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TypesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var hostView: View

    private val sampleSnapshot = LocalTypesSnapshot(
        structs = listOf(
            StructTypeInfo(
                name = "PacketHeader",
                format = "dwq[16]b magic flags payload_ptr digest",
                fields = listOf(
                    StructField("magic", "uint32_t", 0, 4),
                    StructField("flags", "uint16_t", 4, 2),
                    StructField("payload_ptr", "uint8_t *", 8, 8),
                    StructField("digest", "uint8_t", 16, 16, arraySize = 16)
                ),
                totalSize = 32
            ),
            StructTypeInfo(
                name = "SessionCtx",
                format = "qqd state_ptr peer_addr fd",
                fields = listOf(
                    StructField("state_ptr", "void *", 0, 8),
                    StructField("peer_addr", "uint64_t", 8, 8),
                    StructField("fd", "int32_t", 16, 4)
                ),
                totalSize = 20
            )
        ),
        enums = listOf(
            EnumTypeInfo(
                name = "PacketType",
                members = listOf(
                    EnumMember("PKT_HELLO", 1),
                    EnumMember("PKT_AUTH", 2),
                    EnumMember("PKT_DATA", 0x10),
                    EnumMember("PKT_CLOSE", 0xFF)
                )
            )
        ),
        unions = listOf(
            UnionTypeInfo(
                name = "ArgValue",
                format = "0qdx u64 i32 ptr",
                fields = listOf(
                    StructField("u64", "uint64_t", 0, 8),
                    StructField("i32", "int32_t", 0, 4),
                    StructField("ptr", "void *", 0, 8)
                ),
                totalSize = 8
            )
        ),
        typedefs = listOf(
            TypedefTypeInfo("packet_ctx_t", "struct SessionCtx *"),
            TypedefTypeInfo("u32", "uint32_t")
        ),
        links = listOf(
            TypeLinkInfo(0x404020L, "PacketHeader"),
            TypeLinkInfo(0x404080L, "SessionCtx")
        )
    )

    private val sampleVariables = FunctionVariablesData(
        reg = listOf(
            FunctionVariable("ctx", "reg", "int64_t", "reg", refDisplay = "rdi"),
            FunctionVariable("buf", "reg", "void *", "reg", refDisplay = "rsi")
        ),
        bp = listOf(
            FunctionVariable("var_18h", "var", "uint32_t", "bp", refDisplay = "rbp-0x18"),
            FunctionVariable("var_8h", "var", "char *", "bp", refDisplay = "rbp-0x8")
        )
    )

    private val sampleFunctionInfo = FunctionDetailInfo(
        name = "sym.parse_packet",
        addr = 0x401280L,
        size = 312,
        realSize = 296,
        noReturn = false,
        stackFrame = 48,
        callType = "amd64",
        cost = 84,
        cc = 6,
        bits = 64,
        type = "fcn",
        nbbs = 11,
        ninstrs = 64,
        edges = 15,
        signature = "int sym.parse_packet(struct PacketHeader *hdr, size_t len)",
        minAddr = 0x401280L,
        maxAddr = 0x4013B8L,
        nlocals = 3,
        nargs = 2,
        isPure = false,
        isLineal = false,
        indegree = 3,
        outdegree = 4,
        diffType = ""
    )

    private val sampleCcs = listOf(
        CallingConventionInfo("amd64", "rax", listOf("rdi", "rsi", "rdx", "rcx", "r8", "r9"), "rax amd64 (rdi, rsi, rdx, rcx, r8, r9)"),
        CallingConventionInfo("ms", "rax", listOf("rcx", "rdx", "r8", "r9"), "rax ms (rcx, rdx, r8, r9)"),
        CallingConventionInfo("amd64syscall", "rax", listOf("rdi", "rsi", "rdx", "r10", "r8", "r9"), "rax amd64syscall (rdi, rsi, rdx, r10, r8, r9)")
    )

    @Test
    fun localTypesScreenFiltersAndRendersLightAndDark() {
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                var cat by remember { mutableStateOf(TypeCategory.ALL) }
                var query by remember { mutableStateOf("") }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    LocalTypesContent(
                        snapshot = sampleSnapshot,
                        loading = false,
                        mutating = false,
                        loadError = null,
                        notice = TypesNotice("Imported header: protocol.h"),
                        selectedCategory = cat,
                        searchQuery = query,
                        linkPreviews = mapOf(
                            0x404020L to "magic : 0x00404020 = 0xdeadbeef\nflags : 0x00404024 = 0x0003"
                        ),
                        onSelectCategory = { cat = it },
                        onSearchQueryChange = { query = it },
                        onRefresh = {},
                        onDismissNotice = {},
                        onOpenDefineDialog = {},
                        onOpenImportDialog = {},
                        onOpenLinkDialog = {},
                        onInspectType = {},
                        onUnlinkAddress = {},
                        onToggleLinkPreview = {},
                        onJumpToHex = {},
                        onJumpToDisasm = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Local Types").assertIsDisplayed()
        compose.onNodeWithText("PacketHeader").assertIsDisplayed()
        compose.onNodeWithText("PacketType").assertIsDisplayed()
        compose.onNodeWithText("Links").assertIsDisplayed()
        maybeSaveScreenshot("types_all_light.png")

        // Switch to Links category (directly visible in FlowRow without horizontal scroll)
        compose.onNodeWithText("Links").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("0x404020").assertIsDisplayed()
        compose.onNodeWithText("Hide Memory").assertIsDisplayed()
        maybeSaveScreenshot("types_links_light.png")
    }

    @Test
    fun localTypesDarkThemeRendersCleanly() {
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    LocalTypesContent(
                        snapshot = sampleSnapshot,
                        loading = false,
                        mutating = false,
                        loadError = null,
                        notice = null,
                        selectedCategory = TypeCategory.ALL,
                        searchQuery = "",
                        onSelectCategory = {},
                        onSearchQueryChange = {},
                        onRefresh = {},
                        onDismissNotice = {},
                        onOpenDefineDialog = {},
                        onOpenImportDialog = {},
                        onOpenLinkDialog = {},
                        onInspectType = {},
                        onUnlinkAddress = {},
                        onToggleLinkPreview = {},
                        onJumpToHex = {},
                        onJumpToDisasm = {}
                    )
                }
            }
        }

        compose.onNodeWithText("PacketHeader").assertIsDisplayed()
        compose.onNodeWithText("Links").assertIsDisplayed()
        maybeSaveScreenshot("types_all_dark.png")
    }

    @Test
    fun typeInspectorStructAndEnumRenderLayoutAndCSource() {
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(Color(0xFF0A0D12)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(top = 18.dp)) {
                            TypeInspectorContent(
                                inspected = InspectedType.Struct(sampleSnapshot.structs.first()),
                                cSource = "struct PacketHeader {\n    uint32_t magic;\n    uint16_t flags;\n    uint8_t *payload_ptr;\n    uint8_t digest[16];\n};",
                                cSourceLoading = false,
                                linkedAddresses = sampleSnapshot.linkedAddressesFor("PacketHeader"),
                                busy = false,
                                onDismiss = {},
                                onCopy = {},
                                onEdit = { _, _ -> },
                                onRequestLink = {},
                                onRequestDelete = {},
                                onJumpToDisasm = {}
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("PacketHeader").assertIsDisplayed()
        compose.onNodeWithText("32 B (0x20)").assertIsDisplayed()
        compose.onNodeWithText("payload_ptr").assertIsDisplayed()
        maybeSaveScreenshot("inspector_struct_layout_dark.png")

        compose.onNodeWithText("C Source").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("pf Format").assertIsDisplayed()
        maybeSaveScreenshot("inspector_struct_c_dark.png")
    }

    @Test
    fun functionVariablesDialogRetypesVariableWithSuggestions() {
        var retyped: Pair<String, String>? = null
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FunctionVariablesDialogCard(
                        variables = sampleVariables,
                        isLoading = false,
                        targetAddress = 0x401280L,
                        onDismiss = {},
                        onRename = { _, _ -> },
                        onRetype = { name, type -> retyped = name to type },
                        typeSuggestions = sampleSnapshot.allTypeSuggestions(),
                        onOpenTypesManager = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        compose.onNodeWithText("ctx").assertIsDisplayed()
        compose.onNodeWithText("@ rdi").assertIsDisplayed()
        // Open retype inline editor on first variable ("ctx")
        compose.onAllNodesWithText("Retype")[0].performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("func_vars_retype_light.png")

        // Tap a suggested local struct pointer type immediately (without clearing "int64_t") and confirm
        compose.onNodeWithText("struct PacketHeader *").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals("ctx" to "struct PacketHeader *", retyped)
    }

    @Test
    fun functionVariablesFullDialogWrapperWorks() {
        var retyped: Pair<String, String>? = null
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                FunctionVariablesDialog(
                    variables = sampleVariables,
                    isLoading = false,
                    targetAddress = 0x401280L,
                    onDismiss = {},
                    onRename = { _, _ -> },
                    onRetype = { name, type -> retyped = name to type },
                    typeSuggestions = sampleSnapshot.allTypeSuggestions(),
                    onOpenTypesManager = {}
                )
            }
        }
        compose.onAllNodesWithText("Retype")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("struct PacketHeader *").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals("ctx" to "struct PacketHeader *", retyped)
    }

    @Test
    fun functionInfoDialogEditsSignatureAndCallingConvention() {
        var updatedSig: String? = null
        var updatedCc: String? = null
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FunctionInfoDialogCard(
                        functionInfo = sampleFunctionInfo,
                        isLoading = false,
                        targetAddress = 0x401280L,
                        onDismiss = {},
                        onRename = {},
                        onJump = {},
                        onUpdateSignature = { updatedSig = it },
                        onUpdateCallingConvention = { updatedCc = it },
                        availableCallingConventions = sampleCcs,
                        typeSuggestions = sampleSnapshot.allTypeSuggestions(),
                        onOpenVariables = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        compose.onNodeWithText("sym.parse_packet").assertIsDisplayed()
        compose.onNodeWithText("amd64").assertIsDisplayed()
        maybeSaveScreenshot("func_info_dark.png")

        // Open calling convention picker and switch to "ms"
        compose.onNodeWithText("Change").performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("func_info_cc_picker_dark.png")
        compose.onNodeWithText("ms").performClick()
        assertEquals("ms", updatedCc)

        // Open signature editor and confirm
        compose.onNodeWithText("Edit Signature").performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("func_info_sig_edit_dark.png")
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(sampleFunctionInfo.signature, updatedSig)
    }

    @Test
    fun functionInfoFullDialogWrapperWorks() {
        var updatedCc: String? = null
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                FunctionInfoDialog(
                    functionInfo = sampleFunctionInfo,
                    isLoading = false,
                    targetAddress = 0x401280L,
                    onDismiss = {},
                    onRename = {},
                    onJump = {},
                    onUpdateSignature = {},
                    onUpdateCallingConvention = { updatedCc = it },
                    availableCallingConventions = sampleCcs,
                    typeSuggestions = sampleSnapshot.allTypeSuggestions(),
                    onOpenVariables = {}
                )
            }
        }
        compose.onNodeWithText("Change").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ms").performClick()
        assertEquals("ms", updatedCc)
    }

    @Test
    fun cDeclarationEditorAndLinkDialogRenderCleanly() {
        var submitted: String? = null
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CDeclarationEditorCard(
                        editingTypeName = null,
                        initialCode = "",
                        busy = false,
                        errorMessage = null,
                        onDismiss = {},
                        onSubmit = { submitted = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        compose.onNodeWithText("+ struct").performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("c_editor_light.png")
        compose.onNodeWithText("Apply").performClick()
        assertEquals(true, submitted?.contains("struct MyStruct"))
    }

    @Test
    fun linkTypeDialogValidatesAndSubmits() {
        var linked: Pair<String, Long>? = null
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinkTypeDialogCard(
                        initialTypeName = null,
                        availableTypes = sampleSnapshot.allLinkableTypeNames(),
                        cursorAddress = 0x404020L,
                        busy = false,
                        errorMessage = null,
                        onDismiss = {},
                        onConfirm = { t, a -> linked = t to a },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        maybeSaveScreenshot("link_type_light.png")
        compose.onNodeWithText("PacketHeader").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bind Type").performClick()
        assertEquals("PacketHeader" to 0x404020L, linked)
    }

    private fun maybeSaveScreenshot(name: String) {
        val dir = (System.getProperty("typesScreenshotDir") ?: System.getenv("TYPES_SCREENSHOT_DIR"))
            ?.takeIf { it.isNotBlank() } ?: return
        compose.runOnIdle {
            val outDir = File(dir).apply { mkdirs() }
            val width = hostView.width.coerceAtLeast(1)
            val height = hostView.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            hostView.draw(Canvas(bitmap))
            File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
