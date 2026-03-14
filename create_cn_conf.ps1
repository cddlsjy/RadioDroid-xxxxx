# 创建cn.conf文件
$cnConfPath = "D:\cn.conf"

# 清空现有文件（如果存在）
if (Test-Path $cnConfPath) {
    Clear-Content $cnConfPath
}

# 下载direct-list.txt并添加到cn.conf
try {
    Write-Host "正在下载 direct-list.txt..."
    $directList = Invoke-WebRequest -Uri "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/direct-list.txt" -UseBasicParsing
    Add-Content $cnConfPath $directList.Content
    Write-Host "direct-list.txt 下载完成"
} catch {
    Write-Host "下载 direct-list.txt 失败: $_"
}

# 下载apple-cn.txt并添加到cn.conf
try {
    Write-Host "正在下载 apple-cn.txt..."
    $appleList = Invoke-WebRequest -Uri "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/apple-cn.txt" -UseBasicParsing
    Add-Content $cnConfPath $appleList.Content
    Write-Host "apple-cn.txt 下载完成"
} catch {
    Write-Host "下载 apple-cn.txt 失败: $_"
}

# 下载google-cn.txt并添加到cn.conf
try {
    Write-Host "正在下载 google-cn.txt..."
    $googleList = Invoke-WebRequest -Uri "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/google-cn.txt" -UseBasicParsing
    Add-Content $cnConfPath $googleList.Content
    Write-Host "google-cn.txt 下载完成"
} catch {
    Write-Host "下载 google-cn.txt 失败: $_"
}

# 处理文件内容：去除full:前缀，删除regexp行，添加nameserver格式
Write-Host "正在处理文件内容..."
$lines = Get-Content $cnConfPath
$processedLines = @()

foreach ($line in $lines) {
    # 跳过空行
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    
    # 跳过以regexp:开头的行
    if ($line -match "^regexp:") {
        continue
    }
    
    # 去除full:前缀
    $processedLine = $line -replace "^full:", ""
    
    # 跳过处理后为空的行
    if ([string]::IsNullOrWhiteSpace($processedLine)) {
        continue
    }
    
    # 添加nameserver格式
    $processedLine = "nameserver /$processedLine/cn"
    $processedLines += $processedLine
}

# 写回处理后的内容
$processedLines | Set-Content $cnConfPath

Write-Host "文件处理完成！"
Write-Host "cn.conf文件已创建在: $cnConfPath"
Write-Host "文件包含 $(($processedLines | Measure-Object).Count) 行内容"
