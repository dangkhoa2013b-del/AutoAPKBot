package com.bot.autoapk

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private var selectedFileBytes: ByteArray? = null
    private var selectedMimeType: String? = null
    private var selectedFileName: String = ""

    private lateinit var tvFileName: TextView

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedMimeType = contentResolver.getType(it) ?: "application/octet-stream"
            selectedFileName = getFileName(it)
            selectedFileBytes = contentResolver.openInputStream(it)?.readBytes()
            tvFileName.text = "Đã đính kèm: \$selectedFileName"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etGeminiKey = findViewById<EditText>(R.id.etGeminiKey)
        val etGithubToken = findViewById<EditText>(R.id.etGithubToken)
        val etRepo = findViewById<EditText>(R.id.etRepo)
        val swProMode = findViewById<Switch>(R.id.swProMode)
        val btnPickFile = findViewById<Button>(R.id.btnPickFile)
        tvFileName = findViewById(R.id.tvFileName)
        val etIdea = findViewById<EditText>(R.id.etIdea)
        val btnBuild = findViewById<Button>(R.id.btnBuild)
        val btnDirectPush = findViewById<Button>(R.id.btnDirectPush)
        val tvLog = findViewById<TextView>(R.id.tvLog)

        btnPickFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        btnBuild.setOnClickListener {
            val apiKey = etGeminiKey.text.toString().trim()
            val ghToken = etGithubToken.text.toString().trim()
            val repo = etRepo.text.toString().trim()
            val idea = etIdea.text.toString().trim()
            val isPro = swProMode.isChecked

            if (apiKey.isEmpty() || ghToken.isEmpty() || repo.isEmpty()) {
                tvLog.text = "Vui lòng điền đủ API Key, GitHub Token và tên Repo!"
                return@setOnClickListener
            }

            if (idea.isEmpty() && selectedFileBytes == null) {
                tvLog.text = "Hãy nhập ý tưởng hoặc chọn ít nhất 1 tệp/ảnh mẫu!"
                return@setOnClickListener
            }

            val modeName = if (isPro) "PRO (Tư duy sâu)" else "FLASH (Tốc độ cao)"
            tvLog.text = "1. Đang gọi AI chế độ \$modeName để phân tích & viết code..."

            callGeminiWithFile(apiKey, idea, isPro) { generatedCode ->
                runOnUiThread { tvLog.text = "2. AI viết code xong! Đang đẩy lên GitHub để xuất APK..." }
                pushCodeToGithub(ghToken, repo, generatedCode) { statusMsg ->
                    runOnUiThread { tvLog.text = statusMsg }
                }
            }
        }

        btnDirectPush.setOnClickListener {
            val ghToken = etGithubToken.text.toString().trim()
            val repo = etRepo.text.toString().trim()

            if (ghToken.isEmpty() || repo.isEmpty() || selectedFileBytes == null) {
                tvLog.text = "Vui lòng nhập GitHub Token, tên Repo và chọn file code (.kt) của bạn!"
                return@setOnClickListener
            }

            tvLog.text = "Đang đẩy thẳng file code tạo tay của bạn lên GitHub..."
            val manualCode = String(selectedFileBytes!!, Charsets.UTF_8)
            pushCodeToGithub(ghToken, repo, manualCode) { statusMsg ->
                runOnUiThread { tvLog.text = statusMsg }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "tep_dinh_kem"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun callGeminiWithFile(apiKey: String, idea: String, isPro: Boolean, onResult: (String) -> Unit) {
        val modelId = if (isPro) "gemini-2.5-pro" else "gemini-2.5-flash"
        val host = "://googleapis.com"
        val url = "https://\$host/v1beta/models/modelId:generateContent?key=apiKey"
        
        val promptText = """
            Dựa vào hình ảnh/tệp đính kèm (nếu có) và yêu cầu sau: "\$idea".
            Hãy viết code file MainActivity.kt hoàn chỉnh (package com.bot.autoapk) cho ứng dụng Android.
            Nếu có ảnh giao diện mẫu, hãy tự tạo các thành phần giao diện bằng code Kotlin (LinearLayout, Button, TextView, EditText...) sao cho giống ảnh nhất.
            Chỉ trả về code Kotlin thuần túy, không giải thích, không bọc dấu ```.
        """.trimIndent()

        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", promptText))

        if (selectedFileBytes != null && selectedMimeType != null) {
            val base64Data = Base64.encodeToString(selectedFileBytes, Base64.NO_WRAP)
            val inlineData = JSONObject().apply {
                put("mime_type", selectedMimeType)
                put("data", base64Data)
            }
            partsArray.put(JSONObject().put("inline_data", inlineData))
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", partsArray)))
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("// Lỗi kết nối AI: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                try {
                    val text = JSONObject(body)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                    onResult(text.replace("```kotlin", "").replace("```", "").trim())
                } catch (e: Exception) {
                    onResult("// Lỗi đọc phản hồi từ AI: $body")
                }
            }
        })
    }

    private fun pushCodeToGithub(token: String, repo: String, code: String, onDone: (String) -> Unit) {
        val filePath = "app/src/main/java/com/bot/autoapk/MainActivity.kt"
        val ghApi = "://github.com"
        val apiUrl = "https://$ghApi/repos/$repo/contents/$filePath"

        val getReq = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(getReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onDone("Lỗi kết nối GitHub: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string() ?: "{}"
                val sha = try { JSONObject(resStr).getString("sha") } catch (e: Exception) { "" }

                val encodedContent = Base64.encodeToString(code.toByteArray(), Base64.NO_WRAP)
                val putJson = JSONObject().apply {
                    put("message", "AutoAPKBot update")
                    put("content", encodedContent)
                    if (sha.isNotEmpty()) put("sha", sha)
                }

                val putReq = Request.Builder()
                    .url(apiUrl)
                    .header("Authorization", "Bearer $token")
                    .put(putJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(putReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onDone("Lỗi đẩy code: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            onDone("3. THÀNH CÔNG! Hệ thống đang tự động xuất APK.\nTải file cài đặt sau 3 phút tại:\nhttps://github.com")
                        } else {
                            onDone("Lỗi GitHub (${response.code}): Kiểm tra lại Token hoặc tên Repo.")
                        }
                    }
                })
            }
        })
    }
}

