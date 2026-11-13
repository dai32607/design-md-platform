@echo off
rem 兼容入口：实际打包逻辑位于 scripts\pack-demo.bat（路径均由环境变量探测，无本机硬编码）
call "%~dp0scripts\pack-demo.bat" %*
