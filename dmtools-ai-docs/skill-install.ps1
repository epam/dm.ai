# DMtools Agent Skill Installer for Windows PowerShell
# Works with Cursor, Claude, Codex, and any Agent Skills compatible system
# Installs to project-level directories (.cursor\skills, .claude\skills, .codex\skills)
# and to global ~\.claude\skills if ~\.claude exists (Claude Code / GitHub Copilot CLI)
#
# Usage:
#   irm https://github.com/epam/dm.ai/releases/latest/download/skill-install.ps1 | iex
#   # When piped (non-interactive): installs to ALL detected locations automatically
#
#   $env:INSTALL_LOCATION = "1"; irm .../skill-install.ps1 | iex   # Install to first location only
#   # Interactive: runs menu to choose location

$ErrorActionPreference = "Stop"

# Configuration
$SKILL_NAME   = "dmtools"
$GITHUB_REPO  = "epam/dm.ai"
$TEMP_DIR     = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), [System.Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $TEMP_DIR | Out-Null

# ── Output helpers ────────────────────────────────────────────────────────────
function Write-Header {
    Write-Host ""
    Write-Host "╔════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║      DMtools Agent Skill Installer         ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Cyan
    Write-Host ""
}
function Write-Ok($msg)   { Write-Host "✓ $msg" -ForegroundColor Green  }
function Write-Err($msg)  { Write-Host "✗ $msg" -ForegroundColor Red    }
function Write-Info($msg) { Write-Host "ℹ $msg" -ForegroundColor Yellow }

# ── Detect skill directories ──────────────────────────────────────────────────
function Get-SkillDirs {
    $dirs = @()
    $cwd  = (Get-Location).Path

    foreach ($sub in @(".cursor", ".claude", ".codex")) {
        $path = Join-Path $cwd $sub
        if (Test-Path $path) { $dirs += Join-Path $path "skills" }
    }

    $globalClaude = Join-Path $env:USERPROFILE ".claude"
    if (Test-Path $globalClaude) {
        $globalSkills = Join-Path $globalClaude "skills"
        # Add only if not already covered by project-level .claude
        $alreadyAdded = $dirs | Where-Object { $_ -eq (Join-Path $cwd ".claude\skills") }
        if (-not $alreadyAdded) { $dirs += $globalSkills }
    }

    if ($dirs.Count -eq 0) { $dirs += Join-Path $cwd ".cursor\skills" }
    return $dirs
}

# ── Download skill package ────────────────────────────────────────────────────
function Get-SkillPackage {
    Write-Info "Fetching latest release information..."

    $zipPath = Join-Path $TEMP_DIR "dmtools-skill.zip"

    try {
        $apiUrl   = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        $release  = Invoke-RestMethod -Uri $apiUrl -TimeoutSec 30
        $asset    = $release.assets | Where-Object { $_.name -like "dmtools-skill-*.zip" } | Select-Object -First 1
        if ($asset) {
            Write-Info "Downloading $($asset.name)..."
            Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $zipPath -UseBasicParsing
            Write-Ok "Downloaded latest release"
        } else {
            throw "No skill ZIP found in release assets"
        }
    } catch {
        Write-Info "Falling back to main branch archive..."
        $fallback = "https://github.com/$GITHUB_REPO/archive/refs/heads/main.zip"
        Invoke-WebRequest -Uri $fallback -OutFile $zipPath -UseBasicParsing
        Write-Ok "Downloaded from main branch"
    }

    Write-Info "Extracting skill package..."
    Expand-Archive -Path $zipPath -DestinationPath $TEMP_DIR -Force

    # Locate SKILL.md
    $skillMd = Get-ChildItem -Path $TEMP_DIR -Recurse -Filter "SKILL.md" | Select-Object -First 1
    if (-not $skillMd) {
        Write-Err "SKILL.md not found in package"
        throw "Invalid skill package"
    }
    return $skillMd.DirectoryName
}

# ── Install to a single directory ────────────────────────────────────────────
function Install-ToDirectory($skillSource, $targetDir) {
    $dest = Join-Path $targetDir $SKILL_NAME
    if (Test-Path $dest) {
        Write-Info "Removing old version..."
        Remove-Item -Recurse -Force $dest
    }
    New-Item -ItemType Directory -Path $dest | Out-Null

    # Copy everything except installer artifacts
    $skip = @("install.sh", "skill-install.ps1", "dmtools-skill.zip")
    Get-ChildItem -Path $skillSource | Where-Object { $_.Name -notin $skip } | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination $dest -Recurse -Force
    }
    Write-Ok "Installed to $dest"
}

# ── Main ──────────────────────────────────────────────────────────────────────
function Main {
    Write-Header

    $skillSource = Get-SkillPackage
    Write-Info "Detecting skill directories..."
    $dirs = Get-SkillDirs

    Write-Host ""
    Write-Host "Found skill directories:" -ForegroundColor Cyan
    for ($i = 0; $i -lt $dirs.Count; $i++) {
        Write-Host "  $($i+1). $($dirs[$i])"
    }
    Write-Host ""

    # Determine choice
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

    if ($choice -eq "all" -or $choice -eq "ALL") {
        foreach ($dir in $dirs) { Install-ToDirectory $skillSource $dir }
    } else {
        $idx = [int]$choice - 1
        if ($idx -ge 0 -and $idx -lt $dirs.Count) {
            $selectedDir = $dirs[$idx]
            New-Item -ItemType Directory -Path $selectedDir -Force | Out-Null
            Install-ToDirectory $skillSource $selectedDir
        } else {
            Write-Err "Invalid choice: $choice"
            exit 1
        }
    }

    # Cleanup
    Remove-Item -Recurse -Force $TEMP_DIR -ErrorAction SilentlyContinue

    Write-Host ""
    Write-Host "════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host "        DMtools Skill Installed Successfully!       " -ForegroundColor Green
    Write-Host "════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host ""
    Write-Host "The DMtools skill is now available in your AI assistant!" -ForegroundColor White
    Write-Host ""
    Write-Host "You can now:" -ForegroundColor Cyan
    Write-Host "  • Type /dmtools in chat to invoke the skill"
    Write-Host "  • Ask about DMtools and the assistant will use the skill automatically"
    Write-Host ""
    Write-Host "Example questions:" -ForegroundColor Blue
    Write-Host "  • How do I install DMtools?"
    Write-Host "  • Help me configure Jira integration"
    Write-Host "  • Show me how to create JavaScript agents"
    Write-Host "  • Generate test cases from user story PROJ-123"
    Write-Host ""
    Write-Host "For more information: https://github.com/epam/dm.ai" -ForegroundColor DarkGray
}

Main
