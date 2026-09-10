@echo off
set DIR=%~dp0
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle wrapper JAR is not present and no system Gradle was found.
echo Open in Android Studio, or install Gradle 9.6 and run: gradle wrapper --gradle-version 9.6.0
exit /b 1
