@echo off
chcp 936 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set "PROJ=%~dp0"
if "%PROJ:~-1%"=="\" set "PROJ=%PROJ:~0,-1%"
title DESIGN.md 离线演示包 - 一键重新打包
set "PYTHONIOENCODING=gbk:replace"

rem 打包开始时间（Unix 秒，用于总耗时统计）
for /f %%i in ('powershell -NoProfile -Command "[int]((Get-Date) - (Get-Date '1970-01-01Z')).TotalSeconds"') do set "T0=%%i"

rem ==================== 可配置项（路径变化时改这里） ====================
rem 以下为本机默认路径，换到其他机器时可用同名环境变量覆盖，无需改脚本
set "JDK=%JAVA_HOME%"
if defined JAVA_HOME set "JDK=%JAVA_HOME%"
if defined DESIGNMD_JDK set "JDK=%DESIGNMD_JDK%"
set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
if defined MAVEN_HOME set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
if defined DESIGNMD_MVN set "MVN=%DESIGNMD_MVN%"
set "MYSQL_EXE=%MYSQL_HOME%\bin\mysql.exe"
if defined MYSQL_HOME set "MYSQL_EXE=%MYSQL_HOME%\bin\mysql.exe"
set "MYSQL_USER=root"
if defined DESIGNMD_MYSQL_USER set "MYSQL_USER=%DESIGNMD_MYSQL_USER%"
if not defined MYSQL_PWD (
    if defined DESIGNMD_MYSQL_PWD set "MYSQL_PWD=%DESIGNMD_MYSQL_PWD%"
)
if not defined MYSQL_PWD (
    echo [错误] 未设置 MySQL 密码：请先设置环境变量 DESIGNMD_MYSQL_PWD 后重新运行。
    exit /b 1
)
set "MYSQL_DB=design_db"
set "H2_JAR=%USERPROFILE%\.m2\repository\com\h2database\h2\2.4.240\h2-2.4.240.jar"
set "NODE_DIR=%NODE_HOME%"
if defined NODE_HOME set "NODE_DIR=%NODE_HOME%"
set "DEMO_DIR=%USERPROFILE%\Desktop\DesignMD-Demo"
set "DEMO_ZIP=%USERPROFILE%\Desktop\DesignMD-Demo.zip"
rem ======================================================================

set "TOOLS=%PROJ%\tools"
set "RUNTIME=%TOOLS%\runtime21"
set "BUILD=%PROJ%\build"
set "PROJ_OUT=%PROJ%\output"

echo ============================================================
echo    DESIGN.md 离线演示包 - 一键重新打包
echo ============================================================
echo    项目目录 : %PROJ%
echo    演示包   : %DEMO_DIR%
echo    压缩包   : %DEMO_ZIP%
echo    说明     : 本脚本会把当前代码与 MySQL 最新数据重新构建并同步到演示包
echo ------------------------------------------------------------
echo.

rem ---------- [1/7] 环境检查 ----------
echo [1/7] 检查构建环境...

set "PY="
if defined DESIGNMD_PYTHON set "PY=%DESIGNMD_PYTHON%"
if not defined PY (
    where python >nul 2>&1 && set "PY=python"
)
if not defined PY (
    for /f "delims=" %%i in ('powershell -NoProfile -Command "$p = Get-ChildItem \"$env:USERPROFILE\Desktop\Marvis\MarvisAgent\*\runtime\python311\python.exe\" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1; if($p){$p.FullName}"') do set "PY=%%i"
)

if not defined PY (
    echo   [错误] 未找到 Python 运行环境。
    echo          请安装 Python 并加入 PATH，或先执行：set DESIGNMD_PYTHON=python.exe的完整路径
    goto :FAIL
)
if not exist "%JDK%\bin\java.exe" (
    echo   [错误] 未找到 JDK21：%JDK%
    echo          请确认路径，或修改本脚本顶部的 JDK 配置项。
    goto :FAIL
)
if exist "%NODE_DIR%\npm.cmd" set "PATH=%NODE_DIR%;%PATH%"
where npm >nul 2>&1
if errorlevel 1 (
    echo   [错误] 未找到 npm，请确认已安装 Node.js。
    goto :FAIL
)
if not exist "%MVN%" (
    echo   [错误] 未找到 Maven：%MVN%
    goto :FAIL
)
if not exist "%MYSQL_EXE%" (
    echo   [错误] 未找到 MySQL 客户端：%MYSQL_EXE%
    goto :FAIL
)
if not exist "%H2_JAR%" (
    echo   [错误] 未找到 H2 数据库驱动：%H2_JAR%
    goto :FAIL
)
if not exist "%TOOLS%\pack_demo.py" (
    echo   [错误] 未找到打包脚本：%TOOLS%\pack_demo.py
    goto :FAIL
)

"%MYSQL_EXE%" -u%MYSQL_USER% -p%MYSQL_PWD% -e "SELECT 1;" %MYSQL_DB% >nul 2>&1
if errorlevel 1 (
    echo   [错误] 无法连接本机 MySQL 数据库 %MYSQL_DB% 。
    echo          请先启动 MySQL 服务并确认账号 %MYSQL_USER% / 密码是否正确。
    goto :FAIL
)
echo   环境检查通过。
echo.

rem ---------- [2/7] 停止正在运行的演示服务 ----------
echo [2/7] 停止可能正在运行的演示服务...
powershell -NoProfile -Command "$ps = Get-NetTCPConnection -LocalPort 8080,8090 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique; if($ps){ foreach($procId in $ps){ try{ Stop-Process -Id $procId -Force -ErrorAction Stop; Write-Host ('  [已停止] 演示服务进程 PID=' + $procId) } catch { Write-Host ('  [警告] 无法停止进程 PID=' + $procId + '，请手动结束') } } } else { Write-Host '  未发现运行中的演示服务。' }"
ping -n 3 127.0.0.1 >nul 2>&1
echo.

rem ---------- [3/7] 前端构建 ----------
echo [3/7] 构建前端（npm run build）...
pushd "%PROJ%\frontend"
if not exist "node_modules" (
    echo   首次打包需要安装前端依赖，请稍候...
    call npm install
    if errorlevel 1 (
        popd
        echo   [错误] npm install 失败，请检查 Node.js 环境。
        goto :FAIL
    )
)
call npm run build
if errorlevel 1 (
    popd
    echo   [错误] 前端构建失败（npm run build）。
    goto :FAIL
)
popd
if not exist "%PROJ%\frontend\dist\index.html" (
    echo   [错误] 未找到前端构建产物 frontend\dist\index.html 。
    goto :FAIL
)
echo   前端构建完成。
echo.

rem ---------- [4/7] 后端构建 ----------
echo [4/7] 构建后端（JDK21 + Maven，跳过测试）...
set "JAVA_HOME=%JDK%"
pushd "%PROJ%\backend"
set "MAVEN_OPTS=-Dfile.encoding=GBK"
call "%MVN%" clean package -DskipTests
if errorlevel 1 (
    popd
    echo   [错误] 后端构建失败，请查看上方 Maven 输出。
    goto :FAIL
)
popd
if not exist "%PROJ%\backend\target\design-md-platform-1.0.0.jar" (
    echo   [错误] 未生成后端程序包 backend\target\design-md-platform-1.0.0.jar 。
    goto :FAIL
)
echo   后端构建完成。
echo.

rem ---------- [5/7] 导出 MySQL 数据并生成演示数据库 ----------
echo [5/7] 从 MySQL 导出最新数据并生成演示数据库文件...
if not exist "%BUILD%" mkdir "%BUILD%"
"%PY%" "%TOOLS%\export_mysql_to_h2.py" --out "%BUILD%\h2init" --mysql "%MYSQL_EXE%" --user %MYSQL_USER% --password %MYSQL_PWD% --db %MYSQL_DB%
if errorlevel 1 (
    echo   [错误] 数据导出失败（常见原因：MySQL 未启动、账号密码不符、目标表不存在）。
    goto :FAIL
)

"%JDK%\bin\javac.exe" -encoding UTF-8 -cp "%H2_JAR%" -d "%BUILD%\h2tool" "%TOOLS%\H2Init.java"
if errorlevel 1 (
    echo   [错误] 编译 H2 导入工具失败。
    goto :FAIL
)

set "DBFILE=%BUILD%\design_db.mv.db"
rem 生成前先清掉上次遗留的同名数据库文件，避免数据重复写入
if exist "%DBFILE%" del /q "%DBFILE%"
set "DBURL=%BUILD:\=/%/design_db"
"%JDK%\bin\java.exe" -cp "%H2_JAR%;%BUILD%\h2tool;%PROJ%\backend\target\classes" H2Init "jdbc:h2:file:%DBURL%;NON_KEYWORDS=USER" "%BUILD%\h2init\schema.sql" "%BUILD%\h2init\data.sql"
if errorlevel 1 (
    echo   [错误] 生成 H2 演示数据库失败。
    goto :FAIL
)
if not exist "%DBFILE%" (
    echo   [错误] 未生成 H2 数据库文件：%DBFILE%
    goto :FAIL
)
echo   演示数据库生成完成。
echo.

rem ---------- [6/7] 准备精简运行时 ----------
echo [6/7] 准备内置精简 Java 运行时...
if not exist "%RUNTIME%\bin\java.exe" (
    if exist "%DEMO_DIR%\runtime\bin\java.exe" (
        echo   从现有演示包复制精简运行时（仅首次）...
        xcopy "%DEMO_DIR%\runtime" "%RUNTIME%" /E /I /Q /Y >nul
    ) else (
        echo   首次生成精简运行时，约需 1 分钟...
        "%JDK%\bin\jlink.exe" --add-modules java.base,java.datatransfer,java.xml,java.prefs,java.desktop,java.instrument,java.logging,java.management,java.security.sasl,java.naming,java.net.http,java.security.jgss,java.transaction.xa,java.sql,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,jdk.zipfs --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "%RUNTIME%"
    )
)
if not exist "%RUNTIME%\bin\java.exe" (
    echo   [错误] 精简运行时准备失败：%RUNTIME%
    goto :FAIL
)
echo   精简运行时就绪。
echo.

rem ---------- [7/7] 组装演示包并输出 ----------
echo [7/7] 组装演示包并覆盖输出...
set "EXTRA_ARG=--extra-output "%PROJ_OUT%""
if defined DESIGNMD_EXTRA_OUT set "EXTRA_ARG=!EXTRA_ARG! --extra-output "%DESIGNMD_EXTRA_OUT%""
"%PY%" "%TOOLS%\pack_demo.py" --project "%PROJ%" --work "%BUILD%" --template "%TOOLS%\demo-package" --jar "%PROJ%\backend\target\design-md-platform-1.0.0.jar" --runtime "%RUNTIME%" --data-db "%DBFILE%" --demo-dir "%DEMO_DIR%" --zip "%DEMO_ZIP%" !EXTRA_ARG!
if errorlevel 1 (
    echo   [错误] 组装演示包失败，请查看上方输出。
    goto :FAIL
)

rem ---------- [8/8] 冒烟测试（Playwright E2E，环境缺失则跳过不阻塞） ----------
echo [8/8] 冒烟测试（检测 Playwright + Chromium 环境）...
"%PY%" -c "import playwright" >nul 2>&1
if errorlevel 1 (
    echo   [跳过] 未检测到 Python Playwright 模块，跳过 E2E 冒烟（不阻塞打包）。
    echo.
    goto E2E_SKIPPED
)
set "E2E_CHROME=%USERPROFILE%\AppData\Local\ms-playwright\chromium-1243\chrome-win64\chrome.exe"
if not exist "%E2E_CHROME%" (
    echo   [跳过] 未检测到 Chromium 内核：%E2E_CHROME%
    echo         跳过 E2E 冒烟（不阻塞打包）。
    echo.
    goto E2E_SKIPPED
)
echo   检测到 Playwright + Chromium，将对新演示包执行 6 步冒烟测试。
echo.
pushd "%DEMO_DIR%"
echo   [E2E] 启动演示服务（8090 端口，cwd=Demo 目录）...
start "DesignMD-E2E" "%DEMO_DIR%\runtime\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%DEMO_DIR%\app\design-md-platform.jar" --spring.profiles.active=demo --server.port=8090
set /a E2E_WAIT=0
:E2E_WAIT_LOOP
set /a E2E_WAIT+=1
if !E2E_WAIT! GTR 120 goto E2E_START_TIMEOUT
ping -n 2 127.0.0.1 >nul 2>&1
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('127.0.0.1',8090);$c.Close();exit 0}catch{exit 1}" >nul 2>&1
if errorlevel 1 goto E2E_WAIT_LOOP
echo   [E2E] 演示服务已就绪，开始冒烟测试（tools\e2e\test_smoke.py，共 6 步）...
"%PY%" "%PROJ%\tools\e2e\test_smoke.py" --base http://127.0.0.1:8090
set "E2E_EXIT=%ERRORLEVEL%"

rem 无论成败都停止演示服务并复位演示数据库，保证三处文件一致性
echo   [E2E] 停止演示服务并复位演示数据库...
powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*design-md-platform.jar*' }; foreach($x in $p){ try{ Stop-Process -Id $x.ProcessId -Force -ErrorAction Stop } catch {} }"
ping -n 3 127.0.0.1 >nul 2>&1
if exist "%DEMO_DIR%\data\design_db.mv.db.bak" (
    copy /y "%DEMO_DIR%\data\design_db.mv.db.bak" "%DEMO_DIR%\data\design_db.mv.db" >nul
)
del /q "%DEMO_DIR%\data\*.lock.db" >nul 2>&1
del /q "%DEMO_DIR%\data\*.trace.db" >nul 2>&1
del /q "%DEMO_DIR%\data\*.temp" >nul 2>&1
popd
if not "%E2E_EXIT%"=="0" goto FAIL_E2E
echo   [E2E] 冒烟测试通过：6/6。
echo.
goto E2E_DONE

:E2E_START_TIMEOUT
echo   [错误] 演示服务启动超时（120 秒），无法执行 E2E 冒烟。
powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*design-md-platform.jar*' }; foreach($x in $p){ try{ Stop-Process -Id $x.ProcessId -Force -ErrorAction Stop } catch {} }"
if exist "%DEMO_DIR%\data\design_db.mv.db.bak" (
    copy /y "%DEMO_DIR%\data\design_db.mv.db.bak" "%DEMO_DIR%\data\design_db.mv.db" >nul
)
del /q "%DEMO_DIR%\data\*.lock.db" >nul 2>&1
del /q "%DEMO_DIR%\data\*.trace.db" >nul 2>&1
del /q "%DEMO_DIR%\data\*.temp" >nul 2>&1
popd
goto FAIL_E2E

:E2E_SKIPPED
echo.
goto E2E_DONE

:E2E_DONE

echo.
echo ============================================================
echo    [完成] 演示包已更新到最新构建
echo ============================================================
echo    文件夹 : %DEMO_DIR%
echo    压缩包 : %DEMO_ZIP%
echo    输出副本 : %PROJ_OUT%
for /f %%i in ('powershell -NoProfile -Command "[int]((Get-Date) - (Get-Date '1970-01-01Z')).TotalSeconds"') do set "T1=%%i"
set /a ELAPSED=T1-T0
echo    本次打包总耗时 : %ELAPSED% 秒（含 E2E 冒烟，计时口径 2026-09-25）
echo.
echo    验证方式：双击 %DEMO_DIR%\启动演示.bat 即可查看最新效果；
echo              也可把压缩包拷给别人，解压后双击启动演示.bat 使用。
echo.
pause
exit /b 0

:FAIL_E2E
echo.
echo ============================================================
echo    [失败] 演示包已生成，但 E2E 冒烟测试未通过
echo ============================================================
echo    桌面文件夹 / 桌面 zip / 项目 output 三处仍为本次新构建内容；
echo    演示数据库已复位为初始基线，可重新双击「启动演示.bat」人工复核。
echo    请查看上方 E2E 失败步骤与 tools\e2e\shots 下的截图定位问题，
echo    修复后重新双击本脚本重打。
echo.
pause
exit /b 1

:FAIL
echo.
echo ============================================================
echo    [失败] 本次打包已中止
echo ============================================================
echo    桌面上的旧演示包在此步骤之前保持原样，未被破坏；
echo    请根据上方 [错误] 提示排查后重新双击本脚本。
echo.
pause
exit /b 1
