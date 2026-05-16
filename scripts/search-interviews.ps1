param(
    [string]$Position = "Java",
    [string]$Company = "ByteDance",
    [string[]]$Keywords = @("Spring", "Redis"),
    [int]$Size = 5
)

$ErrorActionPreference = "Stop"

chcp 65001 | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$body = @{
    position = $Position
    company = $Company
    keywords = $Keywords
    page = 1
    size = $Size
} | ConvertTo-Json -Depth 8

$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$response = Invoke-RestMethod `
    -Uri "http://127.0.0.1:8080/api/interview-experiences/search" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $bytes `
    -TimeoutSec 120

Write-Host ""
Write-Host "搜索关键词：$($response.query)"
Write-Host "结果数量：$($response.total)"
Write-Host ""

$index = 1
foreach ($item in $response.items | Select-Object -First $Size) {
    Write-Host "$index. $($item.title)"
    if ($item.publishedAt) {
        Write-Host "   时间：$($item.publishedAt)"
    }
    Write-Host "   链接：$($item.sourceUrl)"
    if ($item.highlights.Count -gt 0) {
        Write-Host "   摘要：$($item.highlights[0])"
    }
    Write-Host ""
    $index++
}
