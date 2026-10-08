package top.wsdx233.r2droid.util

import android.content.res.AssetManager
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Identity metadata generated alongside the bundled native runtime.
 *
 * The manifest is deliberately strict: an APK without generated metadata must
 * not silently reuse an install produced by a different runtime bundle.
 */
data class BundledRuntimeManifest(
    val schema: Int,
    val abi: String,
    val build: Build,
    val proot: Proot,
    val radare2: Radare2?,
    val plugins: Map<String, String>
) {
    data class Build(val ndk: String, val androidApi: Int)

    data class Proot(
        val source: String,
        val ref: String,
        val commit: String,
        val sha256: String
    )

    data class Radare2(
        val source: String,
        val ref: String,
        val version: String,
        val commit: String,
        val sha256: String,
        val dataSha256: String,
        val libcxxSha256: String,
        val bundleId: String
    )

    companion object {
        const val ASSET_NAME = "runtime-manifest.json"
        private const val EXPECTED_SCHEMA = 1
        private const val EXPECTED_ABI = "arm64-v8a"

        /** Reads and validates the shared manifest, requiring only proot data. */
        @JvmStatic
        fun fromAssets(assets: AssetManager, requireRadare2: Boolean = false): BundledRuntimeManifest {
            val content = try {
                assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (error: IOException) {
                throw IllegalStateException("Missing mandatory $ASSET_NAME asset", error)
            }
            return parse(content, requireRadare2)
        }

        /** Package-visible for deterministic identity tests without Android assets. */
        @JvmStatic
        fun parse(content: String, requireRadare2: Boolean = false): BundledRuntimeManifest {
            val root = try {
                JSONObject(content)
            } catch (error: JSONException) {
                throw IllegalStateException("Invalid $ASSET_NAME JSON", error)
            }
            try {
                val schema = root.getInt("schema")
                require(schema == EXPECTED_SCHEMA) { "Unsupported runtime manifest schema: $schema" }
                val abi = root.getString("abi").required("abi")
                require(abi == EXPECTED_ABI) { "Unsupported runtime ABI: $abi" }

                val buildObject = root.getJSONObject("build")
                val build = Build(
                    ndk = buildObject.getString("ndk").required("build.ndk"),
                    androidApi = buildObject.getInt("androidApi").also {
                        require(it > 0) { "build.androidApi must be positive" }
                    }
                )
                val prootObject = root.getJSONObject("proot")
                val proot = Proot(
                    source = prootObject.getString("source").required("proot.source"),
                    ref = prootObject.getString("ref").required("proot.ref"),
                    commit = prootObject.getString("commit").required("proot.commit"),
                    sha256 = digest(prootObject.getString("sha256"), "proot.sha256")
                )

                val hasRadare2 = root.has("radare2")
                if (requireRadare2 && !hasRadare2) {
                    throw IllegalStateException("$ASSET_NAME has no radare2 metadata")
                }
                val radare2 = if (hasRadare2) {
                    val value = root.getJSONObject("radare2")
                    Radare2(
                        source = value.getString("source").required("radare2.source"),
                        ref = value.getString("ref").required("radare2.ref"),
                        version = value.getString("version").required("radare2.version"),
                        commit = value.getString("commit").required("radare2.commit"),
                        sha256 = digest(value.getString("sha256"), "radare2.sha256"),
                        dataSha256 = digest(value.getString("dataSha256"), "radare2.dataSha256"),
                        libcxxSha256 = digest(value.getString("libcxxSha256"), "radare2.libcxxSha256"),
                        bundleId = digest(value.getString("bundleId"), "radare2.bundleId")
                    )
                } else {
                    null
                }

                val plugins = if (requireRadare2) {
                    val pluginObject = root.optJSONObject("plugins")
                        ?: throw IllegalStateException("$ASSET_NAME has no plugins metadata")
                    val values = mutableMapOf<String, String>()
                    val keys = pluginObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = pluginObject.optString(key, "").trim()
                        if (value.isNotEmpty()) values[key] = value
                    }
                    require(values.isNotEmpty()) { "$ASSET_NAME plugins metadata is empty" }
                    values
                } else {
                    emptyMap()
                }
                if (requireRadare2 && radare2 == null) {
                    throw IllegalStateException("$ASSET_NAME has no radare2 metadata")
                }
                return BundledRuntimeManifest(schema, abi, build, proot, radare2, plugins)
            } catch (error: JSONException) {
                throw IllegalStateException("Incomplete $ASSET_NAME", error)
            }
        }

        private fun String.required(field: String): String = trim().also {
            require(it.isNotEmpty()) { "$field must not be empty" }
        }

        private fun digest(value: String, field: String): String {
            val normalized = value.trim().lowercase()
            require(Regex("[0-9a-f]{64}").matches(normalized)) { "$field must be a SHA-256 digest" }
            return normalized
        }
    }
}
