@echo off
echo Starting Build...
call gradlew.bat :app:assembleDebug --info --stacktrace > build_output.log 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Build Successful
) else (
    echo Build Failed with code %ERRORLEVEL%
)
