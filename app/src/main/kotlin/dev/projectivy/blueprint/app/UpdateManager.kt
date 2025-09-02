package dev.projectivy.blueprint.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object UpdateManager {

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/SicMundus86/ProjectivyIconPack/refs/heads/main/Latestrelease/version.json"

    fun checkForUpdate(context: Context) {
        val client = OkHttpClient()
        val request = Request.Builder().url(VERSION_JSON_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UpdateManager", "Failed to fetch version JSON", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { json ->
                    val obj = JSONObject(json)

                    // Non-nullable currentVersion
                    val currentVersion: String = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                    } catch (e: Exception) {
                        "0.0.0"
                    }

                    // Non-nullable latestVersion and apkUrl
                    val latestVersion: String = try {
                        obj.getString("latestVersion")
                    } catch (e: Exception) {
                        ""
                    }

                    val apkUrl: String = try {
                        obj.getString("updateUrl")
                    } catch (e: Exception) {
                        ""
                    }

                    Log.d("UpdateManager", "Current version: $currentVersion, Latest: $latestVersion")

                    if (latestVersion.isNotEmpty() && isUpdateAvailable(currentVersion, latestVersion)) {
                        showUpdateDialog(context, latestVersion, apkUrl)
                    }
                }
            }
        })
    }

    // Compare semantic versions
    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(currentParts.size, latestParts.size)

        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true   // latest is newer
            if (c > l) return false  // current is newer
        }
        return false  // versions are equal
    }

    private fun showUpdateDialog(context: Context, latestVersion: String, apkUrl: String) {
        Handler(Looper.getMainLooper()).post {
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_update, null)

            val title = dialogView.findViewById<TextView>(R.id.dialog_title)
            val message = dialogView.findViewById<TextView>(R.id.dialog_message)
            val updateButton = dialogView.findViewById<Button>(R.id.button_update)
            val laterButton = dialogView.findViewById<Button>(R.id.button_later)

            title.text = "Update Available"
            message.text = "A new version ($latestVersion) is available."

            val dialog = Dialog(context)
            dialog.setContentView(dialogView)
            dialog.setCancelable(true)

            updateButton.setOnClickListener {
                dialog.dismiss()
                downloadApk(context, apkUrl, "update.apk")
            }
            laterButton.setOnClickListener { dialog.dismiss() }

            val focusListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.05f else 1.0f)
                    .scaleY(if (hasFocus) 1.05f else 1.0f)
                    .setDuration(100)
                    .start()
                if (v is Button) v.setTextColor(if (hasFocus) Color.parseColor("#FFA500") else Color.WHITE)
            }

            updateButton.onFocusChangeListener = focusListener
            laterButton.onFocusChangeListener = focusListener

            dialog.show()
        }
    }

    private fun downloadApk(context: Context, url: String, fileName: String) {
        Handler(Looper.getMainLooper()).post {
            val inflater = LayoutInflater.from(context)
            val progressView = inflater.inflate(R.layout.dialog_download_progress, null)
            val progressDialog = Dialog(context)
            progressDialog.setContentView(progressView)
            progressDialog.setCancelable(false)
            progressDialog.show()

            val progressBar = progressView.findViewById<ProgressBar>(R.id.download_progress)
            val percentageText = progressView.findViewById<TextView>(R.id.download_percentage)

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("UpdateManager", "APK download failed", e)
                    Handler(Looper.getMainLooper()).post { progressDialog.dismiss() }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.e("UpdateManager", "APK download failed: ${response.code}")
                        Handler(Looper.getMainLooper()).post { progressDialog.dismiss() }
                        return
                    }

                    response.body?.byteStream()?.let { inputStream ->
                        try {
                            val apkFile = File(context.filesDir, fileName)
                            val output = FileOutputStream(apkFile)
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            val fileSize = response.body!!.contentLength()

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val progress = (totalRead * 100 / fileSize).toInt()
                                Handler(Looper.getMainLooper()).post {
                                    progressBar.progress = progress
                                    percentageText.text = "$progress%"
                                }
                            }
                            output.flush()
                            output.close()
                            inputStream.close()
                            Handler(Looper.getMainLooper()).post {
                                progressDialog.dismiss()
                                installApk(context, apkFile)
                            }
                        } catch (e: Exception) {
                            Log.e("UpdateManager", "Failed to save APK", e)
                            Handler(Looper.getMainLooper()).post { progressDialog.dismiss() }
                        }
                    }
                }
            })
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileProvider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to launch installer", e)
        }
    }
}




















