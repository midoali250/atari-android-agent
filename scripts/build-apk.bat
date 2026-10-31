@echo off
setlocal
set "ROOT=%~dp0.."
set "BUILD_TYPE=debug"
set "BUNDLE_ROMS=false"
:parse
if "%~1"=="" goto build
if /I "%~1"=="debug" (
    set "BUILD_TYPE=debug"
    shift
    goto parse
)
if /I "%~1"=="release" (
    set "BUILD_TYPE=release"
    shift
    goto parse
)
if /I "%~1"=="--bundle-roms" (
    set "BUNDLE_ROMS=true"
    shift
    goto parse
)
if /I "%~1"=="--help" goto usage
if /I "%~1"=="-h" goto usage
echo Unknown argument: %~1 1>&2
exit /b 2
:usage
echo Usage: scripts\build-apk.bat [debug^|release] [--bundle-roms]
exit /b 0
:build
set "TASK=:android:exportDebugApk"
if /I "%BUILD_TYPE%"=="release" set "TASK=:android:exportReleaseApk"
pushd "%ROOT%" || exit /b 1
call gradlew.bat %TASK% -PbundleRoms=%BUNDLE_ROMS% --console=plain
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
