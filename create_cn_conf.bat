@echo off

set "cnConfPath=D:\cn.conf"

REM 清空现有文件（如果存在）
if exist "%cnConfPath%" (
    echo 清空现有cn.conf文件...
    type nul > "%cnConfPath%"
)

REM 下载direct-list.txt并添加到cn.conf
echo 正在下载 direct-list.txt...
powershell -Command "Invoke-WebRequest -Uri 'https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/direct-list.txt' -UseBasicParsing | Select-Object -ExpandProperty Content | Add-Content -Path '%cnConfPath%'"
echo direct-list.txt 下载完成

REM 下载apple-cn.txt并添加到cn.conf
echo 正在下载 apple-cn.txt...
powershell -Command "Invoke-WebRequest -Uri 'https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/apple-cn.txt' -UseBasicParsing | Select-Object -ExpandProperty Content | Add-Content -Path '%cnConfPath%'"
echo apple-cn.txt 下载完成

REM 下载google-cn.txt并添加到cn.conf
echo 正在下载 google-cn.txt...
powershell -Command "Invoke-WebRequest -Uri 'https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/google-cn.txt' -UseBasicParsing | Select-Object -ExpandProperty Content | Add-Content -Path '%cnConfPath%'"
echo google-cn.txt 下载完成

REM 处理文件内容：去除full:前缀，删除regexp行，添加nameserver格式
echo 正在处理文件内容...
powershell -Command "$lines = Get-Content '%cnConfPath%'; $processedLines = @(); foreach ($line in $lines) { if (-not [string]::IsNullOrWhiteSpace($line)) { if (-not $line -match '^regexp:') { $processedLine = $line -replace '^full:', ''; if (-not [string]::IsNullOrWhiteSpace($processedLine)) { $processedLine = 'nameserver /' + $processedLine + '/cn'; $processedLines += $processedLine } } } }; $processedLines | Set-Content '%cnConfPath%'"
echo 文件处理完成！

echo cn.conf文件已创建在: %cnConfPath%
powershell -Command "$count = (Get-Content '%cnConfPath%' | Measure-Object).Count; Write-Host '文件包含' $count '行内容'"

pause
