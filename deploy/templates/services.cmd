@echo off
rem TGAWS service register/unregister (WinSW, T-801; install|uninstall|status)
rem usage: services.cmd <INSTALL_DIR> <install|uninstall|status>
setlocal
set "INSTALL_DIR=%~1"
set "ACTION=%~2"
set "WINSW=%INSTALL_DIR%/tools/winsw/WinSW-x64.exe"
if "%ACTION%"=="install" (
    echo register services...
    "%WINSW%" install "%INSTALL_DIR%/scripts/TGAWS-App.xml"
    "%WINSW%" install "%INSTALL_DIR%/nginx/TGAWS-Nginx.xml"
    "%WINSW%" install "%INSTALL_DIR%/mysql/TGAWS-MySQL.xml"
    echo start services...
    net start TGAWS-MySQL
    net start TGAWS-App
    net start TGAWS-Nginx
    exit /b 0
)
if "%ACTION%"=="uninstall" (
    net stop TGAWS-Nginx
    net stop TGAWS-App
    net stop TGAWS-MySQL
    "%WINSW%" uninstall "%INSTALL_DIR%/scripts/TGAWS-App.xml"
    "%WINSW%" uninstall "%INSTALL_DIR%/nginx/TGAWS-Nginx.xml"
    "%WINSW%" uninstall "%INSTALL_DIR%/mysql/TGAWS-MySQL.xml"
    exit /b 0
)
if "%ACTION%"=="status" (
    sc query TGAWS-App & sc query TGAWS-Nginx & sc query TGAWS-MySQL
    exit /b 0
)
echo usage: services.cmd ^<INSTALL_DIR^> ^<install^|uninstall^|status^>
exit /b 1
