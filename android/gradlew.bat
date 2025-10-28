@ECHO OFF

set DIR=%~dp0
IF EXIST "%DIR%\jre" (
  SET JAVA_EXE=%DIR%\jre\bin\java.exe
) ELSE (
  SET JAVA_EXE=java.exe
)

IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
  EXIT /B 1
)

set CLASSPATH=%DIR%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
