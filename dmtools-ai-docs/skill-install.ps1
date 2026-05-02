# DMtools Agent Skill Installer for Windows PowerShell
# Works with Cursor, Claude, Codex, and any Agent Skills compatible system
# Installs to project-level directories (.cursor\skills, .claude\skills, .codex\skills)
# and to global ~\.claude\skills if ~\.claude exists (Claude Code / GitHub Copilot CLI)
#
# Usage:
#   irm https://github.com/epam/dm.ai/releases/latest/download/skill-install.ps1 | iex
#   # When piped (non-interactive): installs to ALL detected locations automatically
#
#   $env:DMTOOLS_SKILLS = "jira,github"; irm .../skill-install.ps1 | iex
#   $env:INSTALL_LOCATION = "1"; irm .../skill-install.ps1 | iex

$ErrorActionPreference = "Stop"

$GITHUB_REPO = "epam/dm.ai"
$TEMP_DIR = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), [System.Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $TEMP_DIR | Out-Null

function Write-Header {
    Write-Host ""
    Write-Host "╔════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║      DMtools Agent Skill Installer         ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Cyan
    Write-Host ""
}

function Write-Ok($msg) {
    Write-Host "✓ $msg" -ForegroundColor Green
}

function Write-Err($msg) {
    Write-Host "✗ $msg" -ForegroundColor Red
}

function Write-Info($msg) {
    Write-Host "ℹ $msg" -ForegroundColor Yellow
}

function Get-SkillAssetName($skillKey) {
    switch ($skillKey.ToLowerInvariant()) {
        "dmtools" { "dmtools-skill.zip" }
        "jira" { "dmtools-jira-skill.zip" }
        "github" { "dmtools-github-skill.zip" }
        "ado" { "dmtools-ado-skill.zip" }
        "testrail" { "dmtools-testrail-skill.zip" }
        default { throw "Unsupported skill package: $skillKey" }
    }
}

function Get-SkillInstallName($skillKey) {
    switch ($skillKey.ToLowerInvariant()) {
        "dmtools" { "dmtools" }
        "jira" { "dmtools-jira" }
        "github" { "dmtools-github" }
        "ado" { "dmtools-ado" }
        "testrail" { "dmtools-testrail" }
        default { throw "Unsupported skill package: $skillKey" }
    }
}

function Get-SkillCommandName($skillKey) {
    switch ($skillKey.ToLowerInvariant()) {
        "dmtools" { "/dmtools" }
        "jira" { "/dmtools-jira" }
        "github" { "/dmtools-github" }
        "ado" { "/dmtools-ado" }
        "testrail" { "/dmtools-testrail" }
        default { throw "Unsupported skill package: $skillKey" }
    }
}

function Get-RequestedSkills {
    $raw = $env:DMTOOLS_SKILLS
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @("dmtools")
    }

    $normalized = @()
    foreach ($skill in $raw.Split(",")) {
        $name = $skill.Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }

        switch ($name) {
            "dmtools" { $normalized += $name }
            "jira" { $normalized += $name }
            "github" { $normalized += $name }
            "ado" { $normalized += $name }
            "testrail" { $normalized += $name }
            default { throw "Unsupported skill package: $name" }
        }
    }

    if ($normalized.Count -eq 0) {
        return @("dmtools")
    }
    return $normalized
}

function Get-SkillDirs {
    $dirs = @()
    $cwd = (Get-Location).Path

    foreach ($sub in @(".cursor", ".claude", ".codex")) {
        $path = Join-Path $cwd $sub
        if (Test-Path $path) {
            $dirs += Join-Path $path "skills"
        }
    }

    $globalClaude = Join-Path $env:USERPROFILE ".claude"
    if (Test-Path $globalClaude) {
        $globalSkills = Join-Path $globalClaude "skills"
        $alreadyAdded = $dirs | Where-Object { $_ -eq (Join-Path $cwd ".claude\skills") }
        if (-not $alreadyAdded) {
            $dirs += $globalSkills
        }
    }

    if ($dirs.Count -eq 0) {
        $dirs += Join-Path $cwd ".cursor\skills"
    }
    return $dirs
}

function Get-SkillPackage($skillKey) {
    $assetName = Get-SkillAssetName $skillKey
    $zipPath = Join-Path $TEMP_DIR $assetName
    $extractPath = Join-Path $TEMP_DIR $skillKey
    New-Item -ItemType Directory -Path $extractPath | Out-Null

    Write-Info "Downloading $(Get-SkillInstallName $skillKey) package..."

    try {
        $releaseUrl = "https://github.com/$GITHUB_REPO/releases/latest/download/$assetName"
        Invoke-WebRequest -Uri $releaseUrl -OutFile $zipPath -UseBasicParsing
        Write-Ok "Downloaded $assetName"
    } catch {
        if ($skillKey -ne "dmtools") {
            Write-Err "Failed to download $assetName"
            throw "Focused skill packages are published from release assets only."
        }

        Write-Info "Falling back to main branch archive for dmtools..."
        $fallback = "https://github.com/$GITHUB_REPO/archive/refs/heads/main.zip"
        Invoke-WebRequest -Uri $fallback -OutFile $zipPath -UseBasicParsing
        Write-Ok "Downloaded dmtools from main branch"
    }

    Write-Info "Extracting $assetName..."
    Expand-Archive -Path $zipPath -DestinationPath $extractPath -Force

    $skillMd = Get-ChildItem -Path $extractPath -Recurse -Filter "SKILL.md" | Select-Object -First 1
    if (-not $skillMd) {
        Write-Err "SKILL.md not found in package"
        throw "Invalid skill package: $assetName"
    }
    return $skillMd.DirectoryName
}

function Install-ToDirectory($skillSource, $targetDir, $skillName) {
    $dest = Join-Path $targetDir $skillName
    if (Test-Path $dest) {
        Write-Info "Removing old version..."
        Remove-Item -Recurse -Force $dest
    }
    New-Item -ItemType Directory -Path $dest | Out-Null

    $skip = @("install.sh", "skill-install.ps1")
    Get-ChildItem -Path $skillSource | Where-Object { $_.Name -notin $skip } | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination $dest -Recurse -Force
    }
    Write-Ok "Installed to $dest"
}

function Main {
    Write-Header

    $requestedSkills = Get-RequestedSkills
    Write-Info "Detecting skill directories..."
    $dirs = Get-SkillDirs

    Write-Host ""
    Write-Host "Found skill directories:" -ForegroundColor Cyan
    for ($i = 0; $i -lt $dirs.Count; $i++) {
        Write-Host "  $($i+1). $($dirs[$i])"
    }
    Write-Host ""
    Write-Host "Selected skill packages:" -ForegroundColor Cyan
    foreach ($skill in $requestedSkills) {
        Write-Host "  - $(Get-SkillInstallName $skill) ($(Get-SkillCommandName $skill))"
    }
    Write-Host ""

    $choice = $env:INSTALL_LOCATION
    if (-not $choice) {
        if ($dirs.Count -eq 1) {
            $choice = "1"
        } elseif (-not [Environment]::UserInteractive -or $MyInvocation.InvocationName -eq "") {
            Write-Info "Non-interactive mode — installing to all detected locations"
            $choice = "all"
        } else {
            $choice = Read-Host "Where would you like to install? (Enter number or 'all' for all locations)"
        }
    }

    $targetDirs = @()
    if ($choice -eq "all" -or $choice -eq "ALL") {
        $targetDirs = $dirs
    } else {
        $idx = [int]$choice - 1
        if ($idx -ge 0 -and $idx -lt $dirs.Count) {
            $selectedDir = $dirs[$idx]
            New-Item -ItemType Directory -Path $selectedDir -Force | Out-Null
            $targetDirs = @($selectedDir)
        } else {
            Write-Err "Invalid choice: $choice"
            exit 1
        }
    }

    $installedCommands = @()
    foreach ($skill in $requestedSkills) {
        $skillSource = Get-SkillPackage $skill
        $skillName = Get-SkillInstallName $skill
        $installedCommands += Get-SkillCommandName $skill
        foreach ($dir in $targetDirs) {
            Install-ToDirectory $skillSource $dir $skillName
        }
    }

    Remove-Item -Recurse -Force $TEMP_DIR -ErrorAction SilentlyContinue

    Write-Host ""
    Write-Host "════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host "        DMtools Skill Installed Successfully!       " -ForegroundColor Green
    Write-Host "════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host ""
    Write-Host "The selected DMtools skills are now available in your AI assistant!" -ForegroundColor White
    Write-Host ""
    Write-Host "You can now:" -ForegroundColor Cyan
    foreach ($commandName in $installedCommands) {
        Write-Host "  • Type $commandName in chat to invoke the skill"
    }
    Write-Host "  • Ask about the installed DMtools areas and the assistant will use the matching skill automatically"
    Write-Host ""
    Write-Host "Example questions:" -ForegroundColor Blue
    Write-Host "  • How do I install DMtools?"
    Write-Host "  • Help me configure Jira integration"
    Write-Host "  • Review GitHub pull requests with DMtools"
    Write-Host "  • Generate test cases from user story PROJ-123"
    Write-Host ""
    Write-Host "For more information: https://github.com/epam/dm.ai" -ForegroundColor DarkGray
}

Main
