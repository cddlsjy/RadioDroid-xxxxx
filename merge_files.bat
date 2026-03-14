@echo off

set "cnConfPath=D:\cn.conf"
set "directList=D:\direct-list.txt"
set "googleList=D:\google-cn.txt"
set "appleList=D:\apple-cn.txt"

REM 清空现有文件（如果存在）
if exist "%cnConfPath%" (
    echo 清空现有cn.conf文件...
    type nul > "%cnConfPath%"
)

REM 合并三个文件到cn.conf
echo 正在合并文件...
type "%directList%" >> "%cnConfPath%"
type "%appleList%" >> "%cnConfPath%"
type "%googleList%" >> "%cnConfPath%"
echo 文件合并完成

REM 处理文件内容：去除full:前缀，删除regexp行，添加nameserver格式
echo 正在处理文件内容...
powershell -Command "$lines = Get-Content '%cnConfPath%'; $processedLines = @(); foreach ($line in $lines) { if (-not [string]::IsNullOrWhiteSpace($line)) { if (-not $line -match '^regexp:') { $processedLine = $line -replace '^full:', ''; if (-not [string]::IsNullOrWhiteSpace($processedLine)) { $processedLine = 'nameserver /' + $processedLine + '/cn'; $processedLines += $processedLine } } } }; $processedLines | Set-Content '%cnConfPath%'"
echo 文件处理完成！

echo cn.conf文件已创建在: %cnConfPath%
powershell -Command "$count = (Get-Content '%cnConfPath%' | Measure-Object).Count; Write-Host '文件包含' $count '行内容'"

REM 显示文件的前几行内容，以验证处理结果
echo 显示文件前5行内容：
powershell -Command "Get-Content '%cnConfPath%' | Select-Object -First 5"

pause
