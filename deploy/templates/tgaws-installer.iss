; =============================================================================
; TGAWS 隧道地质灾害AI预警系统 一键安装向导（Inno Setup 6，T-803）
; 流程（《2.系统架构设计说明书》2.12.1）：
;   选目录（默认 D:\TGAWS，强制 ASCII——nginx 不支持非 ASCII 路径）
;   → 端口配置（80/443/8080/3306/9000，冲突检测）
;   → 初始口令（MySQL root + 管理员初始口令，复杂度校验）
;   → 清理同名旧服务 → 释放文件 → 占位符替换 → 证书生成（自签/内网 CA）
;   → MySQL 初始化 → 注册 3 个 Windows 服务并启动 → 健康检查验证 → 完成
; 支持静默安装：/VERYSILENT /DBROOTPWD=xxx /ADMINPWD=xxx [/DIR=...] [/MYSQLPORT=...]
; 卸载：服务注销 + 可选保留数据（默认保留，防误删）
;
; 修订 2026-09-30（致命：口令框不可见→交互式安装无法继续）：
;   1) 原实现把 5 个端口 + 2 个口令挤在同一个 CreateInputQueryPage 页面里（7 行）。
;      Inno 的 TInputQueryWizardPage 不会滚动：放不下的行被裁到页面可视区之外，
;      用户既看不到也无法输入（实测 100% 缩放：页面可视高 330px、行距 56px，
;      第 6/7 行整体落在页面之外，页面内也不存在滚动条控件）。
;      结果两个口令永远为空 → 点"下一步"必然弹"口令至少 8 位。"，交互式安装
;      100% 卡死在该页（125% 缩放下用户截图同样只剩 5 个端口框可见）。
;      现将向导拆成两页：端口页（5 行）+ 初始口令页（2 行），任何缩放比例下
;      所有输入框都在页面可视区内。
;   2) 端口增加"数字 / 1-65535 / 五项不重复"校验；口令改为逐项校验并提示具体
;      原因（长度、空格、单双引号、& < > | ^ % 等会破坏 cmd 命令行 / 配置 XML /
;      SQL 的字符、必须含字母、必须含数字），避免带着非法值进入安装流程后
;      在 MySQL/XML/服务阶段才失败。
;   3) 安装前自动停止并注销同名旧服务（幂等）：旧服务指向历史安装目录时，
;      WinSW install 与 mysqld --install 都会因"服务已存在"失败。
;   4) 静默参数改为大小写不敏感 + 支持带引号的值；静默模式在开始安装前校验参数。
; 修订 2026-09-30 晚（端口检测误报 + 交互不友好，用户实测挡路）：
;   5) PortInUse 原实现 netstat|findstr /R ":PORT .*LISTENING"：cmd 引号撕裂后
;      .*LISTENING 变成"无法打开的文件"，实际生效的是裸子串 ":PORT"——TIME_WAIT、
;      远端端口、":44330" 之类子串全部误报占用（用户换 5 组空闲端口全被挡）。
;      改用 PowerShell Get-NetTCPConnection -State Listen 精确匹配。
;   6) 端口冲突不再挡路：交互模式弹"是否自动填入空闲端口"，一键从当前值向后找
;      最近空闲端口；静默模式自动调整并在日志记录——保证无人值守一次装完。
;   7) NextButtonClick 顶部加 WizardSilent 放行：静默模式下 Inno 仍内部走过向导页
;      序列（页面值为默认值），校验会拿默认 3306 误判冲突并把 Result=False 变成
;      安装中止（实测 exit 1）；静默参数校验统一由 PrepareToInstall 负责。
;   8) services.cmd 三处修复：UTF-8 中文注释在 GBK cmd 解析下吞 CRLF（变量赋值丢失、
;      服务注册失败）→ 注释 ASCII 化；copy 用引号包裹的正斜杠路径在 cmd 下报"系统
;      找不到指定的文件"（实测反斜杠正常，r9 时 exe 已预置所以从未暴露）→ 改反斜杠
;      并补 copy 失败即退出的 errorlevel 检查。
;   9) ssPostInstall 的 MsgBox 全部改走 Notify()：实测 /VERYSILENT 下 [Code] MsgBox
;      未被抑制（弹窗无人点击 → 安装进程永久挂起，残留孤儿进程又让下一次启动撞
;      setup 互斥锁 exit 1）；静默模式改写日志，交互模式维持弹窗。
; =============================================================================
#define AppNameCN "隧道地质灾害AI预警系统"
#define AppVer "1.1.0"
; 构建输出目录：默认沿用构建工作区，可用 ISPP 覆盖（/DTGAWS_OUTDIR=... 或包装脚本）
#ifndef TGAWS_OUTDIR
#define TGAWS_OUTDIR "D:\tgaws-deploy\output"
#endif

[Setup]
AppId={{B7F3C2A1-5E9D-4A6B-8C11-TGAWS000001}
AppName={#AppNameCN}（TGAWS）
AppVersion={#AppVer}
AppPublisher=TGAWS 项目组
DefaultDirName=D:\TGAWS
DisableDirPage=no
DefaultGroupName=TGAWS
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
OutputDir={#TGAWS_OUTDIR}
OutputBaseFilename=TGAWS-Setup-{#AppVer}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
MinVersion=10.0
SetupLogging=yes
UninstallDisplayName={#AppNameCN}（TGAWS）

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Files]
; 后端应用 + jlink 运行时
Source: "D:\tgaws-deploy\staging\app\tgaws-web.jar"; DestDir: "{app}\app"; Flags: ignoreversion
Source: "D:\tgaws-deploy\staging\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs
; Nginx（含前端静态资源与证书目录占位）
Source: "D:\tgaws-deploy\staging\nginx\*"; DestDir: "{app}\nginx"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "logs\*,temp\*"
; MySQL 绿色版（data 目录由初始化脚本生成）
Source: "D:\tgaws-deploy\staging\mysql\*"; DestDir: "{app}\mysql"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "data\*"
; 工具：OpenSSL / WinSW
Source: "D:\tgaws-deploy\staging\tools\*"; DestDir: "{app}\tools"; Flags: ignoreversion recursesubdirs createallsubdirs
; 初始化脚本（DB schema/data/patches）
Source: "D:\tgaws-deploy\staging\scripts\db\*"; DestDir: "{app}\scripts\db"; Flags: ignoreversion recursesubdirs createallsubdirs
; 运维脚本（services/init-db/gen-cert/backup/restore）——[Code] 占位替换后执行
Source: "D:\tgaws-deploy\staging\scripts\*.cmd"; DestDir: "{app}\scripts"; Flags: ignoreversion
; 占位符替换脚本（UTF-8 安全，[Code] 以环境变量注入值调用）
Source: "D:\tgaws-deploy\staging\scripts\replace-placeholders.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion
; WinSW 服务模板（安装时替换占位符生成 TGAWS-App.xml / TGAWS-Nginx.xml）
Source: "D:\tgaws-deploy\staging\scripts\templates\*"; DestDir: "{app}\scripts\templates"; Flags: ignoreversion
; 交付文档
Source: "D:\tgaws-deploy\staging\docs\*"; DestDir: "{app}\docs"; Flags: ignoreversion recursesubdirs createallsubdirs

[Dirs]
; 运行时日志目录（my.ini error/slow log、安装日志、nginx logs 不随包释放）
Name: "{app}\logs\mysql"
Name: "{app}\nginx\logs"

[Code]
const
  HEX_CHARS = '0123456789abcdef';
var
  PortPage: TInputQueryWizardPage;
  PwdPage: TInputQueryWizardPage;
  HttpPort, HttpsPort, AppPort, MysqlPort, TcpPort: string;
  DbRootPwd, AdminInitPwd: string;
  AppInstallDir: string;
  InstallLog: string;

// ---------- 工具函数 ----------
function IsAsciiPath(const S: string): Boolean;
var I: Integer;
begin
  Result := True;
  for I := 1 to Length(S) do
    if Ord(S[I]) > 127 then begin Result := False; Exit; end;
end;

function RandomHex(const Len: Integer): string;
var I: Integer;
begin
  Result := '';
  for I := 1 to Len do
    Result := Result + HEX_CHARS[Trunc(Random(16)) + 1];
end;

function HasDigit(const S: string): Boolean;
var I: Integer;
begin
  Result := False;
  for I := 1 to Length(S) do
    if (S[I] >= '0') and (S[I] <= '9') then begin Result := True; Exit; end;
end;

function HasLetter(const S: string): Boolean;
var I: Integer;
begin
  Result := False;
  for I := 1 to Length(S) do
    if ((S[I] >= 'A') and (S[I] <= 'Z')) or ((S[I] >= 'a') and (S[I] <= 'z')) then begin
      Result := True; Exit;
    end;
end;

// 去掉静默参数值两端的引号：/DBROOTPWD="xxx"
function Unquote(const S: string): string;
var T: string;
begin
  T := Trim(S);
  if (Length(T) >= 2) and (T[1] = '"') and (T[Length(T)] = '"') then
    T := Copy(T, 2, Length(T) - 2);
  Result := T;
end;

function RunAndLog(const Exe, Params: string; const Show: Boolean): Boolean;
var Code: Integer;
begin
  Log('RUN: ' + Exe + ' ' + Params);
  if Show then
    Result := Exec(Exe, Params, '', SW_SHOW, ewWaitUntilTerminated, Code)
  else
    Result := Exec(Exe, Params, '', SW_HIDE, ewWaitUntilTerminated, Code);
  // Exec 仅表示"进程成功启动"；真正的成败看退出码（T-813 修复：init-db/services 失败不再静默）
  Result := Result and (Code = 0);
  Log('RUN rc=' + IntToStr(Code));
end;

function CurlInstalled: Boolean;
begin
  Result := FileExists(ExpandConstant('{sys}\curl.exe'))
         or FileExists(ExpandConstant('{syswow64}\curl.exe'));
end;

// ---------- 端口冲突检测 ----------
// 原实现 netstat | findstr /R ":PORT .*LISTENING"：cmd 引号撕裂导致 .*LISTENING 变成
// "无法打开的文件"，实际生效的匹配只剩裸子串 ":PORT"——TIME_WAIT 连接、远端端口、
// ":44330" 之类子串全部误报占用（实测用户换 5 组空闲端口全部被挡）。改用 PowerShell
// Get-NetTCPConnection 精确匹配 Listen 状态（无管道/引号问题，PS 5.1 Win10+ 内置）。
function PortInUse(const Port: string): Boolean;
var Res: Integer;
begin
  Result := Exec(ExpandConstant('{sys}') + '\WindowsPowerShell\v1.0\powershell.exe',
    '-NoProfile -NonInteractive -Command "if (Get-NetTCPConnection -LocalPort ' + Port
    + ' -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"',
    '', SW_HIDE, ewWaitUntilTerminated, Res)
    and (Res = 0);
end;

// ---------- 输入校验：返回空串=通过，否则返回给用户看的具体原因 ----------
function CheckPort(const Name, S: string): string;
var I, N: Integer; T: string;
begin
  Result := '';
  T := Trim(S);
  if T = '' then begin Result := Name + '不能为空。'; Exit; end;
  if Length(T) > 5 then begin
    Result := Name + '必须是 1-65535 的整数，当前值：' + S;
    Exit;
  end;
  for I := 1 to Length(T) do
    if (T[I] < '0') or (T[I] > '9') then begin
      Result := Name + '必须是数字，当前值：' + S;
      Exit;
    end;
  N := StrToIntDef(T, 0);
  if (N < 1) or (N > 65535) then
    Result := Name + '必须在 1-65535 之间，当前值：' + S;
end;

function CheckPwd(const Name, S: string): string;
var I: Integer;
begin
  Result := '';
  if Length(S) < 8 then begin
    Result := Name + '至少 8 位（当前 ' + IntToStr(Length(S)) + ' 位）。'#13#10
      + '请在该口令输入框中设置：至少 8 位，且同时包含字母与数字，例如 Tgaws@2026Root';
    Exit;
  end;
  for I := 1 to Length(S) do begin
    if S[I] = ' ' then begin Result := Name + '不能包含空格。'; Exit; end;
    if S[I] = '"' then begin
      Result := Name + '不能包含双引号（会破坏安装脚本命令行）。'; Exit;
    end;
    if S[I] = '''' then begin
      Result := Name + '不能包含单引号（会破坏 MySQL 初始化 SQL）。'; Exit;
    end;
    if (S[I] = '&') or (S[I] = '<') then begin
      Result := Name + '不能包含 & 或 <（会破坏服务配置 XML）。'; Exit;
    end;
    if (S[I] = '|') or (S[I] = '>') or (S[I] = '^') or (S[I] = '%') then begin
      Result := Name + '不能包含 | > ^ %（会破坏安装脚本命令行）。'; Exit;
    end;
  end;
  if not HasLetter(S) then begin Result := Name + '必须包含至少一个字母。'; Exit; end;
  if not HasDigit(S) then begin Result := Name + '必须包含至少一个数字。'; Exit; end;
end;

function ValidatePorts: string;
begin
  Result := CheckPort('HTTP 端口', HttpPort);
  if Result <> '' then Exit;
  Result := CheckPort('HTTPS 端口', HttpsPort);
  if Result <> '' then Exit;
  Result := CheckPort('应用端口', AppPort);
  if Result <> '' then Exit;
  Result := CheckPort('MySQL 端口', MysqlPort);
  if Result <> '' then Exit;
  Result := CheckPort('TCP 采集端口', TcpPort);
  if Result <> '' then Exit;
  if (HttpPort = HttpsPort) or (HttpPort = AppPort) or (HttpPort = MysqlPort) or (HttpPort = TcpPort)
     or (HttpsPort = AppPort) or (HttpsPort = MysqlPort) or (HttpsPort = TcpPort)
     or (AppPort = MysqlPort) or (AppPort = TcpPort) or (MysqlPort = TcpPort) then
    Result := '五个端口不能重复，请为每个服务指定不同端口。';
end;

function ValidatePasswords: string;
begin
  Result := CheckPwd('MySQL root 口令', DbRootPwd);
  if Result <> '' then Exit;
  Result := CheckPwd('管理员初始口令', AdminInitPwd);
end;

function ConflictingPorts: string;
begin
  Result := '';
  if PortInUse(HttpPort) then Result := Result + 'HTTP ' + HttpPort + '  ';
  if PortInUse(HttpsPort) then Result := Result + 'HTTPS ' + HttpsPort + '  ';
  if PortInUse(AppPort) then Result := Result + '应用 ' + AppPort + '  ';
  if PortInUse(MysqlPort) then Result := Result + 'MySQL ' + MysqlPort + '  ';
  if PortInUse(TcpPort) then Result := Result + 'TCP采集 ' + TcpPort + '  ';
end;

// 从当前值开始逐个 +1 探测空闲端口，自动重填五个端口（互不重复）。
// 用户可保留默认值一路"下一步"：有冲突就自动换到最近的空闲端口。
procedure AutoFillFreePorts;
var N, I: Integer; Busy, P: string;
begin
  Busy := ' ';
  for I := 1 to 5 do begin
    case I of
      1: P := HttpPort;
      2: P := HttpsPort;
      3: P := AppPort;
      4: P := MysqlPort;
      5: P := TcpPort;
    end;
    N := StrToIntDef(P, 0);
    if (N < 1) or (N > 65535) then N := 80;
    while (N < 65535)
      and (PortInUse(IntToStr(N)) or (Pos(' ' + IntToStr(N) + ' ', Busy) > 0)) do
      N := N + 1;
    case I of
      1: HttpPort := IntToStr(N);
      2: HttpsPort := IntToStr(N);
      3: AppPort := IntToStr(N);
      4: MysqlPort := IntToStr(N);
      5: TcpPort := IntToStr(N);
    end;
    Busy := Busy + IntToStr(N) + ' ';
  end;
end;

// ---------- 同名旧服务清理（幂等；覆盖安装必需） ----------
// 0=存在 1060=不存在 1072=已标记为删除
function ServiceQueryCode(const Name: string): Integer;
var Code: Integer;
begin
  Code := 1;
  if not Exec(ExpandConstant('{cmd}'), '/C "sc query ' + Name + ' >nul 2>&1"',
        '', SW_HIDE, ewWaitUntilTerminated, Code) then
    Code := 1;
  Result := Code;
end;

function ServiceGone(const Name: string): Boolean;
var Code: Integer;
begin
  Code := ServiceQueryCode(Name);
  Result := (Code <> 0) and (Code <> 1072);
end;

procedure RemoveExistingServices;
var Exists, AllGone: Boolean; J: Integer; Code: Integer;
begin
  Log('== 同名旧服务清理（幂等） ==');
  Exists := (ServiceQueryCode('TGAWS-App') = 0)
         or (ServiceQueryCode('TGAWS-Nginx') = 0)
         or (ServiceQueryCode('TGAWS-MySQL') = 0);
  if not Exists then begin
    Log('未发现同名旧服务，跳过清理');
    Exit;
  end;
  Log('发现同名旧服务（可能指向历史安装目录），先停止并注销，否则服务注册会因"已存在"失败');
  // 不等待单条命令结果：stop 后立刻 delete（运行中的服务会被标记为删除，停止后自动消失）
  Exec(ExpandConstant('{cmd}'),
    '/C "sc stop TGAWS-Nginx >nul 2>&1 & sc stop TGAWS-App >nul 2>&1 & sc stop TGAWS-MySQL >nul 2>&1'
    + ' & sc delete TGAWS-Nginx >nul 2>&1 & sc delete TGAWS-App >nul 2>&1 & sc delete TGAWS-MySQL >nul 2>&1"',
    '', SW_HIDE, ewWaitUntilTerminated, Code);
  AllGone := False;
  for J := 1 to 60 do begin
    if ServiceGone('TGAWS-App') and ServiceGone('TGAWS-Nginx') and ServiceGone('TGAWS-MySQL') then begin
      AllGone := True;
      Break;
    end;
    Sleep(1000);
  end;
  if AllGone then
    Log('旧服务已注销（等待 ' + IntToStr(J) + ' 秒）')
  else
    Log('WARN 旧服务注销等待超时（60 秒），继续安装');
end;

// ---------- 向导页面 ----------
procedure InitializeWizard;
begin
  // 页面 1：端口（5 行，页面可视区内）
  PortPage := CreateInputQueryPage(wpSelectDir,
    '端口配置', '系统服务端口（一般保持默认即可）',
    '这 5 个端口用于系统内部服务：网页访问（HTTP 80 / HTTPS 443）、后台服务 8080、'#13#10
    + '数据库 3306、设备数据采集 9000。'#13#10
    + '保持默认值直接点"下一步"即可：若某个端口已被其他程序占用，向导会自动提示，'#13#10
    + '并可一键自动填入空闲端口。');
  PortPage.Add('HTTP 端口', False);
  PortPage.Add('HTTPS 端口', False);
  PortPage.Add('应用端口', False);
  PortPage.Add('MySQL 端口', False);
  PortPage.Add('TCP 采集端口', False);
  // Inno 6：Add 仅 (Prompt, Password) 两参数，默认值经 Values 赋值
  PortPage.Values[0] := '80';
  PortPage.Values[1] := '443';
  PortPage.Values[2] := '8080';
  PortPage.Values[3] := '3306';
  PortPage.Values[4] := '9000';

  // 页面 2：初始口令（2 行）
  // 注意：不要与端口放在同一页——TInputQueryWizardPage 不滚动，超出的行会被裁掉
  PwdPage := CreateInputQueryPage(PortPage.ID,
    '初始口令配置', '设置 MySQL root 口令与管理员初始口令',
    '口令要求：至少 8 位，同时包含字母与数字；不能包含空格、引号、& < > | ^ % 等字符。'#13#10
    + '请自行记录这两个口令，安装完成后向导不再显示。');
  PwdPage.Add('MySQL root 口令', True);
  PwdPage.Add('管理员初始口令（admin）', True);
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var E, C: string;
begin
  Result := True;
  // 静默模式：Inno 内部仍会走过向导页序列（页面值为默认值），但向导校验在此
  // 只会误判（默认 3306 与开发库冲突即中止）；静默参数校验/端口自动调整由
  // PrepareToInstall 统一负责，这里全部放行。
  if WizardSilent then Exit;
  if CurPageID = wpSelectDir then begin
    if not IsAsciiPath(WizardForm.DirEdit.Text) then begin
      MsgBox('安装目录必须为纯英文字符路径（Nginx 不支持中文路径）。'#13#10
        + '默认 D:\TGAWS 即可。', mbError, MB_OK);
      Result := False; Exit;
    end;
  end;
  if CurPageID = PortPage.ID then begin
    HttpPort := Trim(PortPage.Values[0]);
    HttpsPort := Trim(PortPage.Values[1]);
    AppPort := Trim(PortPage.Values[2]);
    MysqlPort := Trim(PortPage.Values[3]);
    TcpPort := Trim(PortPage.Values[4]);
    E := ValidatePorts;
    if E <> '' then begin
      MsgBox(E, mbError, MB_OK);
      Result := False; Exit;
    end;
    C := ConflictingPorts;
    if C <> '' then begin
      // 冲突不挡路：一键自动填入空闲端口（从当前值向后找最近空闲），用户确认后即可继续
      if MsgBox('检测到以下端口已被占用：'#13#10 + C + #13#10#13#10
        + '是否自动填入空闲端口？'#13#10
        + '（选"是"自动换成最近的空闲端口；选"否"返回手动修改）',
        mbConfirmation, MB_YESNO) = IDYES then begin
        AutoFillFreePorts;
        PortPage.Values[0] := HttpPort;
        PortPage.Values[1] := HttpsPort;
        PortPage.Values[2] := AppPort;
        PortPage.Values[3] := MysqlPort;
        PortPage.Values[4] := TcpPort;
        MsgBox('已自动填入空闲端口：'#13#10
          + '  网页 HTTP ' + HttpPort + ' / HTTPS ' + HttpsPort + #13#10
          + '  后台服务 ' + AppPort + ' / 数据库 ' + MysqlPort + ' / 设备采集 ' + TcpPort + #13#10#13#10
          + '请确认后点击"下一步"继续。', mbInformation, MB_OK);
      end;
      Result := False; Exit;
    end;
  end;
  if CurPageID = PwdPage.ID then begin
    DbRootPwd := PwdPage.Values[0];
    AdminInitPwd := PwdPage.Values[1];
    E := ValidatePasswords;
    if E <> '' then begin
      MsgBox(E, mbError, MB_OK);
      Result := False; Exit;
    end;
  end;
end;

// ---------- 静默参数解析（大小写不敏感，支持带引号的值） ----------
procedure ParseSilentParams;
var I: Integer; Raw, Up: string;
begin
  for I := 1 to ParamCount do begin
    Raw := ParamStr(I);
    Up := Uppercase(Raw);
    if Pos('/DBROOTPWD=', Up) = 1 then DbRootPwd := Unquote(Copy(Raw, 12, MaxInt))
    else if Pos('/ADMINPWD=', Up) = 1 then AdminInitPwd := Unquote(Copy(Raw, 11, MaxInt))
    else if Pos('/MYSQLPORT=', Up) = 1 then MysqlPort := Trim(Copy(Raw, 12, MaxInt))
    else if Pos('/HTTPPORT=', Up) = 1 then HttpPort := Trim(Copy(Raw, 11, MaxInt))
    else if Pos('/HTTPSPORT=', Up) = 1 then HttpsPort := Trim(Copy(Raw, 12, MaxInt))
    else if Pos('/APPPORT=', Up) = 1 then AppPort := Trim(Copy(Raw, 10, MaxInt))
    else if Pos('/TCPPORT=', Up) = 1 then TcpPort := Trim(Copy(Raw, 10, MaxInt));
  end;
  if DbRootPwd = '' then DbRootPwd := 'Tgaws@' + RandomHex(8) + 'R1';
  if AdminInitPwd = '' then AdminInitPwd := 'Tgaws@' + RandomHex(8) + 'A1';
  if HttpPort = '' then HttpPort := '80';
  if HttpsPort = '' then HttpsPort := '443';
  if AppPort = '' then AppPort := '8080';
  if MysqlPort = '' then MysqlPort := '3306';
  if TcpPort = '' then TcpPort := '9000';
end;

// 静默安装：开始安装前校验参数，参数非法直接中止（避免解包到一半才失败）；
// 端口冲突不中止——自动换成最近的空闲端口（保证无人值守一次装完），并在日志中记录。
function PrepareToInstall(var NeedsRestart: Boolean): String;
var E, C: string;
begin
  Result := '';
  if not WizardSilent then Exit;
  ParseSilentParams;
  E := ValidatePorts;
  if E = '' then E := ValidatePasswords;
  if E = '' then begin
    C := ConflictingPorts;
    if C <> '' then begin
      AutoFillFreePorts;
      Log('静默安装：检测到端口冲突（' + C + '），已自动调整为 HTTP ' + HttpPort
        + ' / HTTPS ' + HttpsPort + ' / 应用 ' + AppPort
        + ' / MySQL ' + MysqlPort + ' / TCP采集 ' + TcpPort);
    end;
  end;
  if E <> '' then
    Result := '安装参数校验失败：' + E + #13#10#13#10
      + '请用 /VERYSILENT /DBROOTPWD=xxx /ADMINPWD=xxx 等参数重试（口令需 ≥8 位且含字母与数字）。';
end;

// 静默模式不弹窗（实测 /VERYSILENT 下 [Code] MsgBox 未被抑制，无人点击会永久挂起），
// 改为写安装日志；交互模式维持弹窗提示。
procedure Notify(const Msg: string);
begin
  if WizardSilent then
    Log('NOTIFY: ' + Msg)
  else
    MsgBox(Msg, mbInformation, MB_OK);
end;

// ---------- 安装主流程 ----------
procedure CurStepChanged(CurStep: TSetupStep);
var
  DirF, DirB, AppDbPwd, JwtSecret, ConfigKey, BackupKey, DbUrl: string;
  Res: Integer;
  RunOk: Boolean;
  HealthOk: Boolean;
  TryCount: Integer;
  HealthBody: AnsiString;
begin
  if CurStep <> ssPostInstall then Exit;

  ParseSilentParams;
  AppInstallDir := ExpandConstant('{app}');
  DirF := AppInstallDir;
  StringChange(DirF, '\', '/');
  DirB := AppInstallDir;
  AppDbPwd := 'Tgaws@' + RandomHex(12) + 'D1';
  JwtSecret := RandomHex(48);
  ConfigKey := RandomHex(32);
  BackupKey := RandomHex(32);
  InstallLog := DirB + '\logs\install.log';

  // 幂等：先注销同名旧服务（旧实例可能占用端口/目录，导致后续初始化或注册失败）
  RemoveExistingServices;

  Log('== 占位符替换 ==');
  // 由随包 replace-placeholders.ps1 完成（Inno PS 文本 API 为 AnsiString 体系，
  // UTF-8 中文文件经 GBK 往返会损坏；PS [IO.File] 默认 UTF-8 读写无此问题）。
  // my.ini/nginx.conf 用正斜杠路径（MySQL option 文件反斜杠是转义符：\t→TAB、\s→空格）；
  // WinSW xml 用反斜杠（XML 无转义）；DB_URL 中 & 写 &amp;（XML 实体）。
  DbUrl := 'jdbc:mysql://127.0.0.1:' + MysqlPort + '/tgaws?useSSL=false&amp;serverTimezone=Asia/Shanghai&amp;characterEncoding=utf8&amp;allowPublicKeyRetrieval=true';
  RunOk := RunAndLog(ExpandConstant('{cmd}'),
    '/C "set "TGAWS_DIRB=' + DirB + '"&& set "TGAWS_DIRF=' + DirF
    + '"&& set "TGAWS_HTTP_PORT=' + HttpPort + '"&& set "TGAWS_HTTPS_PORT=' + HttpsPort
    + '"&& set "TGAWS_APP_PORT=' + AppPort + '"&& set "TGAWS_MYSQL_PORT=' + MysqlPort
    + '"&& set "TGAWS_TCP_PORT=' + TcpPort + '"&& set "TGAWS_MEM_MB=4096'
    + '"&& set "TGAWS_DB_URL=' + DbUrl
    + '"&& set "TGAWS_DB_USERNAME=tgaws"&& set "TGAWS_DB_PASSWORD=' + AppDbPwd
    + '"&& set "TGAWS_JWT_SECRET=' + JwtSecret + '"&& set "TGAWS_CONFIG_ENC_KEY=' + ConfigKey
    + '"&& set "TGAWS_ADMIN_INIT_PASSWORD=' + AdminInitPwd
    + '"&& "' + ExpandConstant('{sys}') + '\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "'
    + DirB + '\scripts\replace-placeholders.ps1" >> "' + InstallLog + '" 2>&1"', False);
  if not RunOk then begin
    Notify('占位符替换失败，请查看日志：' + InstallLog);
    Exit;
  end;
  // WinSW 模板 → 实际 XML
  RenameFile(DirB + '\scripts\templates\winsw-app.xml', DirB + '\scripts\TGAWS-App.xml');
  RenameFile(DirB + '\scripts\templates\winsw-nginx.xml', DirB + '\nginx\TGAWS-Nginx.xml');
  // MySQL 走原生 NT 服务（mysqld --install，services.cmd 内执行），无需 WinSW 模板

  Log('== 生成 HTTPS 自签证书（内网 CA） ==');
  RunOk := RunAndLog(ExpandConstant('{cmd}'), '/C ""' + DirB + '\scripts\gen-cert.cmd" "' + DirB + '" > "' + InstallLog + '" 2>&1"', False);

  Log('== MySQL 初始化（幂等；--demo 追加演示种子） ==');
  RunOk := RunAndLog(ExpandConstant('{cmd}'), '/C ""' + DirB + '\scripts\init-db.cmd" "' + DirB + '" ' + MysqlPort + ' "' + DbRootPwd + '" "' + AppDbPwd + '" --demo >> "' + InstallLog + '" 2>&1"', False);
  if not RunOk then begin
    Notify('MySQL 初始化失败，请查看日志：' + InstallLog);
    Exit;
  end;

  Log('== 注册并启动服务 ==');
  // services.cmd 已幂等（已注册/已运行自动跳过），失败重试一次——覆盖 AV 扫描
  // 瞬时锁文件等偶发场景（实测首次失败可零输出 rc=1，3 秒后重试即成功）。
  RunOk := False;
  for TryCount := 1 to 2 do begin
    RunOk := RunAndLog(ExpandConstant('{cmd}'), '/C ""' + DirB + '\scripts\services.cmd" "' + DirB + '" install >> "' + InstallLog + '" 2>&1"', False);
    if RunOk then Break;
    if TryCount = 1 then begin
      Log('WARN 服务注册第 1 次执行失败，3 秒后重试（幂等，已注册项自动跳过）');
      Sleep(3000);
    end;
  end;
  if not RunOk then begin
    Notify('服务注册/启动失败，请查看日志：' + InstallLog);
    Exit;
  end;

  Log('== 健康检查（HTTPS /api/v1/system/health） ==');
  HealthOk := False;
  for TryCount := 1 to 30 do begin
    if CurlInstalled then begin
      // Exec 只表示 curl 进程跑完，必须校验退出码与响应体（API-S01：body 含 status=UP）
      HealthOk := Exec(ExpandConstant('{sys}\curl.exe'), '-sk --max-time 5 -o "' + DirB
        + '\logs\health.json" https://127.0.0.1:' + HttpsPort + '/api/v1/system/health',
        '', SW_HIDE, ewWaitUntilTerminated, Res)
        and (Res = 0)
        and LoadStringFromFile(DirB + '\logs\health.json', HealthBody)
        and (Pos('UP', HealthBody) > 0);
    end;
    if HealthOk then Break;
    Sleep(2000);
  end;
  if HealthOk then
    Notify('安装完成！请访问 https://127.0.0.1:' + HttpsPort + '/'#13#10#13#10
      + '登录账号：admin（口令为安装时设置的管理员初始口令）'#13#10
      + '移动端（巡检 H5）需先安装内网 CA 证书：' + DirB + '\nginx\conf\certs\ca.crt')
  else
    Notify('服务已注册但健康检查未通过（服务可能仍在启动），请稍后访问 https://127.0.0.1:'
      + HttpsPort + '/ 或查看日志。');
end;

// ---------- 卸载 ----------
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var DirB: string; Res: Integer;
begin
  if CurUninstallStep = usUninstall then begin
    // 服务注销必须在文件删除前执行（usPostUninstall 时 WinSW exe/xml 已被移除）
    DirB := ExpandConstant('{app}');
    Exec(ExpandConstant('{cmd}'), '/C ""' + DirB + '\scripts\services.cmd" "' + DirB + '" uninstall >nul 2>&1"',
      '', SW_HIDE, ewWaitUntilTerminated, Res);
  end;
  if CurUninstallStep = usPostUninstall then begin
    DirB := ExpandConstant('{app}');
    if MsgBox('是否保留数据库数据与上传文件（' + DirB + '\mysql\data、' + DirB + '\data）？'#13#10
      + '选"是"保留（推荐，便于重装恢复）；选"否"彻底删除。', mbConfirmation, MB_YESNO) = IDNO then begin
      DelTree(DirB + '\mysql\data', True, True, True);
      DelTree(DirB + '\data', True, True, True);
      DelTree(DirB + '\backup', True, True, True);
    end;
  end;
end;
