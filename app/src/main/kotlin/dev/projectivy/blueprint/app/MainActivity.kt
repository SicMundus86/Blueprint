package dev.projectivy.blueprint.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.javiersantos.piracychecker.PiracyChecker
import dev.jahir.blueprint.ui.activities.BottomNavigationBlueprintActivity
import dev.projectivy.blueprint.app.R
import dev.jahir.kuper.BuildConfig

class MainActivity : BottomNavigationBlueprintActivity() {

    override val billingEnabled = true

    override fun amazonInstallsEnabled(): Boolean = false
    override fun checkLPF(): Boolean = true
    override fun checkStores(): Boolean = true
    override val isDebug: Boolean = BuildConfig.DEBUG
    override fun getLicKey(): String? = "MIIBIjANBgkqhkiGgKglYGYGihLuihUuhhuBlouBkuiu"

    override fun getLicenseChecker(): PiracyChecker? {
        destroyChecker() // Important
        return null
    }

    override fun defaultTheme(): Int = R.style.MyApp_Default
    override fun amoledTheme(): Int = R.style.MyApp_Default_Amoled
    override fun defaultMaterialYouTheme(): Int = R.style.MyApp_Default_MaterialYou
    override fun amoledMaterialYouTheme(): Int = R.style.MyApp_Default_Amoled_MaterialYou

    private val REQUEST_CODE_STORAGE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStoragePermissionAndUpdate()
    }

    private fun checkStoragePermissionAndUpdate() {
        // Only request WRITE_EXTERNAL_STORAGE for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_STORAGE
                )
            } else {
                // Permission already granted
                UpdateManager.checkForUpdate(this)
            }
        } else {
            // Android 10+ doesn’t require WRITE_EXTERNAL_STORAGE for scoped downloads
            UpdateManager.checkForUpdate(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                UpdateManager.checkForUpdate(this)
            } else {
                // Permission denied, optionally show a message or fallback
            }
        }
    }
}


