@echo off
:: 1. Set console code page to UTF-8
chcp 65001 > nul

:: 2. Check if jar package exists
if not exist "target\javacli-1.0-SNAPSHOT.jar" (
    echo [WARNING] target\javacli-1.0-SNAPSHOT.jar not found, compiling using Maven...
    call mvn clean package
    if %errorlevel% neq 0 (
        echo [ERROR] Compile failed, please check Maven environment.
        pause
        exit /b %errorlevel%
    )
)

:: 3. Launch with UTF-8 properties and native access enabled
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar target\javacli-1.0-SNAPSHOT.jar %*
