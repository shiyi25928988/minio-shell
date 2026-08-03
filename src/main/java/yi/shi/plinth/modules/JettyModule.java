package yi.shi.plinth.modules;

import java.util.EnumSet;

import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import org.eclipse.jetty.ee10.servlet.ListenerHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import yi.shi.plinth.proxy.MinioProxyServlet;
import yi.shi.plinth.servlet.DispatcherServlet;
import yi.shi.plinth.servlet.GuiceServletCustomContextListener;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * @author shiyi
 */
public class JettyModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ServletContextHandler.class).toProvider(ServletContextHandlerProvider.class).in(Singleton.class);
		bind(Server.class).toProvider(ServerProvider.class).in(Singleton.class);
		bind(ServletContext.class).toProvider(ServletContextProvider.class).in(Singleton.class);
	}


	private static class ServletContextHandlerProvider implements Provider<ServletContextHandler> {
		@Override
		public ServletContextHandler get() {
			ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
			servletContextHandler.setContextPath("/");
			ServletHolder dispatcherHolder = servletContextHandler.addServlet(DispatcherServlet.class, "/*");
			// 显式启用 multipart：Jetty 经 addServlet(Class,path) 注册时不一定自动应用
			// @MultipartConfig 注解，不设置则 request.getPart()/getParts() 会抛 IllegalStateException
			dispatcherHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(""));
			servletContextHandler.addServlet(MinioProxyServlet.class, "/s3/*");
			servletContextHandler.addFilter(GuiceFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
			servletContextHandler.getServletHandler().addListener(new ListenerHolder(GuiceServletCustomContextListener.class));
			servletContextHandler.setDefaultRequestCharacterEncoding("UTF-8");
			servletContextHandler.setDefaultResponseCharacterEncoding("UTF-8");
			return servletContextHandler;
		}
	}

	private static class ServletContextProvider implements Provider<ServletContext> {
		@Inject
		ServletContextHandler servletContextHandler;

		@Override
		public ServletContext get() {
			ServletContext servletContext =  servletContextHandler.getServletContext();
			return servletContext;
		}
	}

	private static class ServerProvider implements Provider<Server> {

		@Inject
		ServletContextHandler servletContextHandler;

		@Override
		public Server get() {
			try {
				ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
				contextHandlerCollection.addHandler(servletContextHandler);
				int port = Integer.parseInt(System.getProperty("server.port", "8080"));
				Server server = new Server();
				server.setStopAtShutdown(true);
				server.setHandler(contextHandlerCollection);
				server.addConnector(createConnector(server, port));
				return server;
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Failed to create Jetty Server", e);
			}
		}

		/**
		 * 配置 server.ssl.enabled=true 时启用 HTTPS（SslContextFactory + ServerConnector），
		 * 否则使用普通 HTTP 连接器（向后兼容）。
		 */
		private static ServerConnector createConnector(Server server, int port) {
			boolean sslEnabled = Boolean.parseBoolean(System.getProperty("server.ssl.enabled", "false"));
			if (sslEnabled) {
				SslContextFactory.Server ssl = new SslContextFactory.Server();
				String certDir = System.getProperty("server.ssl.cert.dir", "certs");
				ssl.setKeyStorePath(System.getProperty("server.ssl.keystore", certDir + "/keystore.p12"));
				String pwd = System.getProperty("server.ssl.keystore.password", "plinth");
				ssl.setKeyStorePassword(pwd);
				ssl.setKeyManagerPassword(pwd);

				HttpConfiguration httpsConfig = new HttpConfiguration();
				httpsConfig.setSecureScheme("https");
				httpsConfig.setSecurePort(port);
				httpsConfig.addCustomizer(new SecureRequestCustomizer());

				ServerConnector sslConnector = new ServerConnector(server,
						new SslConnectionFactory(ssl, "http/1.1"),
						new HttpConnectionFactory(httpsConfig));
				sslConnector.setPort(port);
				return sslConnector;
			}
			ServerConnector httpConnector = new ServerConnector(server);
			httpConnector.setPort(port);
			return httpConnector;
		}
	}

}
