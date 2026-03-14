# 合并并处理文件
$directList = Get-Content 'D:\direct-list.txt'
$appleList = Get-Content 'D:\apple-cn.txt'
$googleList = Get-Content 'D:\google-cn.txt'

# 合并所有行
$allLines = $directList + $appleList + $googleList

# 处理每一行
$processedLines = @()
foreach ($line in $allLines) {
    # 跳过空行
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    
    # 跳过以regexp:开头的行
    if ($line -match '^regexp:') {
        continue
    }
    
    # 去除full:前缀
    $line = $line -replace '^full:', ''
    
    # 跳过处理后为空的行
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    
    # 添加nameserver格式
    $processedLine = "nameserver /$line/cn"
    $processedLines += $processedLine
}

# 写入结果到cn.conf
$processedLines | Out-File 'D:\cn.conf' -Encoding utf8

# 显示结果
Write-Host "处理完成，生成 $($processedLines.Count) 行内容"
Write-Host "前5行内容："
$processedLines | Select-Object -First 5
