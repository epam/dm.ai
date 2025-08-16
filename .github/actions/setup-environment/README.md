# Setup DMTools Environment Action

This composite action sets up the complete development environment for DMTools workflows, including Java, Node.js, Playwright, and necessary caching.

## Usage

```yaml
- name: Setup DMTools Environment
  uses: ./.github/actions/setup-environment
  with:
    java-version: '23'           # Optional, defaults to '23'
    node-version: '20'           # Optional, defaults to '20'
    cache-key-suffix: '-pr'      # Optional, for cache isolation
```

## Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| `java-version` | Java version to install | No | `'23'` |
| `node-version` | Node.js version to install | No | `'20'` |
| `cache-key-suffix` | Additional suffix for cache keys to avoid conflicts between workflows | No | `''` |

## What it does

1. **Sets up Java** using Temurin distribution
2. **Sets up Node.js** with specified version
3. **Installs Playwright** with all browsers and dependencies
4. **Installs xmllint** for XML processing in tests
5. **Caches Gradle** files (dependencies, wrapper, build cache)
6. **Caches Playwright** browsers for faster subsequent runs

## Cache Strategy

- **Gradle Cache**: Uses file hashes of `*.gradle*` and `gradle-wrapper.properties`
- **Node.js Cache**: Caches npm cache and node_modules using `package*.json` hash
- **Playwright Cache**: Uses `package*.json` hash with smart installation checks
- **Cache Isolation**: Different workflows use different cache keys via `cache-key-suffix`

### Playwright Caching Improvements (Enhanced v2)

- **Enhanced Detection**: Checks cache directory existence and browser installation status
- **Efficient Installation**: Only installs Chromium browser (not all browsers) to reduce download time
- **Better Cache Paths**: Multiple platform-specific cache directories
- **Improved Key Strategy**: Package hash first for better cache organization
- **Detailed Logging**: Shows cache hits, directory sizes, and installation progress
- **Smart Dependencies**: System dependencies managed separately from browser binaries
- **Faster Fallbacks**: Multiple restore keys for maximum cache reuse

## Cache Keys (Updated v2)

- Gradle: `{os}-gradle-{gradle-files-hash}{suffix}`
- Node.js: `{os}-node{suffix}-{package-files-hash}`
- Playwright: `{os}-playwright-{package-files-hash}-{suffix}-v2`

## Used in Workflows

- `fatjar_release.yml` - with suffix `-release`
- `gemini-cli-implementation.yml` - with suffix `-gemini`
- `pr-tests.yml` - with suffix `-pr`

This ensures each workflow has isolated caches while sharing the common setup logic.
