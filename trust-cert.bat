@echo off
chcp 65001 >nul 2>&1
setlocal

set "HOST_PORT=%~1"
if "%HOST_PORT%"=="" set /p "HOST_PORT=Input host:port (e.g. 192.168.1.100:5000): "

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0trust-cert.ps1" -HostPort "%HOST_PORT%"
pause
