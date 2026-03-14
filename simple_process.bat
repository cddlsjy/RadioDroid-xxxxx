@echo off

rem 合并文件
type D:\direct-list.txt > D:\cn.conf
type D:\apple-cn.txt >> D:\cn.conf
type D:\google-cn.txt >> D:\cn.conf

echo 文件合并完成

rem 创建临时文件
type nul > D:\temp.txt

rem 处理每一行
for /f "tokens=* delims=" %%a in ('findstr /v "^regexp:" D:\cn.conf') do (
    set "line=%%a"
    if not "%%a"=="" (
        setlocal enabledelayedexpansion
        set "line=!line:full:=!"
        if not "!line!"=="" (
            echo nameserver /!line!/cn >> D:\temp.txt
        )
        endlocal
    )
)

rem 替换原文件
move /y D:\temp.txt D:\cn.conf

echo 处理完成！

rem 显示前5行内容
echo 前5行内容：
type D:\cn.conf | head -5

echo 完成！
