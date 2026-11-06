@echo off
setlocal
pushd "%~dp0"
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-frontend.ps1"
) else (
  pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-frontend.ps1"
)
set "TPS_BUILD_RESULT=%ERRORLEVEL%"
echo.
if not "%TPS_BUILD_RESULT%"=="0" echo Frontend build failed. Read the error above.
pause
popd
exit /b %TPS_BUILD_RESULT%
