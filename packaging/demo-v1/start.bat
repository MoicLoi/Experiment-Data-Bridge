@echo off
setlocal EnableExtensions
chcp 65001 >nul
title EDB Server Console
cd /d "%~dp0"

echo ============================================================
echo                     EDB Server Launcher
echo ============================================================
echo Working directory: %CD%
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found in PATH.
  echo.
  echo Possible causes:
  echo   - Java 21 is not installed.
  echo   - JAVA_HOME is set but %%JAVA_HOME%%\bin is not in PATH.
  echo   - A new terminal was not opened after changing PATH.
  echo.
  echo Install Java 21, run "java -version", and try again.
  goto :failed
)

echo Detected Java runtime:
java -version 2>&1
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
set "EDB_EXIT_CODE=%ERRORLEVEL%"

if not "%EDB_EXIT_CODE%"=="0" goto :failed

echo.
if exist "%~dp0eula.txt" findstr /r /c:"^[ ]*eula[ ]*=[ ]*false[ ]*$" "%~dp0eula.txt" >nul && (
  echo [INFO] First-run initialization is complete.
  echo Edit eula.txt and config\application.yml, then start again.
) || (
  echo [INFO] EDB Server stopped normally.
)
goto :finish

:failed
if not defined EDB_EXIT_CODE set "EDB_EXIT_CODE=1"
echo.
echo [ERROR] EDB failed to start or stopped unexpectedly. Exit code: %EDB_EXIT_CODE%
echo.
echo Common causes:
echo   1. eula.txt still contains eula=false.
echo   2. config\application.yml still contains CHANGE_ME.
echo   3. MySQL is not running, credentials are wrong, or the database is missing.
echo   4. Port 25560 is already in use.
echo   5. A plugin JAR is incompatible or duplicated.
echo   6. Java 21 is unavailable or the server JAR is missing.
echo.
echo Check these logs:
echo   logs\launcher.log
echo   logs\server.err.log
echo   logs\server.out.log
echo.
if exist "%~dp0logs\launcher.log" (
  echo Last launcher messages:
  powershell.exe -NoProfile -Command "Get-Content -LiteralPath '%~dp0logs\launcher.log' -Tail 12" 2>nul
  echo.
)
if exist "%~dp0logs\server.err.log" (
  echo Last server error messages:
  powershell.exe -NoProfile -Command "Get-Content -LiteralPath '%~dp0logs\server.err.log' -Tail 12" 2>nul
  echo.
)

:finish
echo Press any key to close this window...
pause >nul
exit /b %EDB_EXIT_CODE%
