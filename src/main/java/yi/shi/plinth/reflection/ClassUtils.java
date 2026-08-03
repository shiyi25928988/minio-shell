package yi.shi.plinth.reflection;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yshi
 *
 */
@Slf4j
public final class ClassUtils {


	/**
	 * Return a classes set by the given package name.
	 *
	 * @param packageName
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static Set<Class<?>> getClassSet(final String packageName) throws IOException {

		Set<Class<?>> classSet = new HashSet<>();

		Enumeration<URL> URLs = getClassLoader().getResources(packageName.replace(".", "/"));

		while (URLs.hasMoreElements()) {
			URL url = URLs.nextElement();
			if (Objects.nonNull(url)) {

				switch (url.getProtocol()) {

				case "file":
					String packagePath = escapeSpace(url.getPath());
					addClass(classSet, packagePath, packageName);
					break;

				case "jar":
					JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
					if (Objects.nonNull(jarURLConnection)) {
						JarFile jarFile = jarURLConnection.getJarFile();
						if (Objects.nonNull(jarFile)) {
							// 仅加载指定包下的类：避免遍历整个 fat jar 误加载依赖中的
							// multi-release Java 22+ 类（如 Jackson 的 FastDoubleSwar）导致启动失败
							String entryPrefix = packageName.replace(".", "/") + "/";
							Enumeration<JarEntry> jarEntries = jarFile.entries();
							while (jarEntries.hasMoreElements()) {
								JarEntry jarEntry = jarEntries.nextElement();
								String jarEntryName = jarEntry.getName();
								if (jarEntryName.endsWith(".class") && jarEntryName.startsWith(entryPrefix)
										&& !jarEntryName.startsWith("META-INF/")) {
									String className = jarEntryName.substring(0, jarEntryName.lastIndexOf("."))
											.replaceAll("/", ".");
									doAddClass(classSet, className);
								}
							}
						}
					}
					break;
				default:
					log.error(url.getProtocol() + "file process not supported!!");
				}
			}
		}
		return classSet;
	}

	/**
	 * @param classSet
	 * @param packagePath
	 * @param packageName
	 */
	private static void addClass(final Set<Class<?>> classSet, @NonNull final String packagePath,
			@NonNull final String packageName) {

		File[] files = new File(packagePath).listFiles(new FileFilter() {
			@Override
			public boolean accept(final File file) {

				if (file.isFile()) {
					if (file.getName().endsWith(".class")) {
						return true;
					}
				}
				if (file.isDirectory()) {
					return true;
				}
				return false;
			}
		});

		Stream.of(files).forEach(file -> {
			String fileName = file.getName();
			if (file.isFile()) {
				String className = fileName.substring(0, fileName.lastIndexOf("."));
				className = packageName.concat(".").concat(className);
				doAddClass(classSet, className);
			} else if (file.isDirectory()) {
				String subPackagePath = packagePath + "/" + fileName;
				String subPackageName = packageName + "." + fileName;
				addClass(classSet, subPackagePath, subPackageName);
			}
		});
	}

	/**
	 * Get the current class loader.
	 *
	 * @return ClassLoader
	 */
	private static ClassLoader getClassLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	/**
	 * @param className
	 * @param initialize
	 * @return 加载失败的类返回 null（跳过），不再误将 Object.class 加入扫描结果
	 */
	private static Class<?> loadClass(final String className, final boolean initialize) {
		try {
			return Class.forName(className, initialize, getClassLoader());
		} catch (ClassNotFoundException | NoClassDefFoundError | UnsupportedClassVersionError e) {
			return null;
		}
	}

	/**
	 * @param classSet
	 * @param className
	 */
	private static void doAddClass(final Set<Class<?>> classSet, final String className) {
		Class<?> clazz = loadClass(className, false);
		if (Objects.nonNull(clazz)) {
			classSet.add(clazz);
		}
	}

	/**
	 * %20 is a space in URL
	 *
	 * @param  str
	 * @return
	 */
	private static String escapeSpace(final String str) {
		String newStr = str.replace("%20", " ");
		newStr = newStr.replace("%5c", File.separator);
		return newStr;
	}
}
