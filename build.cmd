@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0"
call "D:\HarnessDesktopWIn\tools\gradle-8.7\bin\gradle.bat" assembleDebug --console=plain > "%~dp0build.log" 2>&1
echo EXITCODE=%ERRORLEVEL% >> "%~dp0build.log"
