@echo off
rem Local dev helper - runs the desktop client directly (README/CLAUDE.md's
rem documented `exec:exec` workflow), without having to remember the exact
rem Maven commands. Not part of the release pipeline - see lwjgl3/pom.xml's
rem release-client profile for how the client actually gets packaged/shipped.
setlocal
cd /d "%~dp0"

rem Always reinstall core fresh, not conditionally: a stale local install can
rem silently go missing under a version other builds now expect the moment
rem a new commit lands (jgitver, design.md 3.10/CLAUDE.md's "install core
rem first" gotcha), so there's no safe way to skip this step.
echo Installing core...
call mvn install -pl core -am -DskipTests -q
if errorlevel 1 (
    echo core failed to build - aborting.
    pause
    exit /b 1
)

echo Starting client...
call mvn -pl lwjgl3 compile exec:exec
pause
