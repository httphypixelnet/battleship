param(
    [string]$ProjectRoot = ".",
    [string]$SourceDir = "src/main/",
    [string]$OutputFile = "build\all-kotlin-sources.kt"
)

$projectRootPath = (Resolve-Path -Path $ProjectRoot).Path
$sourcePath = Join-Path -Path $projectRootPath -ChildPath $SourceDir
$outputPath = Join-Path -Path $projectRootPath -ChildPath $OutputFile

if (-not (Test-Path -Path $sourcePath)) {
    throw "Source directory not found: $sourcePath"
}

$outputDirectory = Split-Path -Path $outputPath -Parent
if (-not (Test-Path -Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$resolvedOutputPath = [System.IO.Path]::GetFullPath($outputPath)
$kotlinFiles = Get-ChildItem -Path $sourcePath -Recurse -File -Filter "*.kt" |
    Where-Object { [System.IO.Path]::GetFullPath($_.FullName) -ne $resolvedOutputPath } |
    Sort-Object -Property FullName

if ($kotlinFiles.Count -eq 0) {
    throw "No Kotlin files found under $sourcePath"
}

$writer = [System.IO.StreamWriter]::new($resolvedOutputPath, $false, [System.Text.Encoding]::UTF8)
try {
    $s = "// External Libraries Used:
//
// JmDNS (Java Multicast DNS) - for network service discovery
// Source: https://github.com/jmdns/jmdns
//
// Gson (Google JSON library) - for JSON parsing and serialization
// Source: https://github.com/google/gson
//
// JavaFX - used for building the graphical user interface
// Source: https://openjfx.io/
//
// Note: All other code in this file is my own unless otherwise specified.
".Split("\n")
    $s | ForEach-Object { $writer.WriteLine($_)}
    foreach ($file in $kotlinFiles) {
        $relativePath = [System.IO.Path]::GetRelativePath($projectRootPath, $file.FullName)

        $writer.WriteLine("// ===== BEGIN FILE: $relativePath =====")
        [System.IO.File]::ReadLines($file.FullName) | ForEach-Object { $writer.WriteLine($_) }
        $writer.WriteLine("// ===== END FILE: $relativePath =====")
        $writer.WriteLine()
    }
}
finally {
    $writer.Dispose()
}

Write-Host "Combined $($kotlinFiles.Count) Kotlin files into $resolvedOutputPath"
