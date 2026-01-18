@echo off
chcp 65001 >nul
echo ========================================
echo 法律咨询助手系统 - 启动脚本
echo ========================================
echo.

echo 设置Java环境变量...
set JAVA_HOME=D:\dev\jdk17
set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo 检查Java版本...
java -version

echo.
echo 检查Docker服务状态...
docker-compose ps

echo.
echo ========================================
echo 启动应用...
echo 注意：应用启动需要一些时间，请等待...
echo 看到 "Started LegalAssistantApplication" 表示启动成功
echo ========================================
echo.

mvn spring-boot:run

pause
