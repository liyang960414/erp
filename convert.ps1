$inputFile = "example\物料.csv"
$outputFile = "example\物料_utf8.csv"

# 尝试GB2312编码读取
try {
    $content = [System.IO.File]::ReadAllText($inputFile, [System.Text.Encoding]::GetEncoding('GB2312'))
    [System.IO.File]::WriteAllText($outputFile, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Successfully converted from GB2312 to UTF-8"
    Write-Host "Output file: $outputFile"
    Write-Host "First 200 characters:"
    $content.Substring(0, [Math]::Min(200, $content.Length))
} catch {
    Write-Host "GB2312 failed, trying GBK..."
    try {
        $content = [System.IO.File]::ReadAllText($inputFile, [System.Text.Encoding]::GetEncoding('GBK'))
        [System.IO.File]::WriteAllText($outputFile, $content, [System.Text.UTF8Encoding]::new($false))
        Write-Host "Successfully converted from GBK to UTF-8"
        Write-Host "Output file: $outputFile"
        Write-Host "First 200 characters:"
        $content.Substring(0, [Math]::Min(200, $content.Length))
    } catch {
        Write-Host "Conversion failed: $_"
    }
}

