[CmdletBinding()]
param(
    [ValidateSet("Staged", "Range", "Working")]
    [string]$DiffMode = "Working",

    [string]$BaseRef,

    [string]$HeadRef = "HEAD",

    [string[]]$ChangedPath,

    [switch]$RequireCompleteTasks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$hasExplicitPaths = $PSBoundParameters.ContainsKey("ChangedPath")
$resolvedRangeBase = $null

function Invoke-GitLines {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& git -C $repoRoot @Arguments 2>$null)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $exitCode."
    }

    return @($output | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
}

function Resolve-RangeBase {
    if ($BaseRef -and $BaseRef -notmatch '^0+$') {
        return $BaseRef
    }

    if ($BaseRef -and $BaseRef -match '^0+$') {
        @(& git -C $repoRoot rev-parse --verify "origin/main^{commit}" 2>$null) > $null
        if ($LASTEXITCODE -eq 0) {
            $mergeBase = Invoke-GitLines -Arguments @("merge-base", $HeadRef, "origin/main")
            return @($mergeBase)[0]
        }
    }

    $upstream = @(& git -C $repoRoot rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>$null)
    if ($LASTEXITCODE -eq 0 -and $upstream.Count -gt 0) {
        $mergeBase = Invoke-GitLines -Arguments @("merge-base", $HeadRef, $upstream[0].ToString().Trim())
        return @($mergeBase)[0]
    }

    @(& git -C $repoRoot rev-parse --verify "origin/main^{commit}" 2>$null) > $null
    if ($LASTEXITCODE -eq 0) {
        $mergeBase = Invoke-GitLines -Arguments @("merge-base", $HeadRef, "origin/main")
        return @($mergeBase)[0]
    }

    @(& git -C $repoRoot rev-parse --verify "$HeadRef^" 2>$null) > $null
    if ($LASTEXITCODE -eq 0) {
        return "$HeadRef^"
    }

    return $null
}

function Get-SnapshotFile {
    param([string]$Path)

    if ($DiffMode -eq "Working") {
        $absolutePath = Join-Path $repoRoot $Path
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
            return [pscustomobject]@{ Exists = $false; Content = $null }
        }

        return [pscustomobject]@{
            Exists = $true
            Content = Get-Content -LiteralPath $absolutePath -Raw
        }
    }

    $objectName = if ($DiffMode -eq "Staged") {
        ":$Path"
    } else {
        "$HeadRef`:$Path"
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& git -C $repoRoot show $objectName 2>$null)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        return [pscustomobject]@{ Exists = $false; Content = $null }
    }

    return [pscustomobject]@{
        Exists = $true
        Content = $output -join "`n"
    }
}

function Get-SnapshotPathsUnder {
    param([string]$Root)

    if ($DiffMode -eq "Working") {
        $absoluteRoot = Join-Path $repoRoot $Root
        if (-not (Test-Path -LiteralPath $absoluteRoot -PathType Container)) {
            return @()
        }

        return @(Get-ChildItem -LiteralPath $absoluteRoot -Recurse -File | ForEach-Object {
            $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
        })
    }

    if ($DiffMode -eq "Staged") {
        return Invoke-GitLines -Arguments @("ls-files", "--cached", "--", "$Root/")
    }

    return Invoke-GitLines -Arguments @("ls-tree", "-r", "--name-only", $HeadRef, "--", $Root)
}

function Test-PathInBaseSnapshot {
    param([string]$Path)

    $baseSnapshot = if ($DiffMode -eq "Range") {
        $resolvedRangeBase
    } else {
        "HEAD"
    }

    if (-not $baseSnapshot -or $baseSnapshot -match '^0+$') {
        return $false
    }

    $objectName = "$baseSnapshot`:$Path"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & git -C $repoRoot cat-file -e $objectName 2>$null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return $exitCode -eq 0
}

function Get-ChangedPaths {
    if ($hasExplicitPaths) {
        return @($ChangedPath)
    }

    if ($DiffMode -eq "Staged") {
        return Invoke-GitLines -Arguments @("diff", "--cached", "--name-only", "--diff-filter=ACMRD")
    }

    if ($DiffMode -eq "Working") {
        $tracked = Invoke-GitLines -Arguments @("diff", "HEAD", "--name-only", "--diff-filter=ACMRD")
        $untracked = Invoke-GitLines -Arguments @("ls-files", "--others", "--exclude-standard")
        return @($tracked + $untracked | Sort-Object -Unique)
    }

    $resolvedBase = Resolve-RangeBase
    $script:resolvedRangeBase = $resolvedBase
    if (-not $resolvedBase) {
        return Invoke-GitLines -Arguments @("diff-tree", "--root", "--no-commit-id", "--name-only", "-r", $HeadRef)
    }

    if ($resolvedBase -match '^0+$') {
        return Invoke-GitLines -Arguments @("diff-tree", "--root", "--no-commit-id", "--name-only", "-r", $HeadRef)
    }

    return Invoke-GitLines -Arguments @("diff", "--name-only", "--diff-filter=ACMRD", "$resolvedBase...$HeadRef")
}

$paths = @(Get-ChangedPaths | ForEach-Object {
    $_.Replace('\', '/').TrimStart([char[]]@('.', '/'))
} | Where-Object { $_ } | Sort-Object -Unique)

if ($paths.Count -eq 0) {
    Write-Host "OpenSpec coverage: no changed paths."
    exit 0
}

$baselineChanges = @($paths | Where-Object { $_ -match '^RT3/' })
if ($baselineChanges.Count -gt 0) {
    throw "RT3 is a read-only vendor baseline. Changed paths: $($baselineChanges -join ', ')"
}

$governedPatterns = @(
    '^android/',
    '^extension/',
    '^docs/api/',
    '^docs/testing/.*\.ps1$',
    '^scripts/',
    '^\.githooks/',
    '^\.github/workflows/',
    '^\.codex/',
    '^AGENTS\.md$',
    '^LLM-RULES\.md$',
    '^USER-RULES\.md$',
    '^package\.json$',
    '^package-lock\.json$',
    '^openspec/config\.yaml$'
)

if ($DiffMode -eq "Staged" -and -not $hasExplicitPaths) {
    $workingOnlyPaths = @(
        (Invoke-GitLines -Arguments @("diff", "--name-only", "--diff-filter=ACMRD")) +
        (Invoke-GitLines -Arguments @("ls-files", "--others", "--exclude-standard")) | Where-Object { $_ } | ForEach-Object {
            $_.Replace('\', '/').TrimStart([char[]]@('.', '/'))
        } | Sort-Object -Unique
    )
    $unstagedGoverned = @($workingOnlyPaths | Where-Object {
        $path = $_
        @($governedPatterns | Where-Object { $path -match $_ }).Count -gt 0
    })
    if ($unstagedGoverned.Count -gt 0) {
        throw "Governed working-tree changes must be staged together before commit: $($unstagedGoverned -join ', ')"
    }
}

$governedChanges = @($paths | Where-Object {
    $path = $_
    @($governedPatterns | Where-Object { $path -match $_ }).Count -gt 0
})

if ($governedChanges.Count -eq 0) {
    Write-Host "OpenSpec coverage: no governed changes."
    exit 0
}

$taskEvidence = @()
foreach ($path in @($paths | Where-Object { $_ -match '/tasks\.md$' })) {
    $kind = $null
    $root = $null

    if ($path -match '^openspec/changes/archive/(\d{4}-\d{2}-\d{2}-[^/]+)/tasks\.md$') {
        $kind = "archive"
        $root = "openspec/changes/archive/$($Matches[1])"
    } elseif ($path -match '^openspec/changes/([^/]+)/tasks\.md$') {
        $kind = "active"
        $root = "openspec/changes/$($Matches[1])"
    }

    if (-not $kind) {
        continue
    }

    $snapshot = Get-SnapshotFile -Path $path
    if ($snapshot.Exists) {
        $taskEvidence += [pscustomobject]@{
            Kind = $kind
            Root = $root
            TaskPath = $path
            Content = $snapshot.Content
        }
    }
}

$taskEvidence = @($taskEvidence | Sort-Object -Property Root -Unique)
if ($taskEvidence.Count -eq 0) {
    throw "Governed changes require an active or newly archived OpenSpec tasks.md update in the same diff: $($governedChanges -join ', ')"
}
if ($taskEvidence.Count -gt 1) {
    throw "A governed diff must be tied to exactly one OpenSpec change; found: $($taskEvidence.Root -join ', ')"
}

$evidence = $taskEvidence[0]
foreach ($artifactName in @("proposal.md", "design.md", "tasks.md")) {
    $artifactPath = "$($evidence.Root)/$artifactName"
    if (-not (Get-SnapshotFile -Path $artifactPath).Exists) {
        throw "OpenSpec evidence is incomplete in the selected snapshot: $artifactPath"
    }
}

$deltaSpecPaths = @(Get-SnapshotPathsUnder -Root "$($evidence.Root)/specs" | Where-Object {
    $_.Replace('\', '/') -match '/specs/[^/]+/spec\.md$'
})
if ($deltaSpecPaths.Count -eq 0) {
    throw "OpenSpec evidence has no delta specification: $($evidence.Root)/specs/**/spec.md"
}

$mainSpecChanges = @($paths | Where-Object { $_ -match '^openspec/specs/[^/]+/spec\.md$' })
if ($evidence.Kind -eq "active" -and $mainSpecChanges.Count -gt 0) {
    throw "Do not edit current specs while using active change evidence; synchronize them through archive: $($mainSpecChanges -join ', ')"
}

if ($evidence.Kind -eq "archive") {
    if (Test-PathInBaseSnapshot -Path $evidence.TaskPath) {
        throw "Existing OpenSpec archives are immutable and cannot provide evidence for new governed changes: $($evidence.Root)"
    }

    $archiveName = Split-Path -Leaf $evidence.Root
    $changeName = $archiveName.Substring(11)
    $activeTaskPath = "openspec/changes/$changeName/tasks.md"
    if ((Get-SnapshotFile -Path $activeTaskPath).Exists) {
        throw "Archived and active copies of the same OpenSpec change cannot coexist: $changeName"
    }

    $requiredMainSpecs = @($deltaSpecPaths | ForEach-Object {
        $normalized = $_.Replace('\', '/')
        if ($normalized -match '/specs/([^/]+)/spec\.md$') {
            "openspec/specs/$($Matches[1])/spec.md"
        }
    } | Sort-Object -Unique)
    $missingMainSpecs = @($requiredMainSpecs | Where-Object { $_ -notin $mainSpecChanges })
    if ($missingMainSpecs.Count -gt 0) {
        throw "Archived OpenSpec evidence requires synchronized current specs in the same diff: $($missingMainSpecs -join ', ')"
    }
}

$incompleteCount = [regex]::Matches($evidence.Content, '(?m)^\s*-\s+\[ \]').Count
if (($RequireCompleteTasks -or $evidence.Kind -eq "archive") -and $incompleteCount -gt 0) {
    throw "OpenSpec tasks are incomplete in $($evidence.TaskPath) ($incompleteCount unchecked)."
}

Write-Host "OpenSpec coverage: OK ($($governedChanges.Count) governed path(s), change '$($evidence.Root)')."
