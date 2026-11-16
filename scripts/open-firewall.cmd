@echo off
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo Run this file as Administrator.
  pause
  exit /b 1
)
netsh advfirewall firewall add rule name="LTM Chat TCP 5000" dir=in action=allow protocol=TCP localport=5000
netsh advfirewall firewall add rule name="LTM Chat UDP 5001" dir=in action=allow protocol=UDP localport=5001
netsh advfirewall firewall add rule name="LTM Chat Multicast UDP 5002" dir=in action=allow protocol=UDP localport=5002
echo Firewall rules added for Chat TCP/UDP.
