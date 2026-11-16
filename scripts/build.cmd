@echo off
setlocal
cd /d "%~dp0.."
echo [BUILD] Building LTM Connect...
call mvn -U clean package
if errorlevel 1 (
  echo [ERROR] Build failed. Check JDK 21 and Maven configuration.
  exit /b 1
)
echo [OK] Build completed.
