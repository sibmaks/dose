@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo Downloading the official Gradle 8.10.2 wrapper JAR...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-8.10.2-wrapper.jar'; $p='%WRAPPER_JAR%'; Invoke-WebRequest -Uri $u -OutFile ($p+'.tmp'); $h=(Get-FileHash -Algorithm SHA256 ($p+'.tmp')).Hash.ToLowerInvariant(); if ($h -ne '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046') { Remove-Item ($p+'.tmp'); throw 'Gradle wrapper hash mismatch' }; Move-Item -Force ($p+'.tmp') $p"
  if errorlevel 1 exit /b 1
)
java -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
