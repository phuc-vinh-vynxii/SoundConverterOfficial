# Sound Converter - Công cụ xử lý âm thanh với AI

Sound Converter là ứng dụng Java giúp quản lý, phân tích và trộn các file âm thanh với sự hỗ trợ của trí tuệ nhân tạo. Ứng dụng sử dụng JavaFX cho giao diện người dùng, MySQL cho lưu trữ dữ liệu và Whisper CPP để phân tích giọng nói.

## Tính năng chính

- **Quản lý file âm thanh**: Nhập và quản lý các file MP3
- **Phân tích bằng AI**: Sử dụng Whisper CPP để phiên âm nội dung giọng nói
- **Trích xuất đoạn âm thanh**: Tạo các đoạn nhỏ từ file âm thanh lớn
- **Trộn âm thanh**: Kết hợp nhiều đoạn âm thanh thành một file mới
- **Lưu cấu hình trộn**: Lưu và tải các cấu hình trộn âm thanh
- **Hỗ trợ nhiều ngôn ngữ**: Phân tích được nhiều ngôn ngữ khác nhau, đặc biệt là tiếng Việt

## Yêu cầu hệ thống

- Java Development Kit (JDK) 17 trở lên
- MySQL 8.0 trở lên
- FFmpeg (được tích hợp sẵn trong ứng dụng)
- Whisper CPP (được tích hợp sẵn trong ứng dụng)
- Ít nhất 4GB RAM (khuyến nghị 8GB)
- Ít nhất 1GB dung lượng ổ đĩa trống

## Cấu trúc thư mục

```
SoundConverterOfficial/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── soundconverter/
│   │   │           ├── ai/             # Tích hợp Whisper CPP
│   │   │           ├── controllers/    # Các controller JavaFX
│   │   │           ├── dao/            # Data Access Objects
│   │   │           ├── models/         # Các model dữ liệu
│   │   │           ├── services/       # Các dịch vụ xử lý
│   │   │           └── Main.java       # Entry point
│   │   └── resources/
│   │       ├── fxml/                   # Các file giao diện FXML
│   │       ├── ffmpeg/                 # Thư viện FFmpeg
│   │       ├── whisper/                # Thư viện Whisper CPP
│   │       │   └── models/             # Các model AI
│   │       └── css/                    # Style sheets
├── output/                             # Thư mục lưu file tạm
├── pom.xml                             # Cấu hình Maven
└── README.md                           # Tài liệu hướng dẫn
```

## Hướng dẫn cài đặt

### 1. Cài đặt Java

1. Tải JDK 17 hoặc mới hơn từ [trang chủ Oracle](https://www.oracle.com/java/technologies/downloads/) hoặc sử dụng OpenJDK
2. Cài đặt JDK và thiết lập biến môi trường JAVA_HOME
3. Kiểm tra cài đặt bằng lệnh: `java -version`

### 2. Cài đặt MySQL

1. Tải MySQL từ [trang chủ MySQL](https://dev.mysql.com/downloads/mysql/)
2. Cài đặt MySQL Server
3. Cấu hình kết nối cơ sở dữ liệu:
   - Tạo file `config.properties` trong thư mục `src/main/resources/` (sao chép từ file `config.properties.example`)
   - Chỉnh sửa thông tin kết nối phù hợp với cài đặt MySQL của bạn:

```properties
# Database Configuration
db.url=jdbc:mysql://localhost:3306/soundconverter?createDatabaseIfNotExist=true
db.user=root
db.password=your_mysql_password
```

> **Lưu ý**: Ứng dụng sẽ tự động tạo cơ sở dữ liệu `soundconverter` và các bảng cần thiết khi khởi động lần đầu tiên. Bạn không cần phải tạo thủ công cơ sở dữ liệu hoặc các bảng.

### 3. Cài đặt ứng dụng từ mã nguồn

1. Clone repository về máy:

```bash
git clone https://github.com/phuc-vinh-vynxii/SoundConverterOfficial.git
cd SoundConverterOfficial
```

2. Biên dịch dự án bằng Maven:

```bash
mvn clean package
```

3. Chạy ứng dụng:

```bash
java -jar target/SoundConverterOfficial-1.0-SNAPSHOT.jar
```

### 4. Cài đặt từ file JAR

1. Tải file JAR từ trang Releases của dự án
2. Đảm bảo bạn đã cài đặt MySQL
3. Tạo thư mục để chứa ứng dụng:

```bash
mkdir SoundConverter
cd SoundConverter
```

4. Sao chép file JAR vào thư mục này
5. Tạo các thư mục cần thiết:

```bash
mkdir -p src/main/resources/ffmpeg
mkdir -p src/main/resources/whisper/models
mkdir output
```

6. Tạo file cấu hình kết nối cơ sở dữ liệu:

```bash
mkdir -p src/main/resources
touch src/main/resources/config.properties
```

7. Mở file `src/main/resources/config.properties` và thêm nội dung sau, chỉnh sửa thông tin kết nối phù hợp với cài đặt MySQL của bạn:

```properties
# Database Configuration
db.url=jdbc:mysql://localhost:3306/soundconverter?createDatabaseIfNotExist=true
db.user=root
db.password=your_mysql_password

# Application Settings
app.name=Sound Converter
app.version=1.0
```

> **Lưu ý**: Ứng dụng sẽ tự động tạo cơ sở dữ liệu `soundconverter` và các bảng cần thiết khi khởi động lần đầu tiên.

8. Tải FFmpeg từ [trang chủ FFmpeg](https://ffmpeg.org/download.html) và sao chép `ffmpeg.exe` và `ffprobe.exe` vào thư mục `src/main/resources/ffmpeg`
9. Tải Whisper CPP từ [GitHub](https://github.com/ggerganov/whisper.cpp/releases) và sao chép các file DLL và model vào thư mục `src/main/resources/whisper`
10. Chạy ứng dụng:

```bash
java -jar SoundConverterOfficial-1.0-SNAPSHOT.jar
```

Hoặc tạo file batch để chạy dễ dàng hơn (run.bat):

```batch
@echo off
java -jar SoundConverterOfficial-1.0-SNAPSHOT.jar
pause
```

## Hướng dẫn sử dụng

### 1. Nhập file âm thanh

1. Khởi động ứng dụng Sound Converter
2. Nhấn nút "Import" để chọn file âm thanh (hỗ trợ định dạng MP3, WAV, OGG, AAC)
3. File âm thanh sẽ xuất hiện trong danh sách bên trái

### 2. Phân tích âm thanh bằng AI

1. Chọn file âm thanh từ danh sách
2. Chọn ngôn ngữ từ danh sách thả xuống (mặc định là tiếng Việt)
3. Nhập độ dài đoạn phân tích (tính bằng giây, 0 = tự động)
4. Nhấn nút "Analyze" để bắt đầu phân tích
5. Đợi quá trình phân tích hoàn tất, các đoạn âm thanh sẽ hiển thị trong bảng

### 3. Chỉnh sửa văn bản phiên âm

1. Chọn một đoạn âm thanh trong bảng
2. Nhấn đúp chuột vào cột "Text" để chỉnh sửa
3. Nhập văn bản mới và nhấn Enter để lưu

### 4. Tạo file âm thanh mới từ các đoạn

1. Chọn các đoạn âm thanh muốn trộn
2. Nhấn nút "Add to Merge" để thêm vào danh sách trộn
3. Điều chỉnh thứ tự các đoạn bằng nút "Move Up" và "Move Down"
4. Nhập tên file kết quả vào ô "Output Filename"
5. Nhấn nút "Preview" để nghe thử kết quả
6. Nhấn nút "Merge" để tạo file âm thanh mới
7. Chọn vị trí lưu file kết quả

### 5. Lưu và tải cấu hình trộn

1. Tạo danh sách các đoạn âm thanh cần trộn
2. Nhấn nút "Save Merge" để lưu cấu hình
3. Nhập tên cho cấu hình và xác nhận
4. Để tải lại cấu hình đã lưu, nhấn nút "Load Merge" và chọn cấu hình muốn tải

### 6. Điều chỉnh model AI Whisper

Sound Converter cho phép bạn thay đổi model AI Whisper để phù hợp với nhu cầu sử dụng:

1. **Các model có sẵn trong ứng dụng**:

   - `ggml-tiny.en.bin`: Model nhỏ, chỉ hỗ trợ tiếng Anh, tốc độ nhanh, độ chính xác thấp hơn
   - `ggml-base-q8_0.bin`: Model đa ngôn ngữ cơ bản, hỗ trợ nhiều ngôn ngữ, cân bằng giữa tốc độ và độ chính xác

2. **Thay đổi model**:
   - Tải model mong muốn từ [trang GitHub của Whisper CPP](https://github.com/ggerganov/whisper.cpp/blob/master/models/README.md)
   - Đặt file model vào thư mục `src/main/resources/whisper/models`
   - Mở file `src/main/java/com/soundconverter/ai/WhisperService.java`
   - Tìm và chỉnh sửa các dòng sau:

```java
private static final String MODEL_PATH = "./models/ggml-base-q8_0.bin";
private static final String MODEL_PATH_EN = "./models/ggml-tiny.en.bin";
private static final String MODEL_PATH_MULTILINGUAL = "./models/ggml-base-q8_0.bin";
```

3. **Các model khuyến nghị**:

   - Cho tiếng Việt: `ggml-medium.bin` hoặc `ggml-large.bin` (độ chính xác cao nhưng yêu cầu nhiều tài nguyên)
   - Cho tiếng Anh: `ggml-base.en.bin` hoặc `ggml-small.en.bin` (cân bằng giữa tốc độ và độ chính xác)
   - Cho máy tính có cấu hình thấp: `ggml-tiny.bin` hoặc `ggml-tiny.en.bin` (tốc độ nhanh, yêu cầu ít tài nguyên)

4. **Lưu ý khi sử dụng model lớn**:
   - Model càng lớn càng chính xác nhưng cũng yêu cầu nhiều RAM và CPU hơn
   - Model `large` có thể yêu cầu 8GB RAM trở lên
   - Thời gian phân tích sẽ tăng lên đáng kể với model lớn

## Xử lý sự cố

### Lỗi kết nối cơ sở dữ liệu

- Kiểm tra MySQL đã được khởi động
- Kiểm tra thông tin kết nối trong file `src/main/resources/config.properties` đã chính xác
- Đảm bảo người dùng MySQL có đủ quyền để tạo cơ sở dữ liệu và bảng
- Nếu bạn đã có sẵn cơ sở dữ liệu `soundconverter`, hãy đảm bảo cấu trúc bảng tương thích với ứng dụng

### Lỗi "Invalid memory access" khi phân tích âm thanh

- Đảm bảo Whisper CPP đã được cài đặt đúng cách
- Kiểm tra file âm thanh không bị hỏng
- Thử chọn ngôn ngữ cụ thể thay vì để tự động phát hiện
- Kiểm tra model AI đã được đặt đúng vị trí trong thư mục `src/main/resources/whisper/models`

### Lỗi khi trộn âm thanh

- Kiểm tra FFmpeg đã được cài đặt đúng cách
- Đảm bảo đường dẫn lưu file không chứa ký tự đặc biệt
- Kiểm tra đủ quyền ghi vào thư mục đích
- Xem log lỗi trong console để biết thêm chi tiết

## Các phím tắt và thao tác nhanh

- **Ctrl+I**: Nhập file âm thanh
- **Ctrl+A**: Phân tích file âm thanh đã chọn
- **Ctrl+E**: Chỉnh sửa đoạn âm thanh đã chọn
- **Ctrl+M**: Trộn các đoạn âm thanh đã thêm vào danh sách
- **Ctrl+S**: Lưu cấu hình trộn hiện tại
- **Ctrl+L**: Tải cấu hình trộn đã lưu
- **Ctrl+P**: Xem trước kết quả trộn âm thanh

## Thông tin thêm

Sound Converter sử dụng các công nghệ sau:

- **JavaFX**: Xây dựng giao diện người dùng
- **MySQL**: Lưu trữ thông tin về file âm thanh và đoạn phân tích
- **Whisper CPP**: Mô hình AI phiên âm giọng nói
- **FFmpeg**: Xử lý và trộn file âm thanh

## Liên hệ và hỗ trợ

Nếu bạn gặp vấn đề hoặc có câu hỏi, vui lòng tạo issue trên GitHub hoặc liên hệ qua email: nguyenvinh19525@gmail.com

---

© 2025 Sound Converter Project. Phát triển bởi [PHÚC VĨNH]
