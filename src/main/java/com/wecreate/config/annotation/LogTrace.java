package com.wecreate.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Project：WeCreate
 * Date：2026/1/2
 * Time：14:26
 * Description：TODO
 *
 * @author UokyI
 * @version 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Documented
public @interface LogTrace {
}
