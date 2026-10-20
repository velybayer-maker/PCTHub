@echo off
setlocal
cd /d "%~dp0backend"
echo =============================================
echo PCTHub Java API - Local XAMPP configuration
echo =============================================
where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java was not found. Install a JDK 21 and add it to PATH.
  pause
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo ERROR: Maven was not found. Install Apache Maven and add its bin folder to PATH.
  pause
  exit /b 1
)
set "DB_HOST=localhost"
set "DB_NAME=pcthub"
set "DB_USER=pcthub_user"
set "DB_PASSWORD=PCTHubLocal123!"
set "JWT_SECRET=pcthub-local-development-secret-replace-before-use-2026"
set "APP_DEMO_SEED=true"
echo Starting API at http://127.0.0.1:8081/api
mvn spring-boot:run
if errorlevel 1 (
  echo.
  echo The Java API stopped with an error. Read the Maven / Spring error above.
  pause
)
endlocal
