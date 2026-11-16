# Hướng dẫn vận hành Lab 1 (LTM Connect) trên 2 máy tính qua mạng LAN

Tài liệu này mô tả thứ tự các bước cần thực hiện để chạy **Máy 1 (Server)** và **Máy 2 (Client)** trong cùng mạng LAN/Wi-Fi.

---

## Bước 0: Chuẩn bị chung trên cả 2 máy

1. **Kết nối cùng mạng LAN / Wi-Fi**: Đảm bảo Máy 1 và Máy 2 dùng chung một router/subnet (tránh Wi-Fi khách/công cộng có bật *AP Isolation* vì sẽ chặn giao tiếp giữa 2 máy và chặn UDP Broadcast/Multicast).
2. **Cài đặt công cụ cần thiết** trên cả 2 máy:
   - **JDK 21** (hoặc mới hơn tương thích `--release 21`).
   - **Maven 3.9+** (đã thêm vào `PATH` nếu chạy bằng `.bat`/CMD) hoặc **IntelliJ IDEA**.
3. **Chép thư mục `Lab1-Chat`** sang cả 2 máy.

---

## Bước 1: Thiết lập và chạy trên Máy 1 (Server PC)

Thực hiện **trước** trên Máy 1:

1. **Lấy địa chỉ IPv4 LAN của Máy 1**:
   - Mở `setup-and-run.bat` → chọn `[1] Thiet lap may SERVER`, hoặc mở PowerShell/CMD gõ `ipconfig`.
   - Ghi lại địa chỉ **IPv4 Address** của card Wi-Fi hoặc Ethernet đang dùng (ví dụ: `192.168.1.100`).
2. **Mở Windows Firewall trên Máy 1**:
   - Mục `[1]` trong `setup-and-run.bat` sẽ tự xin quyền Administrator để chạy `scripts\open-firewall.cmd` (hoặc bạn chuột phải `scripts\open-firewall.cmd` → **Run as administrator**).
   - Các port được mở inbound:
     - `TCP 5000`: Chat TCP & truyền tệp đính kèm.
     - `UDP 5001`: UDP Broadcast Relay.
     - `UDP 5002`: IP Multicast (`239.10.10.10`).
3. **(Tùy chọn) Thiết lập MySQL**:
   - Nếu không dùng MySQL, bỏ qua bước này — Server vẫn chạy bình thường ở chế độ lưu file trên đĩa (`data/chat-files`).
   - Nếu có MySQL 8: chạy `database/lab1_chat.sql` trong MySQL Workbench, đổi mật khẩu mẫu, rồi cấu hình 3 biến môi trường (`LTM_CHAT_DB_URL`, `LTM_CHAT_DB_USER`, `LTM_CHAT_DB_PASSWORD`) theo mẫu `config/server-db.env.example`.
4. **Kiểm tra file cấu hình Server**:
   - File `config/server-machine.properties` mặc định dùng `chat.bind.address=0.0.0.0`, `chat.tcp.port=5000`, `chat.udp.port=5001`. Không cần sửa trừ khi bị trùng port.
5. **Build và khởi động Server**:
   - Qua launcher: chọn `[2] Chay SERVER` trong `setup-and-run.bat` (sau khi đã build ở mục `[1]` hoặc `[6]`).
   - Hoặc qua IntelliJ IDEA: chạy class `vn.ltm.chat.server.ChatServerMain` (Working Directory là thư mục gốc `Lab1-Chat`).
   - **Dấu hiệu thành công**: Console in ra:
     ```text
     [CHAT] TCP listening on 0.0.0.0:5000; UDP relay on 0.0.0.0:5001
     [CHAT] UDP broadcast relay ready on port 5001
     ```

---

## Bước 2: Thiết lập và kiểm tra mạng trên Máy 2 (Client PC)

Thực hiện trên Máy 2 **sau khi Server ở Bước 1 đã chạy**:

1. **Mở Windows Firewall trên Máy 2 (khuyên dùng)**:
   - Chuột phải `scripts\open-firewall.cmd` → **Run as administrator** trên Máy 2 để đảm bảo Máy 2 nhận được gói tin **UDP Broadcast (`5001`)** và **IP Multicast (`5002`)**.
2. **Cấu hình IP Server cho Client**:
   - Mở `setup-and-run.bat` → chọn `[3] Cau hinh may CLIENT`, nhập đúng IPv4 LAN của Máy 1 (ví dụ `192.168.1.100`).
   - Hoặc sửa trực tiếp file `config/client.properties`:
     ```properties
     server.host=192.168.1.100
     chat.tcp.port=5000
     chat.udp.listen.port=5001
     chat.multicast.group=239.10.10.10
     chat.multicast.port=5002
     ```
   - > **Lưu ý**: `ChatClientApp` **chặn** `localhost` và `127.0.0.1`. Luôn dùng IPv4 LAN thật của Máy 1.
3. **Kiểm tra thông mạng TCP tới Máy 1**:
   - Chọn `[7] Kiem tra ket noi mang toi Server` trong `setup-and-run.bat`, hoặc chạy:
     ```powershell
     powershell -ExecutionPolicy Bypass -File scripts\check-network.ps1 -ServerIp 192.168.1.100
     ```
   - Đảm bảo `TcpTestSucceeded : True` trước khi mở ứng dụng.

---

## Bước 3: Chạy Client trên Máy 2 và kiểm thử

1. **Build trên Máy 2** (nếu chạy bằng `.bat`/Maven): chọn `[6] Build du an` trong `setup-and-run.bat`.
2. **Khởi chạy Client**:
   - Chạy 1 cửa sổ: chọn `[4] Chay 1 CLIENT` trong `setup-and-run.bat` (hoặc chạy mục tiêu Maven `javafx:run` / class `vn.ltm.chat.client.ChatClientApp` trong IntelliJ IDEA).
   - Hoặc demo nhiều client cùng lúc: chọn `[5] Chay 6 CLIENT cung luc` (mở 6 cửa sổ `Client01` → `Client06`).
   - *(Lưu ý khi nộp minh chứng 2 máy: không chạy Client trên Máy 1 để tách biệt rõ Server ở Máy 1 và các Client ở Máy 2).*
3. **Kết nối trong giao diện JavaFX**:
   - Kiểm tra ô **SERVER** đã hiện đúng IPv4 của Máy 1, **PORT** là `5000`, nhập **TÊN HIỂN THỊ** (2–32 ký tự, không trùng với client đang online) → bấm **Kết nối Server**.
4. **Trình tự test các tính năng mạng**:
   1. **Online list & Broadcast · TCP**: Gửi tin nhắn chung, kiểm tra các cửa sổ đều nhận được.
   2. **Private · TCP**: Chọn người nhận ở combobox, gửi tin riêng.
   3. **Group · TCP**: Nhập cùng tên nhóm (vd: `team-a`) ở các client muốn tham gia → bấm **Tham gia nhóm** → gửi tin nhắn nhóm.
   4. **Gửi tệp qua TCP (giới hạn 6 MiB)**: Chọn chế độ TCP (Broadcast/Private/Group) → bấm **📎 Đính kèm**, thử các file mẫu trong thư mục `samples/` (`sample-image.png`, `attachment-demo.txt`, `sample-audio.mp3`, `sample-video.mp4`).
   5. **Broadcast · UDP & Multicast · UDP**: Gửi text ở 2 chế độ UDP để kiểm tra gói datagram trên LAN (chỉ hỗ trợ text, không gửi tệp).
   6. **Ngắt kết nối**: Đóng 1 Client để xem danh sách online cập nhật; tắt Server để kiểm tra Client báo mất kết nối.

---

## Xử lý nhanh khi gặp lỗi kết nối

| Triệu chứng | Nguyên nhân thường gặp & Cách xử lý |
| :--- | :--- |
| Không kết nối được TCP (`Connection refused` / `Timed out`) | Kiểm tra lại Server ở Máy 1 đã chạy chưa, IP trong `config/client.properties` có đúng IPv4 của Máy 1 không, và Máy 1 đã chạy `scripts\open-firewall.cmd` với quyền Admin chưa. |
| App báo *"Hãy nhập IPv4 LAN thật của máy Server, không dùng localhost"* | Không được điền `localhost` hoặc `127.0.0.1` vào ô Server. |
| TCP chạy nhưng UDP Broadcast / Multicast không nhận được | Kiểm tra Firewall UDP `5001`/`5002` trên cả 2 máy; hoặc router/Wi-Fi đang bật chặn peer-to-peer / multicast (thử đổi sang mạng Wi-Fi khác hoặc phát hotspot). |
| Lỗi `'mvn' is not recognized` khi chạy `.bat` | Máy chưa cài Maven vào `PATH`. Cài Maven 3.9+ thêm vào `PATH`, hoặc dùng `.bat` cho các bước mạng/firewall/config rồi chạy `ChatServerMain` / `ChatClientApp` bằng IntelliJ IDEA. |
