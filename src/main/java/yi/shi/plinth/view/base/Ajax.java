package yi.shi.plinth.view.base;

/**
 * 生成 jQuery $.ajax 代码片段（供页面内 script 使用）。
 */
public class Ajax {

    /**
     * POST JSON，成功后可选跳转。
     */
    public static String postJson(String url, String jsonData, String successRedirect) {
        String redirect = successRedirect == null ? ""
                : "setTimeout(function(){window.location.href='" + successRedirect + "';},500);";
        return """
                $.ajax({
                    url: '%s',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify(%s),
                    success: function(response) {
                        M.toast({html: 'success'});
                        %s
                    },
                    error: function(xhr) {
                        M.toast({html: (xhr.responseText || 'error')});
                    }
                });
                """.formatted(url, jsonData, redirect);
    }

    /**
     * GET，成功回调由调用方提供（response 为返回数据）。
     */
    public static String get(String url, String successHandler) {
        return """
                $.ajax({
                    url: '%s',
                    method: 'GET',
                    success: function(response) {
                        %s
                    },
                    error: function(xhr) {
                        M.toast({html: (xhr.responseText || 'error')});
                    }
                });
                """.formatted(url, successHandler);
    }
}
