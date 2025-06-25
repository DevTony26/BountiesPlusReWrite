@echo off
setlocal EnableDelayedExpansion

:: Script to zip specific folders and encode to Base64 for BountiesPlusRewrite
:: Project path: C:\Users\Anthony\IdeaProjects\BountiesPlusRewrite
:: Folders to include:
:: - src\main\java\tony26\bountiesPlus (Java classes)
:: - src\main\resources (YAML configs)
:: Usage: Double-click or run "start zip_and_encode.bat" in Command Prompt

:: Define variables
set "PROJECT_DIR=C:\Users\Anthony\IdeaProjects\BountiesPlusRewrite"
set "JAVA_DIR=%PROJECT_DIR%\src\main\java\tony26\bountiesPlus"
set "RESOURCES_DIR=%PROJECT_DIR%\src\main\resources"
set "ZIP_FILE=%PROJECT_DIR%\plugin_source.zip"
set "BASE64_FILE=%PROJECT_DIR%\plugin_source.txt"

:: Check if specified folders exist
if not exist "%JAVA_DIR%\" (
    echo Error: Java folder "%JAVA_DIR%" does not exist.
    echo Please verify the path and ensure src\main\java\tony26\bountiesPlus exists.
    pause
    exit /b 1
)
if not exist "%RESOURCES_DIR%\" (
    echo Error: Resources folder "%RESOURCES_DIR%" does not exist.
    echo Please verify the path and ensure src\main\resources exists.
    pause
    exit /b 1
)

:: Remove old ZIP and Base64 files if they exist
if exist "%ZIP_FILE%" del "%ZIP_FILE%"
if exist "%BASE64_FILE%" del "%BASE64_FILE%"

:: Create ZIP file of specified folders using tar (built-in to Windows 10/11)
echo Creating ZIP file: %ZIP_FILE%
cd /d "%PROJECT_DIR%"
tar -cf "%ZIP_FILE%" -C "%PROJECT_DIR%\src\main\java" "tony26\bountiesPlus" -C "%PROJECT_DIR%\src\main" "resources"
if %ERRORLEVEL% neq 0 (
    echo Error: Failed to create ZIP file.
    pause
    exit /b 1
)

:: Verify ZIP file was created
if not exist "%ZIP_FILE%" (
    echo Error: ZIP file "%ZIP_FILE%" was not created.
    pause
    exit /b 1
)

:: Encode ZIP file to Base64 using PowerShell
echo Encoding ZIP to Base64: %BASE64_FILE%
powershell -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('%ZIP_FILE%')) | Out-File -FilePath '%BASE64_FILE%' -Encoding ASCII"
if %ERRORLEVEL% neq 0 (
    echo Error: Failed to encode ZIP file to Base64.
    pause
    exit /b 1
)

:: Verify Base64 file was created
if not exist "%BASE64_FILE%" (
    echo Error: Base64 file "%BASE64_FILE%" was not created.
    pause
    exit /b 1
)

:: Display success message
echo Success!
echo - ZIP file created: %ZIP_FILE%
echo - Base64 file created: %BASE64_FILE%
echo You can copy the Base64 string from plugin_source.txt and paste it into your message.
echo.
echo Next steps:
echo 1. Open plugin_source.txt in a text editor (e.g., Notepad, IntelliJ).
echo 2. Copy the Base64 string.
echo 3. Paste into your message with instructions to decode and unzip.
echo 4. If the string is too long, split into chunks or use a hosting service (e.g., Pastebin, Google Drive).
pause

endlocal