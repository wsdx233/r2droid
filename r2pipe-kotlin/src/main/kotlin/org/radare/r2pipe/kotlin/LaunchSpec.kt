package org.radare.r2pipe.kotlin

import java.io.File

data class LaunchSpec(
    val command: List<String>,
    val workingDirectory: File? = null,
    val environment: Map<String, String> = emptyMap()
)
