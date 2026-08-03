$(document).ready(function () {
    // 切换到注册
    $('#toRegister').click(function (e) {
        e.preventDefault();
        $('#loginForm').hide();
        $('#registerForm').show();
        $('#formTitle').text('Register');
    });

    // 切换到登录
    $('#toLogin').click(function (e) {
        e.preventDefault();
        $('#registerForm').hide();
        $('#loginForm').show();
        $('#formTitle').text('Login');
    });

    // 登录
    $('#loginForm').submit(function (event) {
        event.preventDefault();
        var username = $('#loginUsername').val();
        var password = $('#loginPassword').val();
        $.ajax({
            url: '/user/login',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({username: username, password: password}),
            success: function (response) {
                M.toast({html: 'login success'});
                setTimeout(function () { window.location.href = '/'; }, 500);
            },
            error: function (xhr) {
                M.toast({html: (xhr.responseText || 'login failed')});
            }
        });
    });

    // 注册
    $('#registerForm').submit(function (event) {
        event.preventDefault();
        var username = $('#regUsername').val();
        var password = $('#regPassword').val();
        $.ajax({
            url: '/user/register',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({username: username, password: password}),
            success: function (response) {
                M.toast({html: 'register success'});
                setTimeout(function () {
                    $('#registerForm').hide();
                    $('#loginForm').show();
                    $('#formTitle').text('Login');
                    $('#loginUsername').val(username);
                }, 500);
            },
            error: function (xhr) {
                M.toast({html: (xhr.responseText || 'register failed')});
            }
        });
    });
});
