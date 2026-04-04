package com.wecreate.utils;
import lombok.extern.slf4j.Slf4j;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.Pointer;
/**
 * Project：WeCreate
 * Date：2026/1/1
 * Time：17:06
 * Description：TODO
 *
 * @author UokyI
 * @version 1.0
 */
@Slf4j
public class ProcessUtils {

    /**
     * 获取进程ID (兼容JDK 8及更高版本)
     * @param process 进程对象
     * @return 进程ID
     */
    public static long getProcessId(Process process) {
        // 优先使用 Java 9+ 的公共 API
        try {
            java.lang.reflect.Method pidMethod = Process.class.getMethod("pid");
            long pid = (long) pidMethod.invoke(process);
            if (pid > 0) {
                return pid;
            }
        } catch (Exception e) {
            // Java 8 不支持 Process.pid()，继续下面的逻辑
        }

        // Java 8 的处理方式：通过反射获取
        String className = process.getClass().getName();
        if (className.equals("java.lang.UNIXProcess")) {
            try {
                java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
                pidField.setAccessible(true);
                return pidField.getLong(process);
            } catch (Exception unixException) {
                log.error("无法从UNIXProcess获取PID", unixException);
            }
        } else if (className.equals("java.lang.ProcessImpl")) {
            try {
                // 对于Windows，通过handle获取PID
                java.lang.reflect.Field handleField = process.getClass().getDeclaredField("handle");
                handleField.setAccessible(true);
                long handle = handleField.getLong(process);
                Kernel32 kernel = Kernel32.INSTANCE;
                WinNT.HANDLE winntHandle = new WinNT.HANDLE();
                winntHandle.setPointer(Pointer.createConstant(handle));
                long pid = kernel.GetProcessId(winntHandle);
                if (pid > 0) {
                    return pid;
                }
            } catch (Exception winException) {
                log.warn("无法通过反射获取Windows进程ID，尝试备用方案");
            }

            // 备用方案：通过命令查询
            try {
                // 使用 wmic 查找最近启动的 cmd.exe 或 qwen.cmd 进程
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "wmic process where \"Name='cmd.exe'\" get ProcessId,CommandLine /format:list");
                Process wmicProc = pb.start();
                wmicProc.waitFor();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(wmicProc.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("qwen") || line.contains("qwen.cmd")) {
                        String[] parts = line.split("ProcessId=");
                        if (parts.length > 1) {
                            String pidStr = parts[1].trim().split("\r")[0];
                            try {
                                return Long.parseLong(pidStr);
                            } catch (NumberFormatException ignore) {
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("备用方案也失败: {}", ex.getMessage());
            }
        }

        // 如果所有方法都失败，返回 -1
        return -1;
    }
}