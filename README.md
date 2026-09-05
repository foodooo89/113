# Device Fingerprint — hướng dẫn build APK

## Cách 1 — Build tự động trên GitHub (không cần cài gì trên máy)

1. Tạo 1 repo mới trên GitHub (public hoặc private đều được), đặt tên tuỳ ý.
2. Giải nén file zip này ra, rồi push toàn bộ nội dung thư mục `DeviceFingerprint/` lên repo đó (đơn giản nhất: dùng web GitHub → "Add file" → "Upload files" → kéo thả hết vào, hoặc dùng git):
   ```
   cd DeviceFingerprint
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/<user>/<repo>.git
   git push -u origin main
   ```
3. Vào tab **Actions** trên GitHub repo — workflow "Build APK" sẽ tự chạy (mất khoảng 3-5 phút).
4. Khi chạy xong (dấu tích xanh), bấm vào lần chạy đó → mục **Artifacts** ở cuối trang → tải file `DeviceFingerprint-debug-apk.zip` về, giải nén ra là có `app-debug.apk`.
5. Copy APK vào điện thoại, bật "Cài từ nguồn không xác định" rồi cài như bình thường.

Muốn build lại thủ công bất cứ lúc nào (không cần push code mới): vào tab Actions → chọn workflow "Build APK" → **Run workflow**.

## Cách 2 — Build bằng Android Studio (nếu có máy đủ mạnh)

1. Mở Android Studio → **Open** → chọn thư mục `DeviceFingerprint` (thư mục giải nén ra).
2. Đợi Gradle sync xong (lần đầu cần internet để tải Android Gradle Plugin + thư viện AndroidX).
3. Build → **Build APK(s)**, hoặc chạy trực tiếp lên máy/emulator bằng nút Run.
4. APK debug nằm ở: `app/build/outputs/apk/debug/app-debug.apk`

## Ghi chú
- App xin quyền `READ_PHONE_STATE` để đọc IMEI/thông tin SIM (Android 10+ sẽ không trả IMEI thật vì Google chặn, chỉ trả null hoặc chuỗi rỗng — đây là giới hạn của hệ thống, không phải lỗi app).
- `Build.SERIAL`/`getSerial()` cũng bị Android 8+ giới hạn, cần quyền + có thể vẫn trả `UNKNOWN` tùy ROM.
- Phần `uname -a` và `/proc/cpuinfo` đọc trực tiếp, không cần root — nhưng một số ROM có thể chặn/rút gọn thông tin hiển thị.
- Đã thêm: dump `getprop` đầy đủ, danh sách hardware/software features, camera (id + lens facing + sensor size + focal lengths), Bluetooth adapter, battery (capacity/tech/nhiệt độ/điện áp), network interfaces (MAC/IP từng interface), `/proc/mounts`, tần số CPU max/min, Java system properties. Tất cả đọc được không cần root.
- Khi cài trên máy thật, app sẽ xin thêm quyền CAMERA và BLUETOOTH_CONNECT (Android 12+) để đọc đủ thông tin 2 phần đó — nếu từ chối app vẫn chạy, phần tương ứng sẽ ghi lỗi/permission denied trong report chứ không crash.
- Muốn đối chiếu trực tiếp kết quả spoof của peetools thì so `getprop` (đặc biệt các dòng `ro.build.fingerprint`, `ro.product.*`, `ro.serialno`) trong report này với `profiles.txt` của peetools.
