@echo off
rem Launches the desktop client with its embedded dev-only MCP server enabled
rem (design.md 3.13) - this is the command Claude Code spawns as an MCP
rem server (see .mcp.json). Never used by a real player.
rem
rem CRITICAL: the MCP stdio transport requires stdout to carry *only*
rem JSON-RPC messages - every line this script itself prints, and every line
rem Maven prints, MUST go to stderr (1^>^&2) or be silenced (-q), or it
rem corrupts the protocol stream before the java process even starts. No
rem `pause` anywhere in this file either - there is no human attached to
rem press a key, only Claude Code's MCP client reading stdout.
setlocal
cd /d "%~dp0"

echo Installing core... 1>&2
call mvn install -pl core -am -DskipTests -q 1>&2
if errorlevel 1 (
    echo core failed to build - aborting. 1>&2
    exit /b 1
)

echo Packaging client... 1>&2
call mvn -pl lwjgl3 package -DskipTests -q 1>&2
if errorlevel 1 (
    echo client failed to build - aborting. 1>&2
    exit /b 1
)

set "JAR="
for /f "delims=" %%f in ('dir /b /o-d "lwjgl3\target\StarWars-*.jar" 2^>nul ^| findstr /v /i "^original-"') do (
    if not defined JAR set "JAR=%%f"
)
if not defined JAR (
    echo No client jar found in lwjgl3\target after packaging. 1>&2
    exit /b 1
)

echo Starting client with --mcp using lwjgl3\target\%JAR% ... 1>&2
java --enable-native-access=ALL-UNNAMED -jar "lwjgl3\target\%JAR%" --mcp
