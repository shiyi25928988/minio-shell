$(document).ready(function () {
    loadShares();

    function loadShares() {
        $.ajax({
            url: '/share/list',
            method: 'GET',
            success: function (items) { render(items || []); },
            error: function (xhr) { M.toast({html: (xhr.responseText || 'failed to load shares')}); }
        });
    }

    function render(items) {
        if (items.length === 0) {
            $('#shareTableBody').html('<tr><td colspan="7" class="grey-text">No shares</td></tr>');
            return;
        }
        var html = '';
        items.forEach(function (s) {
            var absUrl = window.location.origin + s.url;
            html += '<tr>'
                + '<td>' + escapeHtml(s.filename) + '</td>'
                + '<td><a href="#" class="copy-link blue-text" data-url="' + escapeAttr(absUrl) + '">Copy</a></td>'
                + '<td>' + (s.expireTime || 'Never') + '</td>'
                + '<td>' + (s.hasPassword ? 'Yes' : 'No') + '</td>'
                + '<td>' + s.downloadCount + (s.maxCount ? ' / ' + s.maxCount : '') + '</td>'
                + '<td>' + (s.createTime || '') + '</td>'
                + '<td><a href="#" class="revoke-link red-text" data-token="' + escapeAttr(s.token) + '">Revoke</a></td>'
                + '</tr>';
        });
        $('#shareTableBody').html(html);

        $('.copy-link').click(function (e) {
            e.preventDefault();
            copyText($(this).data('url'));
        });
        $('.revoke-link').click(function (e) {
            e.preventDefault();
            var token = $(this).data('token');
            if (!confirm('Revoke this share link?')) { return; }
            $.ajax({
                url: '/share/revoke?token=' + encodeURIComponent(token),
                method: 'GET',
                success: function () { M.toast({html: 'revoked'}); loadShares(); },
                error: function (xhr) { M.toast({html: (xhr.responseText || 'revoke failed')}); }
            });
        });
    }

    function copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () { M.toast({html: 'Copied'}); },
                function () { fallbackCopy(text); });
        } else {
            fallbackCopy(text);
        }
    }

    function fallbackCopy(text) {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); M.toast({html: 'Copied'}); }
        catch (e) { M.toast({html: 'copy failed'}); }
        document.body.removeChild(ta);
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c];
        });
    }

    function escapeAttr(s) { return escapeHtml(s).replace(/"/g, '&quot;'); }
});
