package com.autochat.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoChatAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoChatAccessibilityService? = null
            private set

        @Volatile
        var isWatching: Boolean = false
            private set

        @Volatile
        var statusLog: String = "Chưa bật canh chat"
            private set

        private val SUPPORTED_CHAT_PACKAGES = setOf(
            "com.facebook.orca",          // Messenger
            "com.facebook.mlite",         // Messenger Lite
            "com.zing.zalo",              // Zalo
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging",     // Samsung Messages
            "org.telegram.messenger",     // Telegram
            "com.whatsapp"                // WhatsApp
        )

        private val IGNORED_UI_TEXTS = setOf(
            "Aa", "Nhắn tin", "Nhập tin nhắn", "Tin nhắn", "Message", "Write a message...",
            "Gửi", "Send", "Thích", "Like", "Đã xem", "Đã gửi", "Đã nhận", "Đang hoạt động",
            "Active now", "Hôm nay", "Hôm qua"
        )

        private val SENSITIVE_KEYWORDS = listOf(
            "chuyển khoản", "mã otp", "mật khẩu", "số tài khoản", "vay tiền", "mượn tiền", "stk"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchJob: Job? = null

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var bubbleDotView: TextView? = null
    private var bubbleTitleView: TextView? = null
    private var bubbleSubView: TextView? = null

    private var lockedChatHeader: String = ""
    private var lastRepliedPartnerMsgSignature: String = ""
    private var lastSentByAiText: String = ""
    private var isGeneratingOrSending: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateStatus("Trợ năng đã sẵn sàng")
        showFloatingBubbleIfPermitted()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Vòng lặp chủ động kiểm tra mỗi 1.8 giây khi bật Canh Chat nên không cần xử lý nặng ở đây
    }

    override fun onInterrupt() {
        stopWatching("Trợ năng bị ngắt")
    }

    override fun onDestroy() {
        stopWatching("Dịch vụ dừng")
        removeFloatingBubble()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    fun showFloatingBubbleIfPermitted() {
        mainHandler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                return@post
            }
            if (floatingView != null) {
                refreshBubbleUI()
                return@post
            }

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24).toFloat()
                    setColor(Color.parseColor("#E61E293B"))
                    setStroke(dp(1), Color.parseColor("#64748B"))
                }
                elevation = dp(8).toFloat()
            }

            val dot = TextView(this).apply {
                text = "⚪"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, dp(6), 0)
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val title = TextView(this).apply {
                text = "Canh Chat: TẮT"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                paint.isFakeBoldText = true
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val sub = TextView(this).apply {
                text = "Chạm để Bật/Tắt"
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            textCol.addView(title)
            textCol.addView(sub)
            container.addView(dot)
            container.addView(textCol)

            bubbleDotView = dot
            bubbleTitleView = title
            bubbleSubView = sub

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(16)
                y = dp(140)
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isClick = false

            container.setOnTouchListener { _, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = motionEvent.rawX
                        initialTouchY = motionEvent.rawY
                        isClick = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (motionEvent.rawX - initialTouchX).toInt()
                        val dy = (motionEvent.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) {
                            isClick = false
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(container, params)
                        } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleWatching()
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager?.addView(container, params)
                floatingView = container
                refreshBubbleUI()
            } catch (e: Exception) {
                updateStatus("Lỗi hiện nút nổi: ${e.message}")
            }
        }
    }

    fun removeFloatingBubble() {
        mainHandler.post {
            floatingView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (_: Exception) {}
            }
            floatingView = null
        }
    }

    fun toggleWatching() {
        if (isWatching) {
            stopWatching("Đã tắt canh chat")
        } else {
            startWatching()
        }
    }

    fun startWatching() {
        val apiKey = PrefsManager.getApiKey(this)
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Vui lòng mở AutoChat-AI nhập Gemini API Key trước!", Toast.LENGTH_LONG).show()
            updateStatus("Thiếu Gemini API Key")
            return
        }

        isWatching = true
        lockedChatHeader = PrefsManager.getTargetName(this)
        isGeneratingOrSending = false

        // Đánh dấu tin nhắn hiện tại trên màn hình để không trả lời lặp lại tin cũ lúc vừa bấm Bật
        val snapshot = captureScreenConversation()
        if (snapshot != null) {
            if (lockedChatHeader.isBlank() && snapshot.headerTitle.isNotBlank()) {
                lockedChatHeader = snapshot.headerTitle
            }
            val lastPartner = snapshot.turns.lastOrNull { it.isFromPartner }
            if (lastPartner != null) {
                // Nếu tin cuối cùng của cuộc hội thoại đang là của đối phương, sẽ trả lời luôn!
                // Nếu tin cuối cùng đang là của mình thì đánh dấu đã trả lời tin đối phương trước đó.
                val veryLast = snapshot.turns.lastOrNull()
                if (veryLast != null && !veryLast.isFromPartner) {
                    lastRepliedPartnerMsgSignature = lastPartner.text.trim()
                } else {
                    lastRepliedPartnerMsgSignature = ""
                }
            }
        }

        updateStatus("Đang canh: ${lockedChatHeader.ifBlank { "Mọi cuộc chat mở" }}")
        refreshBubbleUI()

        watchJob?.cancel()
        watchJob = serviceScope.launch {
            while (isActive && isWatching) {
                try {
                    if (!isGeneratingOrSending) {
                        checkAndReplyIfNeeded()
                    }
                } catch (e: Exception) {
                    updateStatus("Lỗi canh: ${e.message?.take(40)}")
                }
                delay(1800L)
            }
        }
    }

    fun stopWatching(reason: String = "Đã dừng") {
        isWatching = false
        isGeneratingOrSending = false
        watchJob?.cancel()
        watchJob = null
        updateStatus(reason)
        refreshBubbleUI()
    }

    private suspend fun checkAndReplyIfNeeded() {
        val snapshot = captureScreenConversation() ?: return

        // Kiểm tra xem có đúng ứng dụng chat không
        if (snapshot.packageName !in SUPPORTED_CHAT_PACKAGES) {
            updateBubbleSub("Đang chờ mở Messenger/Zalo...")
            return
        }

        // Nếu người dùng khóa tên người chat cụ thể, kiểm tra tiêu đề
        val targetFilter = PrefsManager.getTargetName(this)
        if (targetFilter.isNotBlank() && snapshot.headerTitle.isNotBlank()) {
            if (!snapshot.headerTitle.contains(targetFilter, ignoreCase = true)) {
                updateBubbleSub("Khác tên ($targetFilter) -> Tạm dừng")
                return
            }
        }

        if (lockedChatHeader.isBlank() && snapshot.headerTitle.isNotBlank()) {
            lockedChatHeader = snapshot.headerTitle
        }

        val turns = snapshot.turns
        if (turns.isEmpty()) {
            updateBubbleSub("Đang canh ${lockedChatHeader.ifBlank { "chat" }}...")
            return
        }

        val lastTurn = turns.last()
        // Nếu tin nhắn cuối cùng bên dưới cùng là của MÌNH (bên phải) -> Đứng yên chờ đối phương nhắn
        if (!lastTurn.isFromPartner) {
            updateBubbleSub("Đang chờ đối phương nhắn...")
            return
        }

        // Gom cụm tin nhắn liên tiếp mới nhất của đối phương ở cuối màn hình
        val consecutivePartnerMsgs = mutableListOf<String>()
        for (i in turns.indices.reversed()) {
            val t = turns[i]
            if (t.isFromPartner) {
                consecutivePartnerMsgs.add(0, t.text.trim())
            } else {
                break
            }
        }
        val combinedLatestPartnerText = consecutivePartnerMsgs.joinToString("\n").trim()
        if (combinedLatestPartnerText.isBlank()) return

        // Nếu cụm tin này giống hệt cụm vừa trả lời hoặc giống câu AI vừa gửi thì bỏ qua
        if (combinedLatestPartnerText == lastRepliedPartnerMsgSignature ||
            combinedLatestPartnerText == lastSentByAiText
        ) {
            updateBubbleSub("Đang chờ tin mới...")
            return
        }

        // Kiểm tra từ khóa nhạy cảm (tiền bạc, chuyển khoản, OTP)
        if (PrefsManager.isSafeGuardEnabled(this)) {
            val lower = combinedLatestPartnerText.lowercase()
            val matchedSensitive = SENSITIVE_KEYWORDS.firstOrNull { lower.contains(it) }
            if (matchedSensitive != null) {
                stopWatching("⚠️ Dừng: Phát hiện '$matchedSensitive'")
                Toast.makeText(
                    this,
                    "AutoChat-AI tạm dừng vì đối phương nhắn nội dung nhạy cảm: '$matchedSensitive'",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // Debounce: chờ 2.2 giây xem đối phương còn đang gõ câu tiếp theo không
        updateBubbleSub("Phát hiện tin mới, chờ 2s...")
        delay(PrefsManager.getDebounceMs(this))
        if (!isWatching) return

        // Đọc lại màn hình sau 2.2s để lấy đủ câu nếu đối phương vừa gửi thêm dòng thứ 2
        val freshSnapshot = captureScreenConversation() ?: return
        val freshTurns = freshSnapshot.turns
        if (freshTurns.isEmpty() || !freshTurns.last().isFromPartner) return

        val freshPartnerMsgs = mutableListOf<String>()
        for (i in freshTurns.indices.reversed()) {
            val t = freshTurns[i]
            if (t.isFromPartner) {
                freshPartnerMsgs.add(0, t.text.trim())
            } else {
                break
            }
        }
        val finalPartnerText = freshPartnerMsgs.joinToString("\n").trim()
        if (finalPartnerText.isBlank() || finalPartnerText == lastRepliedPartnerMsgSignature) return

        // Bắt đầu gọi Gemini sinh câu trả lời
        isGeneratingOrSending = true
        try {
            updateBubbleSub("🤖 Gemini đang suy nghĩ...")
            val apiKey = PrefsManager.getApiKey(this)
            val model = PrefsManager.getModel(this)
            val persona = PrefsManager.getPersona(this)

            val result = GeminiClient.generateReply(
                apiKey = apiKey,
                model = model,
                personaPrompt = persona,
                partnerName = freshSnapshot.headerTitle.ifBlank { lockedChatHeader },
                conversation = freshTurns,
                latestPartnerMessage = finalPartnerText
            )

            result.onSuccess { replyText ->
                if (!isWatching) return@onSuccess
                updateBubbleSub("✍️ Đang gửi: ${replyText.take(18)}...")
                val sentOk = fillAndClickSend(replyText)
                if (sentOk) {
                    lastRepliedPartnerMsgSignature = finalPartnerText
                    lastSentByAiText = replyText.trim()
                    updateStatus("Đã trả lời: ${replyText.take(28)}")
                    updateBubbleSub("✅ Đã gửi · Đang canh tiếp")
                    delay(1500L)
                } else {
                    updateBubbleSub("⚠️ Không tìm thấy ô nhập/nút Gửi")
                }
            }.onFailure { err ->
                updateStatus("Lỗi AI: ${err.message?.take(45)}")
                updateBubbleSub("❌ Lỗi gọi Gemini API")
            }
        } finally {
            isGeneratingOrSending = false
        }
    }

    private data class ScreenConversationSnapshot(
        val packageName: String,
        val headerTitle: String,
        val turns: List<ChatTurn>
    )

    private fun captureScreenConversation(): ScreenConversationSnapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: ""

        val metrics: DisplayMetrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val topHeaderBoundary = (screenHeight * 0.16f).toInt()
        val bottomInputBoundary = (screenHeight * 0.84f).toInt()

        val headerCandidates = mutableListOf<Pair<Int, String>>()
        val turns = mutableListOf<ChatTurn>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) return

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val rawText = node.text?.toString()?.trim() ?: ""
            val className = node.className?.toString() ?: ""

            // Bỏ qua chính ô EditText nhập tin nhắn
            val isEditText = className.contains("EditText", ignoreCase = true) || node.isEditable

            if (!isEditText && rawText.isNotBlank() && rawText !in IGNORED_UI_TEXTS) {
                // Nếu nằm ở vùng Header phía trên (16% đầu màn hình)
                if (rect.top in 40..topHeaderBoundary && rawText.length in 2..45) {
                    if (!rawText.matches(Regex("^\\d{1,2}:\\d{2}.*"))) {
                        headerCandidates.add(rect.top to rawText)
                    }
                }
                // Nếu nằm trong vùng danh sách tin nhắn (giữa màn hình)
                else if (rect.top in (topHeaderBoundary + 1)..bottomInputBoundary &&
                    rect.height() > dp(12) &&
                    rawText.length >= 1
                ) {
                    // Loại bỏ các dòng timestamp ngắn như "12:30", "T2", "Đã xem"
                    val isTimestamp = rawText.matches(Regex("^\\d{1,2}:\\d{2}(\\s*(AM|PM|SA|CH))?$", RegexOption.IGNORE_CASE))
                    if (!isTimestamp) {
                        val centerX = rect.centerX()
                        // Phân biệt tin nhắn của đối phương (bên trái) và của mình (bên phải)
                        val isFromPartner = when {
                            rect.left < screenWidth * 0.28f && rect.right < screenWidth * 0.82f -> true
                            rect.right > screenWidth * 0.72f && rect.left > screenWidth * 0.18f -> false
                            else -> centerX < (screenWidth / 2)
                        }
                        turns.add(ChatTurn(isFromPartner = isFromPartner, text = rawText, topY = rect.top))
                    }
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        try {
            traverse(root)
        } catch (_: Exception) {
            return null
        }

        val sortedTurns = turns
            .sortedBy { it.topY }
            .distinctBy { "${it.isFromPartner}_${it.topY / 12}_${it.text}" }

        val headerTitle = headerCandidates.minByOrNull { it.first }?.second ?: ""

        return ScreenConversationSnapshot(
            packageName = pkg,
            headerTitle = headerTitle,
            turns = sortedTurns
        )
    }

    private suspend fun fillAndClickSend(replyText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels

        var inputNode: AccessibilityNodeInfo? = null

        fun findInput(node: AccessibilityNodeInfo?) {
            if (node == null || inputNode != null) return
            val cls = node.className?.toString() ?: ""
            if (node.isEditable || cls.contains("EditText", ignoreCase = true)) {
                inputNode = node
                return
            }
            for (i in 0 until node.childCount) {
                findInput(node.getChild(i))
            }
        }

        findInput(root)

        val targetInput = inputNode ?: return false

        // Điền nội dung trả lời vào ô chat
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
        }
        targetInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val setOk = targetInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!setOk) return false

        // Chờ 350ms để ứng dụng Messenger/Zalo chuyển icon Like/Mic thành nút Gửi
        delay(350L)

        val refreshedRoot = rootInActiveWindow ?: root
        var sendNode: AccessibilityNodeInfo? = null

        val sendKeywords = listOf("gửi", "send")

        fun findSendButton(node: AccessibilityNodeInfo?) {
            if (node == null || sendNode != null) return
            if (!node.isVisibleToUser) return

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val txt = node.text?.toString()?.trim()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val matchesSendLabel = sendKeywords.any { k ->
                desc == k || desc.contains(k) || txt == k || viewId.contains("send")
            }

            if (matchesSendLabel && rect.top > screenHeight * 0.55f && node.isClickable) {
                sendNode = node
                return
            }

            for (i in 0 until node.childCount) {
                findSendButton(node.getChild(i))
            }
        }

        findSendButton(refreshedRoot)

        if (sendNode != null) {
            val clicked = sendNode!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        // Fallback: Nếu nút Gửi không nhận ACTION_CLICK trực tiếp, tìm nút nằm bên phải ô EditText và chạm tọa độ
        val inputRect = Rect()
        targetInput.getBoundsInScreen(inputRect)
        val sendTapX = (resources.displayMetrics.widthPixels * 0.93f)
        val sendTapY = inputRect.centerY().toFloat()
        return tapCoordinate(sendTapX, sendTapY)
    }

    private fun tapCoordinate(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun refreshBubbleUI() {
        mainHandler.post {
            val container = floatingView as? LinearLayout ?: return@post
            val bg = container.background as? GradientDrawable
            if (isWatching) {
                bubbleDotView?.text = "🟢"
                bubbleTitleView?.text = "Đang Canh: ${lockedChatHeader.ifBlank { "Chat" }}"
                bubbleSubView?.text = "Chạm để TẮT"
                bg?.setColor(Color.parseColor("#E6064E3B"))
                bg?.setStroke(dp(1), Color.parseColor("#10B981"))
            } else {
                bubbleDotView?.text = "⚪"
                bubbleTitleView?.text = "Canh Chat: TẮT"
                bubbleSubView?.text = "Chạm để BẬT tự trả lời"
                bg?.setColor(Color.parseColor("#E61E293B"))
                bg?.setStroke(dp(1), Color.parseColor("#64748B"))
            }
        }
    }

    private fun updateBubbleSub(text: String) {
        mainHandler.post {
            bubbleSubView?.text = text
        }
    }

    private fun updateStatus(msg: String) {
        statusLog = msg
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
