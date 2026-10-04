@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "DATA=%~dp0data"
set "DB=%DATA%\design_db.mv.db"
set "BASELINE=%DATA%\design_db.mv.db.bak"
set "BACKUP_DIR=%TEMP%\DesignMD-Demo-backup"

echo ============================================================
echo    停止 DESIGN.md 离线演示服务（并复位演示数据）
echo ============================================================
echo.

rem ---------- [1/4] 停止演示服务进程 ----------
echo [1/4] 停止正在运行的演示服务...
powershell -NoProfile -Command "$demo = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*design-md-platform.jar*' }; $found = $false; foreach($p in $demo){ $found = $true; try{ Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; Write-Host ('  已停止演示服务进程 PID=' + $p.ProcessId) } catch { Write-Host ('  无法停止进程 PID=' + $p.ProcessId + '，请手动结束') } }; if(-not $found){ $ls = Get-NetTCPConnection -LocalPort 8080,8090 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique; if($ls){ foreach($procId in $ls){ try{ Stop-Process -Id $procId -Force -ErrorAction Stop; Write-Host ('  已停止占用 8080/8090 端口的进程 PID=' + $procId + '（未识别为演示程序，可能为其它软件）') } catch { Write-Host ('  无法停止进程 PID=' + $procId + '，请手动结束') } } } else { Write-Host '  未发现正在运行的演示服务。' } }"

rem ---- 等待端口释放（最多约 30 秒）----
set /a W=0
:WAITCHECK
set /a W+=1
if %W% GTR 15 goto WAITTIMEOUT
ping -n 2 127.0.0.1 >nul
powershell -NoProfile -Command "$ls = Get-NetTCPConnection -LocalPort 8080,8090 -State Listen -ErrorAction SilentlyContinue; if($ls){exit 1}else{exit 0}"
if errorlevel 1 goto WAITCHECK
goto WAITDONE

:WAITTIMEOUT
echo   [警告] 服务进程未在预期时间内释放端口，本次跳过数据复位。
echo          请关闭演示服务窗口后重新双击本脚本。
goto ENDPAUSE

:WAITDONE
echo   演示服务已停止。
echo   提示：直接关闭标题为「DESIGN.md 演示服务」的窗口也能停止服务，但不会复位数据。
echo.

rem ---------- [2/4] 备份当前演示数据到系统临时目录 ----------
echo [2/4] 备份当前演示数据到系统临时目录...
if not exist "%DB%" goto NO_DB
if not exist "%BASELINE%" goto NO_BASELINE
powershell -NoProfile -Command "$db='%DB%'; $bak='%BASELINE%'; $dir='%BACKUP_DIR%'; if((Get-FileHash -Algorithm MD5 $db).Hash -eq (Get-FileHash -Algorithm MD5 $bak).Hash){ Write-Host '  演示数据与初始状态一致，无需备份。'; exit 0 }; if(Test-Path $dir){ Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue }; New-Item -ItemType Directory -Force -Path $dir | Out-Null; Copy-Item $db (Join-Path $dir 'design_db.mv.db') -Force; Write-Host ('  已将本次演示数据备份到：' + (Join-Path $dir 'design_db.mv.db'))"
goto BACKUP_DONE

:NO_DB
echo   未发现 data\design_db.mv.db ，跳过备份。
goto BACKUP_DONE

:NO_BASELINE
echo   [警告] 未找到初始基线文件 data\design_db.mv.db.bak ，本次跳过备份与复位。
echo          请重新解压一份演示包。
goto ENDPAUSE

:BACKUP_DONE
echo.

rem ---------- [3/4] 清理 data 目录运行期残留 ----------
echo [3/4] 清理 data 目录运行期残留...
del /q "%DATA%\*.lock.db" >nul 2>&1
del /q "%DATA%\*.trace.db" >nul 2>&1
del /q "%DATA%\*.temp" >nul 2>&1
echo   运行期残留（锁文件等）已清理。
echo.

rem ---------- [4/4] 还原初始演示数据并校验 ----------
echo [4/4] 将演示数据库还原为初始状态...
copy /y "%BASELINE%" "%DB%" >nul
powershell -NoProfile -Command "$db='%DB%'; $bak='%BASELINE%'; if((Test-Path $db) -and (Test-Path $bak)){ if((Get-FileHash -Algorithm MD5 $db).Hash -eq (Get-FileHash -Algorithm MD5 $bak).Hash){ Write-Host '  演示数据库已复位为初始状态。' } else { Write-Host '  [警告] 数据库复位校验未通过，请重新解压演示包。' } }"
echo.

echo ============================================================
echo    复位完成：演示包已回到初始状态
echo ============================================================
echo    - 演示服务已停止，8080/8090 端口已释放
echo    - data 目录运行期残留已清理，演示数据库已还原为初始基线
echo    - 上次演示数据备份（仅在有变化时生成，仅保留最近一份）：
echo        %BACKUP_DIR%\design_db.mv.db
echo.
echo    说明：uploads 目录中演示期间上传的 LOGO / DESIGN.md 文件不会被清除，
echo          如需彻底回到初始状态，可重新解压一份演示包。
echo    下次演示直接双击「启动演示.bat」即可。
echo.
goto ENDPAUSE

:ENDPAUSE
pause
exit /b 0
