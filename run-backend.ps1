# ============================================================
#  Khởi động Spring Boot + trỏ tới AI server trên Colab.
#  MỖI LẦN COLAB ĐỔI URL: chỉ sửa 1 dòng $ColabUrl bên dưới rồi chạy lại.
#  Chạy:  .\run-backend.ps1
# ============================================================

# >>> SỬA Ở ĐÂY mỗi khi ngrok đổi URL <<<
$ColabUrl = "https://poison-rosy-handball.ngrok-free.dev"
$ApiKey   = "123"

# ---- Không cần sửa phần dưới ----

# Luôn chạy từ thư mục chứa script (để .\.env và .\mvnw.cmd đúng dù gọi từ đâu).
Set-Location $PSScriptRoot

# Kill mọi Spring Boot cũ còn chạy (tránh process cũ giữ cấu hình localhost)
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match "quiz-backend|spring-boot:run" } |
    ForEach-Object { Write-Host "Kill process cũ PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Write-Host "AI server  : $ColabUrl" -ForegroundColor Cyan
Write-Host "API key    : $ApiKey" -ForegroundColor Cyan
Write-Host "Đang khởi động Spring Boot..." -ForegroundColor Green

$env:JAVA_HOME = "C:\Program Files\OpenLogic\jre-21.0.6.7-hotspot"

# Spring Boot KHÔNG tự đọc file .env — nạp .env vào biến môi trường tiến trình ở đây
# để ${GEMINI_API_KEY}, ${DB_URL}, ... trong application.properties có hiệu lực.
if (Test-Path ".\.env") {
    Get-Content ".\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx   = $line.IndexOf("=")
            $name  = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
    $keySet = if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) { "RỖNG" } else { "OK ($($env:GEMINI_API_KEY.Length) ký tự)" }
    Write-Host "Đã nạp .env  -> GEMINI_API_KEY: $keySet" -ForegroundColor Green
} else {
    Write-Host "CẢNH BÁO: không thấy .env — GEMINI_API_KEY sẽ chưa được cấu hình!" -ForegroundColor Yellow
}

# Truyền URL + key TRỰC TIẾP vào Spring Boot qua run.arguments (chắc chắn, không qua env var).
$runArgs = "--app.generator.base-url=$ColabUrl --app.generator.api-key=$ApiKey"
& ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.arguments=$runArgs"
