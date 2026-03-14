@echo off

set "cnConfPath=D:\cn.conf"

REM 清空现有文件（如果存在）
if exist "%cnConfPath%" (
    echo 清空现有cn.conf文件...
    type nul > "%cnConfPath%"
)

REM 处理所有三个文件
echo 正在处理文件...

REM 处理direct-list.txt
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" "D:\direct-list.txt"') do (
    set "line=%%a"
    call :process_line
)

REM 处理apple-cn.txt
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" "D:\apple-cn.txt"') do (
    set "line=%%a"
    call :process_line
)

REM 处理google-cn.txt
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" "D:\google-cn.txt"') do (
    set "line=%%a"
    call :process_line
)

echo 文件处理完成！
echo cn.conf文件已创建在: %cnConfPath%

REM 显示文件的前几行内容
echo 显示文件前5行内容：
set "count=0"
for /f "tokens=* delims=" %%a in ('type "%cnConfPath%"') do (
    if !count! lss 5 (
        echo %%a
        set /a "count+=1"
    )
)

goto :eof

:process_line
REM 跳过空行
if "%line%"=="" goto :eof

REM 去除full:前缀
set "line=%line:full:=%"

REM 跳过空行
if "%line%"=="" goto :eof

REM 添加nameserver格式
echo nameserver /%line%/cn >> "%cnConfPath%"
goto :eof
