package yi.shi.plinth.servlet;

import java.io.IOException;

import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import yi.shi.plinth.http.HttpErrorRespHelper;
import yi.shi.plinth.rest.RestApiService;
import yi.shi.plinth.rest.RestApiServiceImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@MultipartConfig(fileSizeThreshold = 0, maxFileSize = -1L, maxRequestSize = -1L)
public class DispatcherServlet extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	private static final String METHOD_DELETE = "DELETE";
	private static final String METHOD_HEAD = "HEAD";
	private static final String METHOD_GET = "GET";
	private static final String METHOD_OPTIONS = "OPTIONS";
	private static final String METHOD_POST = "POST";
	private static final String METHOD_PUT = "PUT";
	private static final String METHOD_TRACE = "TRACE";

	private static RestApiService restService;// = new RestApiServiceImpl();

	public static void initRestApiService(){
		restService = new RestApiServiceImpl();
	}

	private ServletContext servletContext;

	@Override
	public void init(ServletConfig config) throws ServletException {
		servletContext = config.getServletContext();
	}

	@Override
	public void destroy() {
		ServletHelper.destory();
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ServletHelper.init(servletContext, req, resp);
		bindSaTokenContext();
		try {
			switch (req.getMethod()) {
			case METHOD_DELETE:
				restService.doDelete();
				break;
			case METHOD_HEAD:
				restService.doHead();
				break;
			case METHOD_GET:
				restService.doGet();
				break;
			case METHOD_OPTIONS:
				restService.doOptions();
				break;
			case METHOD_POST:
				restService.doPost();
				break;
			case METHOD_PUT:
				restService.doPut();
				break;
			case METHOD_TRACE:
				restService.doTrace();
				break;
			default:
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "HTTP METHOD NOT SUPPORT");
			}
		} catch (Exception e) {
			handleException(e, resp);
		} finally {
			// 线程会被容器复用，必须清理 ThreadLocal，避免请求间串号与内存泄漏
			ServletHelper.destory();
		}
	}

	private void handleException(Exception e, HttpServletResponse resp) throws ServletException {
		try {
			if (!resp.isCommitted()) {
				HttpErrorRespHelper.send500(e.getMessage());
			}
		} catch (Exception ex) {
			log.error(ex.getMessage());
		}
		throw new ServletException(e);
	}

	/**
	 * 每个请求把当前 servlet 的 request/response/storage 绑定到 sa-token 的线程上下文。
	 * 全局的 dao / stpInterface / config / context 实例已在启动时由 ServiceBooter 设置一次，
	 * 这里不再每请求重复创建与覆盖。
	 */
	private static void bindSaTokenContext(){
		SaRequest saRequest = new SaRequestForServlet(ServletHelper.getRequest());
		SaResponse saResponse = new SaResponseForServlet(ServletHelper.getResponse());
		SaStorage storage = new SaStorageForServlet(ServletHelper.getRequest());
		SaTokenContextForThreadLocal ctx = new SaTokenContextForThreadLocal();
		ctx.setContext(saRequest, saResponse, storage);
	}

}
