@echo off
rem TGAWS MySQL init script (T-803 installer step 6; idempotent)
rem usage: init-db.cmd <INSTALL_DIR> <MYSQL_PORT> <MYSQL_ROOT_PASSWORD> <APP_DB_PASSWORD> [--demo]
setlocal EnableDelayedExpansion

set "INSTALL_DIR=%~1"
set "MYSQL_PORT=%~2"
set "ROOT_PWD=%~3"
set "APP_PWD=%~4"
set "BIN=%INSTALL_DIR%/mysql/bin"
set "SCHEMA_DIR=%INSTALL_DIR%/scripts/db"
set "MY_INI=%INSTALL_DIR%/mysql/my.ini"

if not exist "%BIN%/mysqld.exe" echo [ERR] mysqld.exe missing& exit /b 1

if exist "%INSTALL_DIR%/mysql/data/mysql" goto initialized
echo [1/5] initialize data dir...
"%BIN%/mysqld.exe" --defaults-file="%MY_INI%" --initialize-insecure --console
if errorlevel 1 echo [ERR] initialize failed& exit /b 1
:initialized
echo [1/5] data dir ready

echo [2/5] temp-start MySQL...
start "tgaws-mysql-init" /MIN "%BIN%/mysqld.exe" --defaults-file="%MY_INI%" --console

echo        waiting port %MYSQL_PORT% ...
powershell -NoProfile -Command "$ok=$false; for($i=0;$i -lt 300;$i++){ & '%BIN%/mysqladmin.exe' -uroot --protocol=tcp --port=%MYSQL_PORT% --host=127.0.0.1 ping *> $null; if($LASTEXITCODE -eq 0){$ok=$true;break}; Start-Sleep -Seconds 1 }; if(-not $ok){exit 1}"
if errorlevel 1 echo [ERR] MySQL startup timeout& exit /b 1
echo        port %MYSQL_PORT% ready

echo [3/5] set root password + create database/user...
"%BIN%/mysql.exe" -uroot --protocol=tcp --port=%MYSQL_PORT% --host=127.0.0.1 --default-character-set=utf8mb4 -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '%ROOT_PWD%'; CREATE DATABASE IF NOT EXISTS tgaws DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; CREATE USER IF NOT EXISTS 'tgaws'@'127.0.0.1' IDENTIFIED BY '%APP_PWD%'; CREATE USER IF NOT EXISTS 'tgaws'@'localhost' IDENTIFIED BY '%APP_PWD%'; GRANT ALL PRIVILEGES ON tgaws.* TO 'tgaws'@'127.0.0.1'; GRANT ALL PRIVILEGES ON tgaws.* TO 'tgaws'@'localhost'; FLUSH PRIVILEGES;"
if errorlevel 1 echo [ERR] create db/user failed& exit /b 1

echo [4/5] run schema + data scripts...
set "ROOTPWD_OPT=-uroot "-p%ROOT_PWD%" --protocol=tcp --port=%MYSQL_PORT% --host=127.0.0.1 --default-character-set=utf8mb4"
for %%f in ("%SCHEMA_DIR%/schema/01_tables.sql" "%SCHEMA_DIR%/data/01_dict.sql" "%SCHEMA_DIR%/data/02_init_admin.sql" "%SCHEMA_DIR%/data/03_default_rule.sql" "%SCHEMA_DIR%/data/04_init_roles.sql" "%SCHEMA_DIR%/data/05_init_config.sql" "%SCHEMA_DIR%/patches/t606_patrol_ddl.sql" "%SCHEMA_DIR%/patches/t607_rpt_stat_daily.sql" "%SCHEMA_DIR%/patches/t702_third_app.sql" "%SCHEMA_DIR%/patches/t703_audit_hash.sql" "%SCHEMA_DIR%/patches/t708_rule_version.sql") do (
    if exist %%f (
        "%BIN%/mysql.exe" %ROOTPWD_OPT% tgaws < %%f
        if errorlevel 1 echo [ERR] script failed: %%f& exit /b 1
    )
)
if "%~5"=="--demo" (
    "%BIN%/mysql.exe" %ROOTPWD_OPT% tgaws < "%SCHEMA_DIR%/t2_seed.sql"
    if errorlevel 1 echo [ERR] demo seed failed: t2_seed.sql& exit /b 1
    if exist "%SCHEMA_DIR%/data/05_demo_seed.sql" "%BIN%/mysql.exe" %ROOTPWD_OPT% tgaws < "%SCHEMA_DIR%/data/05_demo_seed.sql"
    if errorlevel 1 echo [ERR] demo seed failed: 05_demo_seed.sql& exit /b 1
)

echo [5/5] shutdown temp instance...
"%BIN%/mysqladmin.exe" %ROOTPWD_OPT% shutdown
echo [OK] MySQL init done
endlocal
