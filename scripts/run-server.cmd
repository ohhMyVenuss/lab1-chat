@echo off
setlocal
cd /d "%~dp0.."
echo [SERVER] Starting Chat TCP/UDP server...
call mvn -q exec:java -Dexec.mainClass=vn.ltm.chat.server.ChatServerMain
