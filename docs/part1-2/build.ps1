param(
    [string]$XeLaTeX = 'xelatex'
)

$ErrorActionPreference = 'Stop'
$compiler = Get-Command $XeLaTeX -CommandType Application -ErrorAction Stop
$buildDirectory = Join-Path $PSScriptRoot '.latex-build'
[IO.Directory]::CreateDirectory($buildDirectory) | Out-Null

for ($pass = 1; $pass -le 3; $pass++) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $compiler.Source
    $startInfo.Arguments = '-interaction=nonstopmode -halt-on-error -file-line-error -no-shell-escape -synctex=1 -output-directory=.latex-build report.tex'
    $startInfo.WorkingDirectory = $PSScriptRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($startInfo)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $log = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        $logPath = Join-Path $buildDirectory "pass-$pass.txt"
        [IO.File]::WriteAllText($logPath, $log, [Text.UTF8Encoding]::new($false))
        if ($process.ExitCode -ne 0) {
            throw "XeLaTeX failed on pass $pass. See $logPath`n$log"
        }
        Write-Output "XeLaTeX: pass $pass/3 completed"
    } finally {
        $process.Dispose()
    }
}

$finalLog = [IO.File]::ReadAllText((Join-Path $buildDirectory 'report.log'))
if ($finalLog -match 'Overfull \\[hv]box|Missing character:|ignored error:|There were undefined references|Rerun to get cross-references right|Table widths have changed|Label\(s\) may have changed') {
    throw "Check the PDF layout or references in $buildDirectory\report.log. The previous report.pdf was preserved."
}

Copy-Item -LiteralPath (Join-Path $buildDirectory 'report.pdf') -Destination (Join-Path $PSScriptRoot 'report.pdf') -Force
Write-Output "PDF: $PSScriptRoot\report.pdf"
