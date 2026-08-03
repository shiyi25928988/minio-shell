package yi.shi.plinth.boot;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import com.google.inject.Injector;
import com.google.inject.Module;
import yi.shi.plinth.annotation.PropertiesFile;
import yi.shi.plinth.auth.RoleStpInterface;
import yi.shi.plinth.jetty.JettyBootService;
import yi.shi.plinth.modules.IocModule;
import yi.shi.plinth.modules.JettyModule;
import yi.shi.plinth.modules.ModuleRegister;
import yi.shi.plinth.properties.CoreProperties;
import yi.shi.plinth.db.SchemaInitializer;
import yi.shi.plinth.user.AdminInitializer;


/**
 * @author shiyi
 *
 */
public class ServiceBooter {

	static {
		ModuleRegister.register(new JettyModule());
	}

	public static void startFrom(Class<?> mainClass, Module... modules) throws ClassNotFoundException, IOException {
		Injector injector;
		if (Objects.nonNull(mainClass)) {
			loadPropertiesFile(mainClass);
			IocModule.registScanPackage(mainClass);
		}

		if (Objects.nonNull(modules) && modules.length > 0) {
			for(Module module : modules) {
				ModuleRegister.register(module);
			}
		}

		injector = ModuleRegister.getInjector();// Guice.createInjector(Stage.DEVELOPMENT, ModuleRegister.getModulesAsList());
		initSaToken();
		ensureCertificate();
		SchemaInitializer.ensureSchema();
		AdminInitializer.ensureDefaultAdmin(injector);
		JettyBootService service = injector.getInstance(JettyBootService.class);
		service.start();
	}

	/**
	 * 在启动时一次性初始化 sa-token 的全局配置（context 实例 / dao / 角色提供者 / token 过期时间）。
	 * 这些都是全局单例状态，不应像原先那样在每次请求中重复创建与覆盖。
	 */
	private static void initSaToken() {
		SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
		SaManager.setStpInterface(new RoleStpInterface());
		SaManager.setConfig(SaManager.getConfig().setIsPrint(false).setTimeout(Long.parseLong(System.getProperty("token.expire",  "86400"))));
	}

	/**
	 * 启动时若配置了 server.ssl.host 且 keystore 不存在，则生成自签名证书。
	 */
	private static void ensureCertificate() {
		boolean sslEnabled = Boolean.parseBoolean(System.getProperty("server.ssl.enabled", "false"));
		if (!sslEnabled) {
			return;
		}
		String sslHost = System.getProperty("server.ssl.host");
		if (sslHost == null || sslHost.trim().isEmpty()) {
			return;
		}
		String certDir = System.getProperty("server.ssl.cert.dir", "certs");
		String keystorePath = System.getProperty("server.ssl.keystore", certDir + "/keystore.p12");
		if (java.nio.file.Files.exists(java.nio.file.Paths.get(keystorePath))) {
			return;
		}
		String password = System.getProperty("server.ssl.keystore.password", "plinth");
		try {
			yi.shi.plinth.cert.CertificateGenerator.generate(sslHost, certDir, password);
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate self-signed certificate for " + sslHost, e);
		}
	}

	/**
	 * @PropertiesFile(files = { "application.properties" })
     * @Slf4j
     * public class Main {
     *     public static void main(String... strings) {
	 *
	 *
	 * @param mainClass
	 */
	private static void loadPropertiesFile(Class<?> mainClass) {

		PropertiesFile propertiesFile = mainClass.getAnnotation(PropertiesFile.class);

		if(Objects.isNull(propertiesFile)) {
			return;
		}

		String[] fileName = propertiesFile.files();
		if(fileName.length <= 0) {
			return;
		}

		Arrays.asList(fileName).forEach(pf -> {
			CoreProperties.setProperties(pf);
		});
	}

}
