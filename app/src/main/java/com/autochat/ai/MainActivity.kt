package com.autochat.ai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusBannerText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var targetNameInput: EditText
    private lateinit var personaInput: EditText
    private lateinit var safeGuardCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        // Header
        val title = TextView(this).apply {
            text = "🤖 AutoChat-AI"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            paint.isFakeBoldText = true
        }
        val subtitle = TextView(this).apply {
            text = "Nút nổi Canh màn hình & Tự động trả lời Messenger / Zalo bằng Gemini AI (không cần xác nhận từng tin)"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(0, dp(4), 0, dp(16))
        }
        root.addView(title)
        root.addView(subtitle)

        // Status Card
        statusBannerText = TextView(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = cardBg("#1E293B", "#334155")
        }
        root.addView(statusBannerText)

        // Permission Buttons Row
        val permRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(10))
        }

        val btnOverlay = createActionButton("1. Cấp quyền Hiển thị Nút nổi (Overlay)", "#2563EB") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        val btnAccessibility = createActionButton("2. Bật Trợ năng AutoChat-AI (Accessibility)", "#059669") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Hãy tìm 'AutoChat-AI' trong danh sách dịch vụ đã cài đặt và BẬT lên",
                Toast.LENGTH_LONG
            ).show()
        }

        permRow.addView(btnOverlay)
        permRow.addView(spaceView(dp(8)))
        permRow.addView(btnAccessibility)
        root.addView(permRow)

        // Configuration Form
        root.addView(sectionLabel("🔑 Gemini API Key (Lưu trên máy của bạn)"))
        apiKeyInput = createEditText("Dán mã AIzaSy... từ Google AI Studio", singleLine = true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(PrefsManager.getApiKey(this@MainActivity))
        }
        root.addView(apiKeyInput)

        root.addView(sectionLabel("⚡ Model Gemini"))
        modelInput = createEditText("gemini-2.5-flash", singleLine = true).apply {
            setText(PrefsManager.getModel(this@MainActivity))
        }
        root.addView(modelInput)

        root.addView(sectionLabel("❤️ Tên người muốn canh chat (Để trống = canh mọi cuộc chat đang mở)"))
        targetNameInput = createEditText("Ví dụ: Vợ yêu (hoặc để trống)", singleLine = true).apply {
            setText(PrefsManager.getTargetName(this@MainActivity))
        }
        root.addView(targetNameInput)

        root.addView(sectionLabel("🎭 Vai trò & Giọng điệu của bạn (Persona Prompt)"))
        personaInput = createEditText("Nhập cách xưng hô, giọng điệu...", singleLine = false).apply {
            minLines = 4
            setText(PrefsManager.getPersona(this@MainActivity))
        }
        root.addView(personaInput)

        safeGuardCheck = CheckBox(this).apply {
            text = "Tự động dừng khi đối phương hỏi tiền bạc / chuyển khoản / OTP"
            setTextColor(Color.parseColor("#CBD5E1"))
            isChecked = PrefsManager.isSafeGuardEnabled(this@MainActivity)
            setPadding(dp(4), dp(12), dp(4), dp(12))
        }
        root.addView(safeGuardCheck)

        // Save & Show Bubble Button
        val btnSave = createActionButton("💾 Lưu cài đặt & Hiện nút nổi Canh Chat", "#7C3AED") {
            saveSettings()
        }
        root.addView(spaceView(dp(8)))
        root.addView(btnSave)

        // Guide Card
        val guideText = TextView(this).apply {
            text = """
                📌 HƯỚNG DẪN SỬ DỤNG NHANH:
                1. Bấm nút (1) cấp quyền Nút nổi và nút (2) bật Trợ năng AutoChat-AI.
                2. Dán Gemini API Key và chỉnh lại cách xưng hô (ví dụ: xưng anh - gọi vợ/em), bấm Lưu.
                3. Một nút nổi [⚪ Canh Chat: TẮT] sẽ xuất hiện trên màn hình.
                4. Mở cuộc trò chuyện với vợ trên Messenger hoặc Zalo -> Chạm vào nút nổi để chuyển sang [🟢 Đang Canh].
                5. Để màn hình mở đó: Hễ đối phương nhắn tin mới (bên trái), sau 2 giây AI sẽ tự suy nghĩ và tự điền + bấm Gửi luôn! Chạm lại nút nổi bất kỳ lúc nào để Tắt.
            """.trimIndent()
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = cardBg("#1E293B", "#334155")
        }
        root.addView(spaceView(dp(16)))
        root.addView(guideText)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        refreshStatusBanner()
        AutoChatAccessibilityService.instance?.showFloatingBubbleIfPermitted()
    }

    private fun saveSettings() {
        PrefsManager.setApiKey(this, apiKeyInput.text.toString())
        PrefsManager.setModel(this, modelInput.text.toString())
        PrefsManager.setTargetName(this, targetNameInput.text.toString())
        PrefsManager.setPersona(this, personaInput.text.toString())
        PrefsManager.setSafeGuardEnabled(this, safeGuardCheck.isChecked)

        AutoChatAccessibilityService.instance?.showFloatingBubbleIfPermitted()
        refreshStatusBanner()
        Toast.makeText(this, "Đã lưu cài đặt AutoChat-AI!", Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatusBanner() {
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val accOk = isAccessibilityEnabled(this)
        val hasKey = PrefsManager.getApiKey(this).isNotBlank()

        statusBannerText.text = buildString {
            appendLine("📊 TRẠNG THÁI HỆ THỐNG:")
            appendLine("• Quyền Nút nổi (Overlay): ${if (overlayOk) "✅ Đã cấp" else "❌ Chưa cấp"}")
            appendLine("• Dịch vụ Trợ năng: ${if (accOk) "✅ Đang chạy" else "❌ Chưa bật"}")
            appendLine("• Gemini API Key: ${if (hasKey) "✅ Đã nhập" else "⚠️ Chưa nhập"}")
            append("• Trạng thái Canh: ${AutoChatAccessibilityService.statusLog}")
        }
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expectedComponent = "$packageName/${AutoChatAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedComponent, ignoreCase = true) ||
            enabledServices.contains(packageName, ignoreCase = true)
    }

    private fun sectionLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            paint.isFakeBoldText = true
            setPadding(0, dp(14), 0, dp(6))
        }

    private fun createEditText(hintText: String, singleLine: Boolean): EditText =
        EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isSingleLine = singleLine
            gravity = if (singleLine) Gravity.CENTER_VERTICAL else (Gravity.TOP or Gravity.START)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = cardBg("#1E293B", "#475569")
        }

    private fun createActionButton(title: String, hexColor: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor(hexColor))
            }
            setOnClickListener { onClick() }
        }

    private fun cardBg(fillHex: String, strokeHex: String): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }

    private fun spaceView(heightPx: Int): ViewGroup =
        LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
