@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

where python >nul 2>nul
if %ERRORLEVEL%==0 (
    python "%SCRIPT_DIR%rename-framework.py" %*
    exit /b %ERRORLEVEL%
)

where py >nul 2>nul
if %ERRORLEVEL%==0 (
    py -3 "%SCRIPT_DIR%rename-framework.py" %*
    exit /b %ERRORLEVEL%
)

echo Python 3 not found. Install Python 3 or use rename-framework.ps1.
exit /b 1
