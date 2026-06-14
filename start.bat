@echo off
REM ============================================================
REM Quiz Backend - Startup Script
REM Loads .env file and starts Spring Boot application
REM ============================================================

echo [Quiz Backend] Loading environment variables from .env ...

REM Load each line from .env as an environment variable
for /f "usebackq tokens=* delims=" %%a in ("%~dp0.env") do (
    REM Skip empty lines and comments (lines starting with #)
    echo %%a | findstr /r "^#" >nul 2>&1
    if errorlevel 1 (
        echo %%a | findstr /r "^$" >nul 2>&1
        if errorlevel 1 (
            set "%%a"
        )
    )
)

echo [Quiz Backend] Environment loaded. Starting application...
echo.
echo    Database : %DB_URL%
echo    Gemini   : %GEMINI_MODEL%
echo    Generator: %QUESTION_GENERATOR_URL%
echo.

REM Start Spring Boot
call "%~dp0mvnw.cmd" spring-boot:run
