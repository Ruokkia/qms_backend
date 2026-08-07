@echo off
cd /d %~dp0
:: 优先使用系统 JAVA_HOME（JDK 21），确保 >= 11
set JAVA_HOME=%JAVA_HOME%
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using JAVA_HOME: %JAVA_HOME%
java -version

:: 修复 Flyway checksum 不匹配（已执行的迁移文件被修改过）
call mvn flyway:repair -pl qms-bootstrap
if %ERRORLEVEL% neq 0 (
    echo.
    echo ===== Flyway repair 失败，请检查数据库连接和 Maven 配置 =====
    pause
    exit /b 1
)

:: 启动应用
echo Flyway repair 完成，正在启动应用...
java -jar qms-bootstrap\target\qms-bootstrap-1.0.0.jar > backend-run.log 2>&1
echo 应用已退出
pause
