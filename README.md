# 🤖 AutoChat-AI (Trợ lý Canh & Tự động Trả lời Chat)

Ứng dụng Android gọn nhẹ chuyên dụng giúp **ngồi canh màn hình cuộc trò chuyện đang mở trên Messenger / Zalo** và **tự động trả lời bằng Gemini AI** theo đúng vai của bạn mà không cần bấm xác nhận từng tin.

## ✨ Tính năng chính
1. **Nút nổi (Floating Bubble) kéo thả tự do**:
   - `⚪ Canh Chat: TẮT` -> Chạm 1 lần để chuyển sang `🟢 Đang Canh`.
2. **Nhận diện tin nhắn thông minh bằng Accessibility**:
   - Tự động quét cây giao diện màn hình chat đang mở mỗi 1.8 giây.
   - Phân biệt bong bóng tin nhắn bên trái (đối phương) và bên phải (mình).
   - Nếu tin nhắn cuối cùng là của mình -> Đứng yên chờ, không tốn token gọi AI.
   - Nếu đối phương nhắn tin mới -> Chờ 2.2 giây (debounce) xem đối phương có gõ thêm câu tiếp theo không rồi gom lại gửi lên Gemini.
3. **Nhập vai (Persona) & Tự bấm Gửi 100%**:
   - Gọi trực tiếp Gemini API (`gemini-2.5-flash`) với ngữ cảnh cuộc hội thoại đang hiển thị trên màn hình.
   - Tự điền câu trả lời vào ô chat và tự động bấm nút **Gửi** ngay lập tức.
4. **Cơ chế An toàn**:
   - Có tùy chọn khóa theo tên người chat (ví dụ: chỉ trả lời khi tiêu đề cuộc trò chuyện là `Vợ yêu`).
   - Tự động dừng canh và cảnh báo nếu đối phương nhắn các từ khóa nhạy cảm (`chuyển khoản`, `mã OTP`, `số tài khoản`, `vay tiền`...).

## 🛠️ Cách Build ra APK
- **Qua GitHub Actions**: Mở tab **Actions** trên GitHub hoặc thêm file workflow dưới đây vào `.github/workflows/build.yml`.
- **Qua Android Studio / Gradle**:
  ```bash
  gradle assembleRelease
  ```
