package top.wsdx233.r2droid.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.wsdx233.r2droid.core.data.model.UpdateInfo

object UpdateManager {
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    /**
     * Check for updates silently (no error thrown)
     * @return true if update is available, false otherwise
     */
    suspend fun checkForUpdateSilently(): Boolean {
        if (_isChecking.value) return false

        _isChecking.value = true
        return try {
            val update = UpdateChecker.checkForUpdate()
            _updateInfo.value = update
            update != null
        } catch (e: Exception) {
            // Silent failure - don't show error to user
            false
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Check for updates and throw exception on failure
     * Used for manual checks where user expects feedback
     */
    suspend fun checkForUpdate(): UpdateInfo? {
        if (_isChecking.value) return null

        _isChecking.value = true
        return try {
            val update = UpdateChecker.checkForUpdate()
            _updateInfo.value = update
            update
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Clear the current update info (e.g., after user dismisses dialog)
     */
    fun clearUpdateInfo() {
        _updateInfo.value = null
    }
}
