@echo off

set "cnConfPath=D:\cn.conf"

REM 清空现有文件（如果存在）
if exist "%cnConfPath%" (
    type nul > "%cnConfPath%"
)

REM 处理direct-list.txt
echo 正在处理 direct-list.txt...
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" "D:\direct-list.txt"') do (
    set "line=%%a"
    setlocal enabledelayedexpansion
    set "line=!line:full:=!"
    if not "!line!"=="" (
        echo nameserver /!line!/cn >> "%cnConfPath%"
    )
    endlocal
)

REM 处理apple-cn.txt
echo 正在处理 apple-cn.txt...
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" "D:\apple-cn.txt"') do (
    set "line=%%a"
    setlocal enabledelayedexpansion
    set "line=!line:full:=!"
    if not "!line!"=="" (
        echo nameserver /!line!/cn >> "%cnConfPath%"
    )
    endlocal
)

REM 处理google-cn.txt
echo 正在处理 google-cn.txt...
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" "D:\google-cn.txt"') do (
    set "line=%%a"
    setlocal enabledelayedexpansion
    set "line=!line:full:=!"
    if not "!line!"=="" (
        echo nameserver /!line!/cn >> "%cnConfPath%"
    )
    endlocal
)

echo 文件处理完成！
echo 显示文件前5行内容：
head -5 "%cnConfPath%"
echo 文件已创建在: %cnConfPath%
