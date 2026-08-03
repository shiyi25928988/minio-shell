$(document).ready(function () {
    $('#registerForm').submit(function (event) {
        event.preventDefault();
        var username = $('#username').val();
        var password = $('#password').val();
        $.ajax({
            url: '/user/register',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({username: username, password: password}),
            success: function (response) {
                M.toast({html: 'register success'});
                setTimeout(function () { window.location.href = '/page/login'; }, 500);
            },
            error: function (xhr) {
                M.toast({html: (xhr.responseText || 'register failed')});
            }
        });
    });
});
