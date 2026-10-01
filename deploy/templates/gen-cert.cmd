@echo off
rem =============================================================================
rem TGAWS HTTPS cert generator (internal CA + server cert, bundled OpenSSL)
rem usage: gen-cert.cmd <INSTALL_DIR> [days(3650)]
rem output: {INSTALL_DIR}/nginx/conf/certs/{ca.crt,ca.key,server.crt,server.key}
rem SAN: DNS:localhost + IP:127.0.0.1 + all local IPv4 (mobile H5 over LAN IP)
rem idempotent: skip if server cert exists (reinstall does not overwrite)
rem note: all paths use forward slashes (Windows API compatible; avoids batch escape traps)
rem =============================================================================
setlocal EnableDelayedExpansion

set "INSTALL_DIR=%~1"
set "DAYS=%~2"
if "%DAYS%"=="" set "DAYS=3650"
set "CERTS=%INSTALL_DIR%/nginx/conf/certs"
set "OPENSSL=%INSTALL_DIR%/tools/openssl/openssl.exe"
set "OPENSSL_CONF=%INSTALL_DIR%/tools/openssl/etc/ssl/openssl.cnf"

if not exist "%CERTS%" mkdir "%CERTS%"
if exist "%CERTS%/server.crt" if exist "%CERTS%/server.key" (
    echo [SKIP] server cert already exists
    exit /b 0
)

rem collect local IPv4 (PowerShell one-liner, dedup, exclude loopback/APIPA)
set "SAN=DNS:localhost,IP:127.0.0.1"
for /f "delims=" %%i in ('powershell -NoProfile -Command "((Get-NetIPAddress -AddressFamily IPv4).Where({$_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'})).IPAddress -join ','"') do set "IPS=%%i"
if not "!IPS!"=="" set "SAN=!SAN!,IP:!IPS:,=,IP:!"
echo SAN=!SAN!

if not exist "%CERTS%/ca.crt" (
    "%OPENSSL%" req -x509 -newkey rsa:2048 -sha256 -days %DAYS% -nodes ^
        -keyout "%CERTS%/ca.key" -out "%CERTS%/ca.crt" ^
        -subj "/C=CN/O=TGAWS Internal CA/CN=TGAWS-Tunnel-Geohazard-CA" ^
        -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"
    if errorlevel 1 (echo [ERR] CA generation failed & exit /b 1)
)

"%OPENSSL%" req -newkey rsa:2048 -sha256 -nodes ^
    -keyout "%CERTS%/server.key" -out "%CERTS%/server.csr" ^
    -subj "/C=CN/O=TGAWS/CN=tgaws-server"
if errorlevel 1 (echo [ERR] server key generation failed & exit /b 1)

>"%CERTS%/server.ext" echo basicConstraints=CA:FALSE
>>"%CERTS%/server.ext" echo keyUsage=digitalSignature,keyEncipherment
>>"%CERTS%/server.ext" echo extendedKeyUsage=serverAuth
>>"%CERTS%/server.ext" echo subjectAltName=!SAN!

"%OPENSSL%" x509 -req -in "%CERTS%/server.csr" -CA "%CERTS%/ca.crt" -CAkey "%CERTS%/ca.key" ^
    -CAcreateserial -out "%CERTS%/server.crt" -days %DAYS% -sha256 -extfile "%CERTS%/server.ext"
if errorlevel 1 (echo [ERR] server cert signing failed & exit /b 1)

del "%CERTS%/server.csr" "%CERTS%/server.ext" >nul 2>&1
echo [OK] cert generated: %CERTS%
endlocal
