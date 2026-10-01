@echo off
rem TGAWS db backup (T-805: mysqldump + AES-256-CBC + 3-gen rotation + weekly)
rem usage: backup.cmd <INSTALL_DIR>
rem env: BACKUP_KEY, MYSQL_ROOT_PASSWORD
setlocal EnableDelayedExpansion
set "INSTALL_DIR=%~1"
set "MYSQL=%INSTALL_DIR%/mysql/bin"
set "OPENSSL=%INSTALL_DIR%/tools/openssl/openssl.exe"
set "BACKUP_DIR=%INSTALL_DIR%/backup"
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"
if "%BACKUP_KEY%"=="" echo [ERR] BACKUP_KEY env required& exit /b 1
if "%MYSQL_ROOT_PASSWORD%"=="" echo [ERR] MYSQL_ROOT_PASSWORD env required& exit /b 1
for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set "D=%%a-%%b-%%c"
for /f "tokens=1-3 delims=: " %%a in ('time /t') do set "T=%%a%%b"
set "STAMP=%D%-%T%"
set "STAMP=%STAMP: =0%"
set "OUT=%BACKUP_DIR%/tgaws-%STAMP%.sql.enc"
echo backup tgaws -> %OUT%
"%MYSQL%/mysqldump.exe" -uroot "-p%MYSQL_ROOT_PASSWORD%" --protocol=tcp -P@MYSQL_PORT@ -h127.0.0.1 --single-transaction --routines --events --triggers --default-character-set=utf8mb4 tgaws | "%OPENSSL%" enc -aes-256-cbc -pbkdf2 -salt -pass env:BACKUP_KEY -out "%OUT%"
if errorlevel 1 echo [ERR] backup failed& exit /b 1
for /f "delims=" %%f in ('dir /b /o-d "%BACKUP_DIR%/tgaws-*.sql.enc" 2^>nul ^| more +3') do del "%BACKUP_DIR%/%%f" >nul 2>&1
for /f "tokens=1" %%w in ('powershell -NoProfile -Command "(Get-Date).DayOfWeek"') do set "WEEK=%%w"
if "%WEEK%"=="Sunday" copy "%OUT%" "%BACKUP_DIR%/weekly-%STAMP%.sql.enc" >nul
echo [OK] backup done
endlocal
