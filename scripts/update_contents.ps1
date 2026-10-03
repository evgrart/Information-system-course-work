$ErrorActionPreference = 'Stop'
$reportRoot = Split-Path -Parent $PSScriptRoot
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    foreach ($reportFolder in @('part1', 'part1-2')) {
        $reportPath = Join-Path $reportRoot "docs\$reportFolder\report.docx"
        $document = $word.Documents.Open($reportPath, $false, $false)
        try {
            $document.Repaginate()
            $document.Fields.Update() | Out-Null
            foreach ($contents in $document.TablesOfContents) { $contents.Update() }
            $document.Save()
        } finally {
            $document.Close(0)
            [System.Runtime.InteropServices.Marshal]::ReleaseComObject($document) | Out-Null
        }
    }
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}
