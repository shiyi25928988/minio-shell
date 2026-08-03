package yi.shi.plinth.properties;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

import com.google.common.base.Strings;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;

/**
 * @author shiyi
 *
 */
public class CoreProperties {

	//private static Map<String, String> propertiesFileRegister = new ConcurrentHashMap<>();

	public static void setProperties(String propertiesFileName) {
		//propertiesFileRegister.put(propertiesFileName, propertiesFileName);
		if(propertiesFileName.toLowerCase().startsWith("http:") || propertiesFileName.toLowerCase().startsWith("https:")){
			setRemoteProperties(propertiesFileName);
			return;
		}
		try {
			Properties props = new Properties();
			props.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesFileName));
			// putIfAbsent: -D 命令行参数优先，不被配置文件覆盖（便于容器化用 JAVA_OPTS 注入配置）
			props.forEach((k, v) -> System.getProperties().putIfAbsent(k, v));
		} catch (IOException e) {
			e.printStackTrace();
			//propertiesFileRegister.remove(propertiesFileName);
		}
	}

	public static void setRemoteProperties(String url) {
		HttpClient httpClient = null;
		try {
			httpClient = new HttpClient();
			httpClient.start();
			ContentResponse response = httpClient.GET(url);
			Properties props = new Properties();
			props.load(IOUtils.toInputStream(response.getContentAsString(), "UTF-8"));
			props.forEach((k, v) -> System.getProperties().putIfAbsent(k, v));
		}catch (Exception e){
			e.printStackTrace();
		}finally {
			if(Objects.nonNull(httpClient)){
				try {
					httpClient.stop();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	/**
	 * @param key
	 * @return
	 */
	public static String getProperties(String key) {
		return getProperties(key, "");
	}

	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public static String getProperties(String key, String defaultValue) {

		if(System.getProperties().containsKey(key)) {
			String value = System.getProperties().getProperty(key);
			if(Strings.isNullOrEmpty(value)) {
				return defaultValue;
			}else {
				return value;
			}
		}else {
			return defaultValue;
		}

	}

}
