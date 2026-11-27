package top.wsdx233.r2droid.feature.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object PluginAndroidBridge {

    private data class InvocationMatch<T>(
        val executable: T,
        val arguments: Array<Any?>,
        val score: Int
    )

    private data class ArgumentConversion(
        val value: Any?,
        val score: Int
    )

    const val NO_SUCH_FIELD = "__r2droid_android_no_such_field__"

    private val objectRefs = ConcurrentHashMap<String, Any>()
    private val objectCounter = AtomicLong(0L)
    private val classAliases = mapOf(
        "intent" to "android.content.Intent",
        "uri" to "android.net.Uri"
    )

    fun releaseAll(pluginId: String) {
        val prefix = "$pluginId#android#"
        objectRefs.keys
            .filter { it.startsWith(prefix) }
            .forEach { key -> objectRefs.remove(key) }
    }

    fun importClassPayload(className: String): String {
        return stringPayload(resolveAllowedAndroidClass(className).name)
    }

    fun newPayload(pluginId: String, className: String, argsJson: String): String {
        val clazz = resolveAllowedAndroidClass(className)
        val args = parseArgs(pluginId, argsJson)
        val match = findBestConstructor(clazz, args)
            ?: error("no matching constructor for ${clazz.name}")
        val instance = match.executable.newInstance(*match.arguments)
        return payloadForValue(pluginId, instance)
    }

    fun callPayload(pluginId: String, refId: String, methodName: String, argsJson: String): String {
        val target = resolveReference(pluginId, refId)
        val args = parseArgs(pluginId, argsJson)
        val methods = target.javaClass.methods.filter {
            !Modifier.isStatic(it.modifiers) && it.name == methodName
        }
        val match = findBestMethod(methods, args)
            ?: error("no matching method: ${target.javaClass.name}#$methodName")
        val result = match.executable.invoke(target, *match.arguments)
        return payloadForValue(pluginId, result)
    }

    fun callStaticPayload(pluginId: String, className: String, methodName: String, argsJson: String): String {
        val clazz = resolveAllowedAndroidClass(className)
        val args = parseArgs(pluginId, argsJson)
        val methods = clazz.methods.filter {
            Modifier.isStatic(it.modifiers) && it.name == methodName
        }
        val match = findBestMethod(methods, args)
            ?: error("no matching static method: ${clazz.name}#$methodName")
        val result = match.executable.invoke(null, *match.arguments)
        return payloadForValue(pluginId, result)
    }

    fun getStaticFieldPayload(pluginId: String, className: String, fieldName: String): String {
        val clazz = resolveAllowedAndroidClass(className)
        val field = clazz.fields.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.name == fieldName
        } ?: return NO_SUCH_FIELD
        return payloadForValue(pluginId, field.get(null))
    }

    fun startActivityPayload(appContext: Context, pluginId: String, refId: String): String {
        val intent = resolveReference(pluginId, refId) as? Intent
            ?: error("android reference is not an Intent")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return stringPayload("ok")
    }

    fun getLaunchIntentForPackagePayload(appContext: Context, pluginId: String, packageName: String): String {
        val normalized = packageName.trim()
        require(normalized.isNotBlank()) { "package name is blank" }
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(normalized)
        return payloadForValue(pluginId, launchIntent)
    }

    fun getCurrentActivityPayload(pluginId: String, activity: Activity?): String {
        return payloadForValue(pluginId, activity)
    }

    fun releasePayload(pluginId: String, refId: String): String {
        releaseReference(pluginId, refId)
        return stringPayload("ok")
    }

    fun errorPayload(throwable: Throwable): String {
        val message = throwable.message ?: throwable::class.java.simpleName
        return buildJsonObject {
            put("kind", "error")
            put("message", message)
        }.toString()
    }

    fun javascriptBridge(hostObjectName: String, includeCurrentActivity: Boolean = false): String {
        return """
            (function() {
              const __host = $hostObjectName;
              if (!__host) {
                return;
              }
              const __NO_FIELD = "${NO_SUCH_FIELD}";

              function __serializeArg(value) {
                if (value === undefined || value === null) {
                  return null;
                }
                if (value && typeof value === 'object' && typeof value.__androidRef === 'string') {
                  return { __androidRef: value.__androidRef };
                }
                if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
                  return value;
                }
                throw new Error('Only primitive values and Android references are supported');
              }

              function __invokeArgs(argsLike) {
                return JSON.stringify(Array.prototype.slice.call(argsLike).map(__serializeArg));
              }

              function __unwrap(raw) {
                const text = raw == null ? '' : String(raw);
                if (text === __NO_FIELD) {
                  return __NO_FIELD;
                }
                const payload = JSON.parse(text);
                switch (payload.kind) {
                  case 'null':
                    return null;
                  case 'boolean':
                  case 'number':
                  case 'string':
                    return payload.value;
                  case 'ref':
                    return __createObjectProxy(payload.ref, payload.className);
                  case 'error':
                    throw new Error(payload.message || 'Android bridge error');
                  default:
                    throw new Error('Unknown Android bridge payload');
                }
              }

              function __refOf(value) {
                if (value && typeof value === 'object' && typeof value.__androidRef === 'string') {
                  return value.__androidRef;
                }
                if (typeof value === 'string') {
                  return value;
                }
                throw new Error('Expected Android object reference');
              }

              function __createObjectProxy(refId, className) {
                const target = { __androidRef: refId, __androidClassName: className };
                return new Proxy(target, {
                  get(current, prop) {
                    if (prop in current) {
                      return current[prop];
                    }
                    if (prop === 'release') {
                      return function() {
                        return __unwrap(__host.androidRelease(refId));
                      };
                    }
                    if (prop === 'toJSON') {
                      return function() {
                        return { __androidRef: refId };
                      };
                    }
                    if (prop === 'toString') {
                      return function() {
                        return '[AndroidObject ' + className + ']';
                      };
                    }
                    if (prop === 'then' || typeof prop !== 'string') {
                      return undefined;
                    }
                    return function() {
                      return __unwrap(__host.androidCall(refId, String(prop), __invokeArgs(arguments)));
                    };
                  }
                });
              }

              function __createClassProxy(className) {
                const target = { __androidClassName: className };
                return new Proxy(target, {
                  get(current, prop) {
                    if (prop in current) {
                      return current[prop];
                    }
                    if (prop === 'newInstance') {
                      return function() {
                        return __unwrap(__host.androidNew(className, __invokeArgs(arguments)));
                      };
                    }
                    if (prop === 'toString') {
                      return function() {
                        return '[AndroidClass ' + className + ']';
                      };
                    }
                    if (prop === 'then' || typeof prop !== 'string') {
                      return undefined;
                    }
                    const fieldValue = __unwrap(__host.androidGetStaticField(className, String(prop)));
                    if (fieldValue !== __NO_FIELD) {
                      return fieldValue;
                    }
                    return function() {
                      return __unwrap(__host.androidCallStatic(className, String(prop), __invokeArgs(arguments)));
                    };
                  }
                });
              }

              globalThis.android = {
                __r2droidBridge: true,
                importClass: function(className) {
                  return __createClassProxy(__unwrap(__host.androidImportClass(className)));
                },
                startActivity: function(intent) {
                  return __unwrap(__host.androidStartActivity(__refOf(intent)));
                },
                getLaunchIntentForPackage: function(packageName) {
                  return __unwrap(__host.androidGetLaunchIntentForPackage(packageName));
                },
                getCurrentActivity: function() {
                  return __unwrap(__host.androidGetCurrentActivity());
                },
                release: function(target) {
                  return __unwrap(__host.androidRelease(__refOf(target)));
                }
              };
              globalThis.androidBridgeReady = true;
              ${if (includeCurrentActivity) "globalThis.currentActivity = globalThis.android.getCurrentActivity();" else ""}

              if (typeof globalThis.dispatchEvent === 'function' && typeof globalThis.Event === 'function') {
                try {
                  globalThis.dispatchEvent(new Event('r2pluginready'));
                } catch (_) {
                }
              }
            })();
        """.trimIndent()
    }

    private fun payloadForValue(pluginId: String, value: Any?): String {
        return when (value) {
            null -> buildJsonObject {
                put("kind", "null")
            }.toString()

            is Boolean -> buildJsonObject {
                put("kind", "boolean")
                put("value", value)
            }.toString()

            is Int -> numberPayload(value)
            is Long -> numberPayload(value)
            is Float -> numberPayload(value)
            is Double -> numberPayload(value)
            is Short -> numberPayload(value.toInt())
            is Byte -> numberPayload(value.toInt())

            is CharSequence -> stringPayload(value.toString())
            is Enum<*> -> stringPayload(value.name)

            else -> buildJsonObject {
                put("kind", "ref")
                put("ref", registerReference(pluginId, value))
                put("className", value.javaClass.name)
            }.toString()
        }
    }

    private fun stringPayload(value: String): String {
        return buildJsonObject {
            put("kind", "string")
            put("value", value)
        }.toString()
    }

    private fun numberPayload(value: Number): String {
        return buildJsonObject {
            put("kind", "number")
            when (value) {
                is Int -> put("value", value)
                is Long -> put("value", value)
                is Float -> put("value", value)
                is Double -> put("value", value)
                else -> put("value", value.toDouble())
            }
        }.toString()
    }

    private fun parseArgs(pluginId: String, argsJson: String): List<Any?> {
        if (argsJson.isBlank()) return emptyList()
        val element = Json.parseToJsonElement(argsJson)
        val array = element as? JsonArray ?: error("android bridge arguments must be a JSON array")
        return array.map { item -> decodeArg(pluginId, item) }
    }

    private fun decodeArg(pluginId: String, element: JsonElement): Any? {
        return when (element) {
            JsonNull -> null
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.content.equals("true", ignoreCase = true) || element.content.equals("false", ignoreCase = true) ->
                    element.content.toBooleanStrict()
                element.longOrNull != null -> element.longOrNull
                element.doubleOrNull != null -> element.doubleOrNull
                else -> element.content
            }

            else -> {
                val refId = element.jsonObject["__androidRef"]?.jsonPrimitive?.content
                    ?: error("unsupported android bridge argument: $element")
                resolveReference(pluginId, refId)
            }
        }
    }

    private fun registerReference(pluginId: String, value: Any): String {
        val refId = "$pluginId#android#${objectCounter.incrementAndGet()}"
        // Store Activity/Context references weakly so destroyed Activities are never leaked.
        objectRefs[refId] = if (value is Context) WeakReference(value) else value
        return refId
    }

    private fun resolveReference(pluginId: String, refId: String): Any {
        check(refId.startsWith("$pluginId#android#")) { "android reference does not belong to plugin" }
        val stored = objectRefs[refId] ?: error("android reference not found: $refId")
        return if (stored is WeakReference<*>) {
            stored.get() ?: run {
                objectRefs.remove(refId)
                error("android reference was released: $refId")
            }
        } else {
            stored
        }
    }

    private fun releaseReference(pluginId: String, refId: String) {
        check(refId.startsWith("$pluginId#android#")) { "android reference does not belong to plugin" }
        objectRefs.remove(refId)
    }

    private fun resolveAllowedAndroidClass(className: String): Class<*> {
        val trimmed = className.trim()
        require(trimmed.isNotBlank()) { "android class name is blank" }
        val resolvedName = classAliases[trimmed.lowercase()] ?: trimmed
        check(resolvedName.startsWith("android.") || resolvedName.startsWith("androidx.")) {
            "android class not allowed: $className"
        }
        return Class.forName(resolvedName)
    }

    private fun findBestConstructor(clazz: Class<*>, args: List<Any?>): InvocationMatch<Constructor<*>>? {
        return clazz.constructors
            .mapNotNull { constructor ->
                matchInvocation(constructor, constructor.parameterTypes, args)
            }
            .minByOrNull { it.score }
    }

    private fun findBestMethod(methods: List<Method>, args: List<Any?>): InvocationMatch<Method>? {
        return methods
            .mapNotNull { method ->
                matchInvocation(method, method.parameterTypes, args)
            }
            .minByOrNull { it.score }
    }

    private fun <T> matchInvocation(
        executable: T,
        parameterTypes: Array<Class<*>>,
        args: List<Any?>
    ): InvocationMatch<T>? {
        if (parameterTypes.size != args.size) return null

        val convertedArgs = arrayOfNulls<Any?>(args.size)
        var totalScore = 0

        for (index in args.indices) {
            val conversion = convertArg(args[index], parameterTypes[index]) ?: return null
            convertedArgs[index] = conversion.value
            totalScore += conversion.score
        }

        @Suppress("UNCHECKED_CAST")
        return InvocationMatch(executable, convertedArgs as Array<Any?>, totalScore)
    }

    private fun convertArg(value: Any?, targetType: Class<*>): ArgumentConversion? {
        val boxedType = boxType(targetType)
        if (value == null) {
            return if (targetType.isPrimitive) null else ArgumentConversion(null, 8)
        }

        if (boxedType.isInstance(value)) {
            return ArgumentConversion(value, 0)
        }

        if (boxedType == String::class.java && value is CharSequence) {
            return ArgumentConversion(value.toString(), 1)
        }

        if (boxedType == java.lang.Boolean::class.java && value is Boolean) {
            return ArgumentConversion(value, 0)
        }

        if (Number::class.java.isAssignableFrom(boxedType) && value is Number) {
            return convertNumber(value, boxedType)
        }

        return if (boxedType.isAssignableFrom(value.javaClass)) {
            ArgumentConversion(value, 1)
        } else {
            null
        }
    }

    private fun convertNumber(value: Number, boxedType: Class<*>): ArgumentConversion? {
        val doubleValue = value.toDouble()
        val isIntegral = doubleValue % 1.0 == 0.0
        return when (boxedType) {
            java.lang.Integer::class.java -> {
                if (!isIntegral || doubleValue !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) null
                else ArgumentConversion(value.toInt(), if (value is Int) 0 else 1)
            }

            java.lang.Long::class.java -> {
                if (!isIntegral) null
                else ArgumentConversion(value.toLong(), if (value is Long) 0 else 1)
            }

            java.lang.Float::class.java -> ArgumentConversion(value.toFloat(), if (value is Float) 0 else 2)
            java.lang.Double::class.java -> ArgumentConversion(value.toDouble(), if (value is Double) 0 else 1)

            java.lang.Short::class.java -> {
                if (!isIntegral || doubleValue !in Short.MIN_VALUE.toDouble()..Short.MAX_VALUE.toDouble()) null
                else ArgumentConversion(value.toShort(), 2)
            }

            java.lang.Byte::class.java -> {
                if (!isIntegral || doubleValue !in Byte.MIN_VALUE.toDouble()..Byte.MAX_VALUE.toDouble()) null
                else ArgumentConversion(value.toByte(), 3)
            }

            else -> null
        }
    }

    private fun boxType(type: Class<*>): Class<*> {
        return when (type) {
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            else -> type
        }
    }
}
