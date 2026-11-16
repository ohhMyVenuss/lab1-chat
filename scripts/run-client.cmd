@echo off
setlocal
cd /d "%~dp0.."
if not "%~1"=="" set "CHAT_USERNAME=%~1"
if "%CHAT_USERNAME%"=="" set "CHAT_USERNAME=Client01"
echo [CLIENT] Starting LTM Connect as %CHAT_USERNAME%...
call mvn -q javafx:run
