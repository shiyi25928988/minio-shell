$(document).ready(function () {
    var token = $('#shareToken').val() || '';
    var hasPassword = $('#shareHasPassword').val() === 'true';

    $('#downloadBtn').click(function () {
        if (!token) { return; }
        if (hasPassword) {
            var password = $('#sharePassword').val() || '';
            // 先校验密码（不计数），通过后再下载
            $.ajax({
                url: '/share/check?token=' + encodeURIComponent(token) + '&password=' + encodeURIComponent(password),
                method: 'GET',
                success: function () { downloadFile(password); },
                error: function (xhr) { M.toast({html: (xhr.responseText || 'password incorrect or link invalid')}); }
            });
        } else {
            downloadFile('');
        }
    });

    function downloadFile(password) {
        var url = '/share/download?token=' + encodeURIComponent(token);
        if (password) { url += '&password=' + encodeURIComponent(password); }
        var a = document.createElement('a');
        a.href = url;
        a.download = '';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }
});
