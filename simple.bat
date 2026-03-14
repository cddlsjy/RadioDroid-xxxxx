@echo off

rem 合并文件
type D:\direct-list.txt > D:\cn.conf
type D:\apple-cn.txt >> D:\cn.conf
type D:\google-cn.txt >> D:\cn.conf

rem 处理文件
powershell -Command "$lines = Get-Content 'D:\cn.conf'; $out = @(); foreach ($l in $lines) { if ($l -and $l -notmatch '^regexp:') { $l = $l -replace '^full:', ''; if ($l) { $out += 'nameserver /' + $l + '/cn' } } }; $out | Out-File 'D:\cn.conf' -Encoding utf8; '处理完成'"

echo 完成！
type D:\cn.conf | head -5
