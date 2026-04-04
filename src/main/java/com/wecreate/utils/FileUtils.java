package com.wecreate.utils;

import java.io.File;

/**
 * Project：WeCreate
 * Date：2025/11/21
 * Time：15:45
 * Description：TODO
 *
 * @author UokyI
 * @version 1.0
 */
public class FileUtils {
    /**
     * 检查并创建指定路径
     *
     * @param path 要检查和创建的路径
     * @return 如果路径存在或创建成功返回true，否则返回false
     */
    public static boolean ensurePathExists(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            return directory.mkdirs();
        }
        return true;
    }
}
