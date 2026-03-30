@rem AetherSuite Windows Gradle Wrapper
@echo off
setlocal
set APP_HOME=%~dp0
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (echo Run setup first & exit /b 1)
if defined JAVA_HOME (set JAVA="%JAVA_HOME%\bin\java.exe") else (set JAVA=java)
"%JAVA%" -Xmx64m -Xms64m -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
