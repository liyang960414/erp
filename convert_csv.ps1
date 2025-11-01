# 转换CSV文件编码为UTF-8
$inputFile = "example\物料.csv"
$outputFile = "example\物料_utf8.csv"

# 尝试多种编码
$encodings = @('GBK', 'GB2312', 'GB18030', 'Big5', 'Default')

foreach ($enc in $encodings) {
    try {
        Write-Host "尝试使用编码: $enc"
        
        if ($enc -eq 'Default') {
            $content = Get-Content $inputFile -Raw -Encoding Default
        } else {
            $content = [System.IO.File]::ReadAllText($inputFile, [System.Text.Encoding]::GetEncoding($enc))
        }
        
        # 写入UTF-8格式（带BOM，Excel兼容）
        $utf8WithBom = New-Object System.Text.UTF8Encoding $true
        [System.IO.File]::WriteAllText($outputFile, $content, $utf8WithBom)
        
        Write-Host "成功转换！使用编码: $enc"
        Write-Host "输出文件: $outputFile"
        Write-Host ""
        Write-Host "前3行内容:"
        $lines = $content -split "`r?`n"
        for ($i = 0; $i -lt 3 -and $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            if ($line.Length -gt 100) {
                $line = $line.Substring(0, 100) + "..."
            }
            Write-Host "$($i+1): $line"
        }
        break
    } catch {
        Write-Host "使用 $enc 编码失败: $_" -ForegroundColor Yellow
        continue
    }
}

