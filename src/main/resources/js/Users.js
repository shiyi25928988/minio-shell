$(document).ready(function () {
    var bucketPrefix = $('#bucketPrefix').val() || 'user-';
    $.ajax({
        url: '/user/list?page=1&size=50',
        method: 'GET',
        success: function (response) {
            var users = response.list || [];
            var html = '';
            users.forEach(function (u) {
                var bucket = u.bucket || (bucketPrefix + u.id);
                html += '<tr><td>' + u.id + '</td><td>' + escapeHtml(u.username) + '</td><td>' +
                    (u.roles || '') + '</td><td>' + (u.status === 1 ? 'Active' : 'Disabled') + '</td>' +
                    '<td><a href="/?bucket=' + encodeURIComponent(bucket) + '" class="btn-small blue">Files</a></td></tr>';
            });
            if (users.length === 0) {
                html = '<tr><td colspan="5" class="grey-text">No users</td></tr>';
            }
            $('#userTableBody').html(html);
        },
        error: function (xhr) {
            M.toast({html: (xhr.responseText || 'failed to load users')});
        }
    });

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c];
        });
    }
});
