$(document).ready(function () {
    var bucketPrefix = $('#bucketPrefix').val() || 'user-';
    loadUsers();

    function loadUsers() {
        $.ajax({
            url: '/user/list?page=1&size=50',
            method: 'GET',
            cache: false,
            success: function (response) {
                var users = response.list || [];
                var html = '';
                users.forEach(function (u) {
                    var bucket = u.bucket || (bucketPrefix + u.id);
                    html += '<tr><td>' + u.id + '</td><td>' + escapeHtml(u.username) + '</td><td>' +
                        (u.roles || '') + '</td><td>' + (u.status === 1 ? 'Active' : 'Disabled') + '</td>' +
                        '<td>' +
                          '<a href="/?bucket=' + encodeURIComponent(bucket) + '" class="btn-small blue">Files</a> ' +
                          '<a href="#" class="btn-small orange reset-pwd" data-id="' + u.id + '" data-name="' + escapeHtml(u.username) + '">Reset Password</a>' +
                        '</td></tr>';
                });
                if (users.length === 0) {
                    html = '<tr><td colspan="5" class="grey-text">No users</td></tr>';
                }
                $('#userTableBody').html(html);

                $('.reset-pwd').click(function (e) {
                    e.preventDefault();
                    var id = $(this).data('id');
                    var name = $(this).data('name');
                    if (!confirm('Reset password for "' + name + '" to 123456?')) { return; }
                    $.ajax({
                        url: '/user/reset-password?id=' + id,
                        method: 'GET',
                        cache: false,
                        success: function () { M.toast({html: 'password reset to 123456'}); },
                        error: function (xhr) {
                            var msg = xhr.responseText || 'reset failed';
                            try { msg = JSON.parse(msg).errMsg || msg; } catch (ex) {}
                            M.toast({html: msg});
                        }
                    });
                });
            },
            error: function (xhr) {
                M.toast({html: (xhr.responseText || 'failed to load users')});
            }
        });
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c];
        });
    }
});
