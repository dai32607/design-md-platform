@echo off
setlocal
cd /d "%~dp0"

set "JAVA=%~dp0runtime\bin\java.exe"
set "JAR=%~dp0app\design-md-platform.jar"
set "PORT=8080"

echo ============================================================
echo    DESIGN.md 设计规范平台 - Windows 离线演示
echo ============================================================
echo.

if not exist "%JAVA%" (
    echo [错误] 未找到内置 Java 运行环境：%JAVA%
    echo        请确认 Demo 文件夹结构完整（runtime、app、data、uploads 齐全）。
    echo.
    pause
    exit /b 1
)
if not exist "%JAR%" (
    echo [错误] 未找到程序文件：%JAR%
    echo.
    pause
    exit /b 1
)

rem ---- 检测 8080 端口是否被占用，占用则改用 8090 ----
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('127.0.0.1',8080);$c.Close();exit 1}catch{exit 0}" >nul 2>&1
if errorlevel 1 (
    set "PORT=8090"
    echo [提示] 8080 端口已被其他程序占用，演示服务将改用 8090 端口启动。
    echo.
)

echo    服务端口 : %PORT%
echo    访问地址 : http://localhost:%PORT%
echo    演示账号 : test（普通用户）/ admin（管理员），初始密码请向作者获取
echo.
echo    正在启动服务，请稍候（约 10 至 20 秒）...
echo    启动期间请勿关闭本窗口，以及随后弹出的服务窗口。
echo ------------------------------------------------------------

start "DESIGN.md 演示服务（关闭此窗口即停止服务）" "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=demo --server.port=%PORT%

rem ---- 等待服务就绪（最多 120 秒）----
set /a N=0
:WAIT
set /a N+=1
if %N% GTR 60 goto TIMEOUT
ping -n 2 127.0.0.1 >nul 2>&1
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('127.0.0.1',%PORT%);$c.Close();exit 0}catch{exit 1}" >nul 2>&1
if errorlevel 1 goto WAIT

echo.
echo    [成功] 服务已启动，正在打开浏览器：http://localhost:%PORT%
echo.
start "" "http://localhost:%PORT%"
echo    演示结束后，关闭标题为「DESIGN.md 演示服务」的窗口即可停止服务；
echo    也可以双击本目录下的「停止演示.bat」。
echo.
pause
exit /b 0

:TIMEOUT
echo.
echo    [错误] 服务启动超时（120 秒）。请查看服务窗口中的错误信息后重试。
echo          常见原因：data 目录被占用、杀毒软件拦截 java 进程。
echo.
pause
exit /b 1
