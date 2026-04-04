@echo off
chcp 65001 > nul
echo 正在查找并终止 wecreate 应用...

REM 查找并终止 Java 进程中包含 wecreate 的应用
for /f "tokens=1" %%i in ('jps -l ^| findstr wecreate') do (
    echo 正在终止进程 %%i ...
    taskkill /F /PID %%i
)

REM 如果上面的方式找不到进程，尝试通过 jar 包名查找
for /f "tokens=1" %%i in ('tasklist /fi "imagename eq javaw.exe" /fo csv ^| findstr wecreate') do (
    echo 正在终止 javaw 进程...
    taskkill /F /IM javaw.exe
)

echo 应用终止完成。
pause
