@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script, for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal EnableExtensions EnableDelayedExpansion

set WRAPPER_DIR=%~dp0.mvn\wrapper
set PROPERTIES_FILE=%WRAPPER_DIR%\maven-wrapper.properties
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%" >NUL 2>&1
  if not exist "%PROPERTIES_FILE%" (
    echo Maven wrapper properties not found: "%PROPERTIES_FILE%"
    exit /b 1
  )

  set WRAPPER_URL=
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROPERTIES_FILE%") do (
    if /i "%%A"=="wrapperUrl" set WRAPPER_URL=%%B
  )
  if "!WRAPPER_URL!"=="" set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13;" ^
    "Invoke-WebRequest -Uri '!WRAPPER_URL!' -OutFile '!WRAPPER_JAR!'" 1>nul
  if errorlevel 1 (
    echo Failed to download Maven Wrapper JAR from: !WRAPPER_URL!
    exit /b 1
  )
)

if "%JAVA_HOME%"=="" (
  set JAVA_EXE=java.exe
  set JAVACMD=java.exe
) else (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
  set JAVACMD=%JAVA_HOME%\bin\java.exe
)

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

"%JAVACMD%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
