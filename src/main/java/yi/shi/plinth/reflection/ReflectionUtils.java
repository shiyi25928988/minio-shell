package yi.shi.plinth.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.annotation.cache.LocalCache;
import yi.shi.plinth.utils.LocalCacheUtil;
/**
 * @author yshi
 *
 */
@Slf4j
public final class ReflectionUtils {

	/**
	 * @param clazz
	 * @return
	 * @throws Exception 
	 */
	public static Object newInstance(Class<?> clazz) throws Exception {
		Object instance = null;
		Constructor<?> constructor = clazz.getConstructor();
		try {
			instance = constructor.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			log.error(e.toString());
			throw new Exception(e);
		}
		return instance;
	}
	
	/**
	 * @param obj
	 * @param method
	 * @param args
	 * @return
	 * @throws Exception 
	 */
	public static Object invokeMethod(Object obj, Method method, Object...args) throws Exception {
		Object result = null;
		method.setAccessible(true);
		if(method.isAnnotationPresent(LocalCache.class)){
			result = LocalCacheUtil.retrive(obj, method, args);
		} else {
			try {
				result = method.invoke(obj, args);
			} catch (InvocationTargetException e) {
				// 解包目标异常，避免吞掉真实根因（如业务 IllegalArgumentException / IO 错误）
				Throwable cause = e.getCause();
				log.error("invoke method {}.{} failed", obj.getClass().getName(), method.getName(),
						cause != null ? cause : e);
				if (cause instanceof Exception) {
					throw (Exception) cause;
				}
				throw new Exception(cause != null ? cause : e);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				log.error("invoke method {}.{} failed", obj.getClass().getName(), method.getName(), e);
				throw new Exception(e);
			}
		}
		return result;
	}
	
	/**
	 * Used to inject the target Object to the field.
	 * @param obj
	 * @param field
	 * @param value
	 * @throws Exception 
	 */
	public static void setField(Object obj, Field field, Object value) throws Exception {
		field.setAccessible(true);
		try {
			field.set(obj, value);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			log.error(e.getMessage());
			throw new Exception(e);
		}
	}
}
