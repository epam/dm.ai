# DMtools Installation Troubleshooting

[Back to README installation](../../../README.md#installation)

## 🔍 Common Installation Issues

### Issue: "Java version not found" or "Java 17+ required"

**Symptoms:**
```
Error: Java 17 or higher is required. Current version: 11
```

**Solutions:**

1. **Let the installer handle it:**
   ```bash
   # Re-run installer, it will install Java automatically
   curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
   ```

2. **Manual Java installation:**
   ```bash
   # Using SDKMAN
   curl -s "https://get.sdkman.io" | bash
   source "$HOME/.sdkman/bin/sdkman-init.sh"
   sdk install java 17-open
   sdk default java 17-open
   ```

3. **Verify Java is in PATH:**
   ```bash
   which java
   java -version
   echo $JAVA_HOME
   ```

### Issue: "dmtools: command not found"

**Symptoms:**
```bash
$ dmtools --version
bash: dmtools: command not found
```

**Solutions:**

1. **Reload your shell configuration:**
   ```bash
   source ~/.bashrc  # or ~/.zshrc for macOS
   ```

2. **Check if alias exists:**
   ```bash
   alias | grep dmtools
   # Should show: alias dmtools='java -jar /home/user/.dmtools/dmtools.jar'
   ```

3. **Manually add to PATH:**
   ```bash
   echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
   echo 'alias dmtools="java -jar ~/.dmtools/dmtools.jar"' >> ~/.bashrc
   source ~/.bashrc
   ```

4. **Check if JAR exists:**
   ```bash
   ls -la ~/.dmtools/dmtools.jar
   # Should show the JAR file
   ```

### Issue: "Permission denied" during installation

**Symptoms:**
```
curl: (23) Failed writing body
mkdir: cannot create directory '/home/user/.dmtools': Permission denied
```

**Solutions:**

1. **Fix directory permissions:**
   ```bash
   sudo chown -R $USER:$USER ~/
   chmod 755 ~/
   ```

2. **Create directories manually:**
   ```bash
   mkdir -p ~/.dmtools ~/bin
   chmod 755 ~/.dmtools ~/bin
   ```

3. **Re-run installer:**
   ```bash
   curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
   ```

### Issue: "Network error" or "Failed to download"

**Symptoms:**
```
curl: (7) Failed to connect to raw.githubusercontent.com
wget: unable to resolve host address 'github.com'
```

**Solutions:**

1. **Check internet connection:**
   ```bash
   ping -c 3 github.com
   curl -I https://github.com
   ```

2. **Use proxy if required:**
   ```bash
   export HTTP_PROXY=http://proxy.company.com:8080
   export HTTPS_PROXY=http://proxy.company.com:8080
   curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
   ```

3. **Download manually:**
   ```bash
   # Download via browser, then:
   java -jar ~/Downloads/dmtools-v1.7.179-all.jar --version
   ```

4. **Use alternative download method:**
   ```bash
   # Using wget instead of curl
   wget -O - https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
   ```

## 🖥️ Platform-Specific Issues

### macOS Issues

#### "Operation not permitted" on macOS
```bash
# Grant Terminal full disk access:
# System Preferences → Security & Privacy → Privacy → Full Disk Access
# Add Terminal.app or iTerm2
```

#### Homebrew Java conflicts
```bash
# Check Java installations
brew list | grep openjdk

# Unlink old version
brew unlink openjdk@11

# Link Java 17
brew link openjdk@17
```

### Windows WSL Issues

#### WSL not installed
```powershell
# In PowerShell as Administrator
wsl --install
# Restart computer
```

#### Java installation in WSL
```bash
# In WSL terminal
sudo apt update
sudo apt install openjdk-17-jdk
```

#### Path issues in WSL
```bash
# Add to ~/.bashrc
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### Linux Issues

#### Missing dependencies
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install curl wget unzip

# RHEL/CentOS
sudo yum install curl wget unzip

# Arch
sudo pacman -S curl wget unzip
```

## 🔧 Runtime Issues

### Issue: "Out of memory" error

**Symptoms:**
```
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
```

**Solutions:**

1. **Increase heap size in wrapper script:**
   ```bash
   # Edit ~/bin/dmtools
   java -Xmx2g -jar ~/.dmtools/dmtools.jar "$@"
   ```

2. **Set via environment variable:**
   ```bash
   export JAVA_OPTS="-Xmx2g -Xms512m"
   dmtools run agents/large_task.json
   ```

### Issue: "Class not found" or "NoClassDefFoundError"

**Symptoms:**
```
Exception in thread "main" java.lang.NoClassDefFoundError
```

**Solutions:**

1. **Re-download the JAR:**
   ```bash
   rm ~/.dmtools/dmtools.jar
   curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
   ```

2. **Check JAR integrity:**
   ```bash
   jar tf ~/.dmtools/dmtools.jar | head -20
   # Should list class files
   ```

### Issue: SSL/TLS certificate errors

**Symptoms:**
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Solutions:**

1. **Update CA certificates:**
   ```bash
   # Ubuntu/Debian
   sudo apt-get update && sudo apt-get install ca-certificates

   # macOS
   brew install ca-certificates
   ```

2. **Disable SSL verification (NOT recommended for production):**
   ```bash
   export JAVA_OPTS="-Dcom.sun.net.ssl.checkRevocation=false"
   ```

## 🐛 Debug Mode

Enable debug output for troubleshooting:

```bash
# Run with debug flag
dmtools --debug list

# Set debug environment variable
export DMTOOLS_DEBUG=true
dmtools list

# Check Java system properties
java -XshowSettings:properties -jar ~/.dmtools/dmtools.jar --version
```

## 📋 Diagnostic Commands

Run these commands to gather system information for bug reports:

```bash
# System information
echo "=== System Info ==="
uname -a
echo "=== Java Version ==="
java -version
echo "=== Java Location ==="
which java
echo "=== JAVA_HOME ==="
echo $JAVA_HOME
echo "=== DMtools Location ==="
ls -la ~/.dmtools/
echo "=== Shell ==="
echo $SHELL
echo "=== PATH ==="
echo $PATH
echo "=== Alias ==="
alias | grep dmtools
echo "=== DMtools Version ==="
dmtools --version 2>&1
```

## 🆘 Getting Help

If these solutions don't resolve your issue:

1. **Check existing issues:**
   - [GitHub Issues](https://github.com/epam/dm.ai/issues)

2. **Create a new issue with:**
   - Output from diagnostic commands above
   - Complete error message
   - Steps to reproduce
   - Operating system and version
   - Java version

3. **Community support:**
   - Include `#dmtools` tag in your question
   - Provide context about your environment

## 🔄 Clean Reinstall

If all else fails, perform a clean reinstall:

```bash
# 1. Remove all DMtools files
rm -rf ~/.dmtools
rm ~/bin/dmtools

# 2. Remove from shell config
# Edit ~/.bashrc or ~/.zshrc and remove DMtools lines

# 3. Clear Java cache
rm -rf ~/.java/

# 4. Reinstall
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash

# 5. Reload shell
exec $SHELL
```

---

*Still having issues? Report at [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)*
