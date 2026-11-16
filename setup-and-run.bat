@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title LTM Connect - Lab 1 Setup ^& Launcher

:MENU
cls
echo ============================================================
echo        LTM CONNECT (LAB 1 - CHAT TCP / UDP / MULTICAST)
echo ============================================================
echo.
echo   [1] Thiet lap may SERVER (Xem IP LAN + Mo Firewall + Build)
echo   [2] Chay SERVER (TCP 5000, UDP 5001)
echo   [3] Cau hinh may CLIENT (Cap nhat server.host trong client.properties)
echo   [4] Chay 1 CLIENT (Nhap username)
echo   [5] Chay 6 CLIENT cung luc (Client01 -> Client06)
echo   [6] Build du an (mvn -U clean package)
echo   [7] Kiem tra ket noi mang toi Server (TCP port 5000)
echo   [0] Thoat
echo.
set /p CHOICE="Chon chuc nang [0-7]: "

if "%CHOICE%"=="1" goto SETUP_SERVER
if "%CHOICE%"=="2" goto RUN_SERVER
if "%CHOICE%"=="3" goto SETUP_CLIENT
if "%CHOICE%"=="4" goto RUN_ONE_CLIENT
if "%CHOICE%"=="5" goto RUN_SIX_CLIENTS
if "%CHOICE%"=="6" goto BUILD_PROJECT
if "%CHOICE%"=="7" goto CHECK_NETWORK
if "%CHOICE%"=="0" goto END
goto MENU

:CHECK_MVN
where mvn >nul 2>&1
if errorlevel 1 (
    echo [LOI] Khong tim thay 'mvn' trong PATH.
    echo       Hay cai Maven 3.9+ va them vao PATH, hoac chay bang IntelliJ IDEA.
    exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
    echo [LOI] Khong tim thay 'java' trong PATH. Hay cai JDK 21.
    exit /b 1
)
exit /b 0

:SHOW_LAN_IP
echo.
echo [INFO] Danh sach dia chi IPv4 LAN cua may nay:
powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.PrefixOrigin -ne 'WellKnown' } | Select-Object InterfaceAlias, IPAddress | Format-Table -AutoSize"
exit /b 0

:SETUP_SERVER
echo.
echo === THIET LAP MAY SERVER ===
call :SHOW_LAN_IP
echo [1/2] Dang mo Windows Firewall cho TCP 5000, UDP 5001, UDP 5002 (yeu cau quyen Admin)...
powershell -NoProfile -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~dp0scripts\open-firewall.cmd\"\"' -Verb RunAs -Wait"
echo [2/2] Kiem tra va build du an...
call :CHECK_MVN
if errorlevel 1 (
    pause
    goto MENU
)
call "%~dp0scripts\build.cmd"
echo.
echo [HOAN TAT] Hay ghi lai IPv4 LAN o tren de dien vao may Client!
pause
goto MENU

:RUN_SERVER
echo.
call :CHECK_MVN
if errorlevel 1 (
    pause
    goto MENU
)
call :SHOW_LAN_IP
echo [SERVER] Dang khoi dong ChatServerMain (TCP 5000, UDP 5001)...
echo          (Nhan Ctrl+C de dung Server)
echo.
call "%~dp0scripts\run-server.cmd"
pause
goto MENU

:SETUP_CLIENT
echo.
echo === CAU HINH MAY CLIENT ===
call :SHOW_LAN_IP
set /p SERVER_IP="Nhap IPv4 LAN cua may SERVER (khong dung localhost/127.0.0.1): "
if "%SERVER_IP%"=="" (
    echo [LOI] IP khong duoc de trong.
    pause
    goto MENU
)
if /i "%SERVER_IP%"=="localhost" (
    echo [LOI] App khong cho phep dung localhost. Hay nhap IP LAN (vd: 192.168.1.100).
    pause
    goto MENU
)
if "%SERVER_IP%"=="127.0.0.1" (
    echo [LOI] App khong cho phep dung 127.0.0.1. Hay nhap IP LAN (vd: 192.168.1.100).
    pause
    goto MENU
)

(
    echo # Replace with real Server PC IPv4; no localhost on a two-PC demo.
    echo server.host=%SERVER_IP%
    echo chat.tcp.port=5000
    echo chat.udp.listen.port=5001
    echo chat.multicast.group=239.10.10.10
    echo chat.multicast.port=5002
    echo.
    echo # File attachments are sent over TCP; maximum is 6 MiB per item.
    echo chat.attachment.max.bytes=6291456
) > "%~dp0config\client.properties"

echo [OK] Da cap nhat config\client.properties voi server.host=%SERVER_IP%
echo.
set /p TEST_CONN="Ban co muon kiem tra ket noi TCP toi %SERVER_IP%:5000 ngay khong? (y/n): "
if /i "%TEST_CONN%"=="y" (
    powershell -ExecutionPolicy Bypass -File "%~dp0scripts\check-network.ps1" -ServerIp "%SERVER_IP%"
)
pause
goto MENU

:RUN_ONE_CLIENT
echo.
call :CHECK_MVN
if errorlevel 1 (
    pause
    goto MENU
)
set /p CLIENT_NAME="Nhap ten nguoi dung (2-32 ky tu, mac dinh Client01): "
if "%CLIENT_NAME%"=="" set "CLIENT_NAME=Client01"
echo [CLIENT] Dang chay voi ten %CLIENT_NAME%...
call "%~dp0scripts\run-client.cmd" "%CLIENT_NAME%"
pause
goto MENU

:RUN_SIX_CLIENTS
echo.
call :CHECK_MVN
if errorlevel 1 (
    pause
    goto MENU
)
echo [CLIENT] Dang mo 6 cua so Client (Client01 -> Client06)...
call "%~dp0scripts\run-6-clients.cmd"
pause
goto MENU

:BUILD_PROJECT
echo.
call :CHECK_MVN
if errorlevel 1 (
    pause
    goto MENU
)
call "%~dp0scripts\build.cmd"
pause
goto MENU

:CHECK_NETWORK
echo.
set /p TARGET_IP="Nhap IPv4 cua may SERVER can kiem tra: "
if "%TARGET_IP%"=="" (
    echo [LOI] IP khong duoc de trong.
    pause
    goto MENU
)
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\check-network.ps1" -ServerIp "%TARGET_IP%"
pause
goto MENU

:END
endlocal
exit /b 0
