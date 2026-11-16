@echo off
setlocal
cd /d "%~dp0.."
echo Launching six independent JavaFX client windows...
for %%U in (Client01 Client02 Client03 Client04 Client05 Client06) do (
  start "LTM Chat %%U" cmd.exe /k call "%~dp0run-client.cmd" %%U
)
echo Six client launch commands sent. Each window uses a unique username.
