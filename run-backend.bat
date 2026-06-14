@echo off
REM ============================================================
REM  Double-click file nay de CHAY backend.
REM  No goi PowerShell chay run-backend.ps1 (nam cung thu muc).
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-backend.ps1"
pause