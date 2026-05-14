@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.
@REM ----------------------------------------------------------------------------
@REM Begin all REM://
@echo off

@REM Set the current directory to the location of this script
set WRAPPER_DIR=%~dp0
cd /d "%WRAPPER_DIR%"

@REM Determine Maven home
set MAVEN_PROJECTBASEDIR=%WRAPPER_DIR%

set WRAPPER_JAR="%WRAPPER_DIR%.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

set MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
set MAVEN_HOME=%WRAPPER_DIR%.mvn\maven

@REM Check if Maven exists
if exist "%MAVEN_HOME%\bin\mvn.cmd" goto hasMaven

echo Downloading Maven 3.9.6...
mkdir "%MAVEN_HOME%" 2>nul

@REM Use PowerShell to download
powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MAVEN_URL:"=%' -OutFile '%WRAPPER_DIR%.mvn\maven.zip' }"

if not exist "%WRAPPER_DIR%.mvn\maven.zip" (
    echo Failed to download Maven
    exit /b 1
)

echo Extracting Maven...
powershell -Command "& { Expand-Archive -Path '%WRAPPER_DIR%.mvn\maven.zip' -DestinationPath '%WRAPPER_DIR%.mvn\maven-tmp' -Force }"

@REM Move contents from nested dir
for /d %%i in ("%WRAPPER_DIR%.mvn\maven-tmp\apache-maven-*") do (
    xcopy "%%i\*" "%MAVEN_HOME%\" /s /e /q /y >nul
)

rd /s /q "%WRAPPER_DIR%.mvn\maven-tmp" 2>nul
del "%WRAPPER_DIR%.mvn\maven.zip" 2>nul

:hasMaven
set JAVA_EXE=java
set MAVEN_OPTS=-Dhttps.protocols=TLSv1.2 -Djdk.tls.client.protocols=TLSv1.2 %MAVEN_OPTS%

"%MAVEN_HOME%\bin\mvn.cmd" %*
