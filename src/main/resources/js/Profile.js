$(document).ready(function () {
    var s3Endpoint = $('#s3Endpoint').val() || '';

    function loadProfile() {
        $.ajax({
            url: '/user/current',
            method: 'GET',
            success: function (u) {
                renderProfile(u);
            },
            error: function (xhr) {
                M.toast({html: (xhr.responseText || 'failed to load profile')});
            }
        });
    }

    function renderProfile(u) {
        var access = u.minioAccessKey || '';
        var secret = u.minioSecretKey || '';
        var bucket = u.bucket || '';
        var html = '';

        html += '<div class="card"><div class="card-content">' +
            '<span class="card-title">Account</span>' +
            '<p><b>ID:</b> ' + u.id + '</p>' +
            '<p><b>Username:</b> ' + escapeHtml(u.username) + '</p>' +
            '<p><b>Roles:</b> ' + (u.roles || '') + '</p>' +
            '<p><b>Status:</b> ' + (u.status === 1 ? 'Active' : 'Disabled') + '</p>' +
            '</div></div>';

        html += '<div class="card"><div class="card-content">' +
            '<span class="card-title">Change Password</span>' +
            '<div class="input-field"><input id="oldPwd" type="password"><label for="oldPwd">Current Password</label></div>' +
            '<div class="input-field"><input id="newPwd" type="password"><label for="newPwd">New Password</label></div>' +
            '<div class="input-field"><input id="confirmPwd" type="password"><label for="confirmPwd">Confirm New Password</label></div>' +
            '<button class="btn blue" id="changePwdBtn">Change Password</button>' +
            '</div></div>';

        html += '<div class="card"><div class="card-content">' +
            '<span class="card-title">S3 Credentials</span>' +
            '<p><b>Endpoint:</b> ' + escapeHtml(s3Endpoint) + '</p>' +
            '<p><b>Bucket:</b> ' + escapeHtml(bucket) + '</p>' +
            '<div class="input-field"><input id="accessKey" type="text" value="' + escapeAttr(access) + '" readonly>' +
            '<label for="accessKey">Access Key</label></div>' +
            '<div class="input-field"><input id="secretKey" type="text" value="' + escapeAttr(secret) + '" readonly>' +
            '<label for="secretKey">Secret Key</label></div>' +
            '<button class="btn-small blue copy-btn" data-target="accessKey">Copy Access Key</button> ' +
            '<button class="btn-small blue copy-btn" data-target="secretKey">Copy Secret</button> ' +
            '<button class="btn-small red waves-effect waves-light right" id="regenBtn">Regenerate</button>' +
            '</div></div>';

        var mcCmd = 'mc alias set myapp ' + s3Endpoint + ' ' + access + ' ' + secret + ' --api S3v4';
        var awsCmd = '# aws-cli (path-style)\n' +
            'aws configure set default.s3.addressing_style path\n' +
            'aws --endpoint-url ' + s3Endpoint + ' s3 ls s3://' + bucket + '/';
        html += '<div class="card"><div class="card-content">' +
            '<span class="card-title">Client Configuration</span>' +
            '<p class="grey-text">mc (MinIO Client)</p>' +
            '<pre style="background:#f5f5f5;padding:10px;overflow-x:auto;"><code>' + escapeHtml(mcCmd) + '</code></pre>' +
            '<button class="btn-small blue copy-text" data-text="' + escapeAttr(mcCmd) + '">Copy</button>' +
            '<p class="grey-text" style="margin-top:16px;">aws-cli</p>' +
            '<pre style="background:#f5f5f5;padding:10px;overflow-x:auto;"><code>' + escapeHtml(awsCmd) + '</code></pre>' +
            '<button class="btn-small blue copy-text" data-text="' + escapeAttr(awsCmd) + '">Copy</button>' +
            '<p class="grey-text" style="margin-top:10px;font-size:12px;">Note: large uploads must use unsigned payload ' +
            '(mc does by default; aws-cli: <code>aws configure set default.s3.payload_signing_enabled false</code>).</p>' +
            '</div></div>';

        $('#profileContainer').html(html);

        $('.copy-btn').click(function () {
            copyText($('#' + $(this).data('target')).val());
        });
        $('.copy-text').click(function () {
            copyText($(this).data('text'));
        });
        $('#regenBtn').click(function () {
            if (!confirm('Regenerate access key? The old key will stop working immediately.')) { return; }
            $.ajax({
                url: '/user/key',
                method: 'POST',
                success: function (u2) {
                    M.toast({html: 'key regenerated'});
                    renderProfile(u2);
                },
                error: function (xhr) {
                    M.toast({html: (xhr.responseText || 'regenerate failed')});
                }
            });
        });

        $('#changePwdBtn').click(function () {
            var oldP = $('#oldPwd').val();
            var newP = $('#newPwd').val();
            var confirmP = $('#confirmPwd').val();
            if (!oldP || !newP) { M.toast({html: 'please fill all fields'}); return; }
            if (newP !== confirmP) { M.toast({html: 'new passwords do not match'}); return; }
            $.ajax({
                url: '/user/password',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({oldPassword: oldP, newPassword: newP}),
                success: function () {
                    M.toast({html: 'password changed'});
                    $('#oldPwd').val(''); $('#newPwd').val(''); $('#confirmPwd').val('');
                },
                error: function (xhr) {
                    var msg = xhr.responseText || 'change failed';
                    try { msg = JSON.parse(msg).errMsg || msg; } catch (e) {}
                    M.toast({html: msg});
                }
            });
        });
    }

    function copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                M.toast({html: 'Copied'});
            }, function () { fallbackCopy(text); });
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

    function escapeAttr(s) {
        return escapeHtml(s).replace(/"/g, '&quot;');
    }

    loadProfile();
});
