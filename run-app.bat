@echo off
chcp 65001 > nul
start javaw -Xms6g -Xmx10g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dspring.profiles.active=prod -jar wecreate-0.0.1-SNAPSHOT.jar
