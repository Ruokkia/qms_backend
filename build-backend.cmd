@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\Desktop\kangli_qms\cornley-qms-server
call mvn clean package -DskipTests > build.log 2>&1
echo BUILD_EXIT=%ERRORLEVEL% >> build.log
