@echo off
rem TGAWS db restore (T-805: backup-first, decrypt, drop+recreate, import)
rem usage: restore.cmd <INSTALL_DIR> <backup-file>
rem env: BACKUP_KEY, MYSQL_ROOT_PASSWORD
setlocal EnableDelayedExpansion
set "INSTALL_DIR=%~1"
set "SRC=%~2"
set "MYSQL=%INSTALL_DIR%/mysql/bin"
set "OPENSSL=%INSTALL_DIR%/tools/openssl/openssl.exe"
if not exist "%SRC%" echo [ERR] backup file not found: %SRC%& exit /b 1
if "%BACKUP_KEY%"=="" echo [ERR] BACKUP_KEY env required& exit /b 1
if "%MYSQL_ROOT_PASSWORD%"=="" echo [ERR] MYSQL_ROOT_PASSWORD env required& exit /b 1
echo backup current db first...
call "%INSTALL_DIR%/scripts/backup.cmd" "%INSTALL_DIR%"
if errorlevel 1 echo [ERR] pre-restore backup failed, abort& exit /b 1
set "TMP_SQL=%TEMP%/tgaws-restore-%RANDOM%.sql"
echo decrypt -> %TMP_SQL%
"%OPENSSL%" enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_KEY -in "%SRC%" -out "%TMP_SQL%"
if errorlevel 1 echo [ERR] decrypt failed (BACKUP_KEY mismatch?)& exit /b 1
echo restore tgaws (drop + recreate + import)...
"%MYSQL%/mysql.exe" -uroot "-p%MYSQL_ROOT_PASSWORD%" --protocol=tcp -P@MYSQL_PORT@ -h127.0.0.1 --default-character-set=utf8mb4 -e "DROP DATABASE IF EXISTS tgaws; CREATE DATABASE tgaws DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
"%MYSQL%/mysql.exe" -uroot "-p%MYSQL_ROOT_PASSWORD%" --protocol=tcp -P@MYSQL_PORT@ -h127.0.0.1 --default-character-set=utf8mb4 tgaws < "%TMP_SQL%"
if errorlevel 1 echo [ERR] restore failed& exit /b 1
del "%TMP_SQL%" >nul 2>&1
echo [OK] restore done
endlocal
