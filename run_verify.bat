@echo off
call gradlew.bat clean assembleDebug --console=plain > build_upgrade_verify.log 2>&1
echo EXIT_CODE=%ERRORLEVEL% >> build_upgrade_verify.log
