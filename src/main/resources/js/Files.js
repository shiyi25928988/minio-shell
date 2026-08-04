$(document).ready(function () {
    var bucket = $('#bucket').val() || '';
    var prefix = '';

    // 预览弹窗:关闭时清空内容(移除 video 元素,停止播放)
    var $pm = $('#previewModal');
    if ($pm.length) {
        var pmInst = M.Modal.getInstance($pm[0]);
        if (pmInst) { pmInst.destroy(); }
        M.Modal.init($pm, {onCloseEnd: function () { $('#previewContent').html(''); }});
    }

    function listUrl(extra) {
        var u = '/file/list';
        var params = [];
        if (prefix) { params.push('prefix=' + encodeURIComponent(prefix)); }
        if (bucket) { params.push('bucket=' + encodeURIComponent(bucket)); }
        if (extra) { params.push(extra); }
        return params.length ? u + '?' + params.join('&') : u;
    }

    function withBucket(path) {
        return path + (bucket ? (path.indexOf('?') >= 0 ? '&' : '?') + 'bucket=' + encodeURIComponent(bucket) : '');
    }

    function loadFiles() {
        $('#busyIndicator').show();
        $.ajax({
            url: listUrl(),
            method: 'GET',
            cache: false,
            success: function (items) {
                $('#busyIndicator').hide();
                renderBreadcrumb();
                renderFiles(items || []);
            },
            error: function (xhr) {
                $('#busyIndicator').hide();
                $('#fileContainer').html('<p class="grey-text center-align" style="margin-top:40px;">Failed to load files</p>');
                M.toast({html: (xhr.responseText || 'failed to load files')});
            }
        });
    }

    function renderBreadcrumb() {
        var parts = prefix ? prefix.replace(/\/$/, '').split('/') : [];
        var html = '<a href="#" data-p="" class="crumb-link">Home</a>';
        var acc = '';
        parts.forEach(function (p) {
            if (!p) { return; }
            acc += p + '/';
            html += ' / <a href="#" data-p="' + acc + '" class="crumb-link">' + escapeHtml(p) + '</a>';
        });
        $('#breadcrumb').html(html);
        $('.crumb-link').click(function (e) {
            e.preventDefault();
            prefix = $(this).data('p') || '';
            loadFiles();
        });
    }

    function fileIcon(name, isDir) {
        if (isDir) { return '📁'; }
        var ext = (name.split('.').pop() || '').toLowerCase();
        if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'tif', 'tiff', 'ico'].indexOf(ext) >= 0) { return '🖼️'; }
        if (['mp4', 'avi', 'mov', 'webm', 'mkv', 'flv', 'wmv', 'mpeg', 'mpg'].indexOf(ext) >= 0) { return '🎬'; }
        if (['mp3', 'wav', 'flac', 'ogg', 'm4a', 'aac', 'wma'].indexOf(ext) >= 0) { return '🎵'; }
        if (ext === 'pdf') { return '📕'; }
        if (['doc', 'docx'].indexOf(ext) >= 0) { return '📘'; }
        if (['xls', 'xlsx', 'csv'].indexOf(ext) >= 0) { return '📊'; }
        if (['ppt', 'pptx'].indexOf(ext) >= 0) { return '📽️'; }
        if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'].indexOf(ext) >= 0) { return '📦'; }
        if (['js', 'ts', 'java', 'py', 'go', 'rs', 'c', 'cpp', 'cc', 'h', 'hpp', 'html', 'htm', 'css', 'scss',
             'json', 'xml', 'yml', 'yaml', 'sql', 'sh', 'bat', 'rb', 'php', 'kt', 'swift', 'vue'].indexOf(ext) >= 0) { return '💻'; }
        if (['txt', 'md', 'markdown', 'log', 'rtf'].indexOf(ext) >= 0) { return '📝'; }
        return '📄';
    }

    function renderFiles(items, showPath) {
        if (items.length === 0) {
            $('#fileContainer').html('<p class="grey-text center-align" style="margin-top:40px;">Empty folder &mdash; drag &amp; drop files here to upload</p>');
            return;
        }
        items.sort(function (a, b) {
            return (a.dir === b.dir) ? a.display.localeCompare(b.display) : (a.dir ? -1 : 1);
        });
        var html = '<ul class="collection">';
        items.forEach(function (it) {
            var icon = fileIcon(it.display, it.dir);
            var size = it.dir ? '' : '<span class="grey-text" style="margin-right:12px;">' + formatSize(it.size) + '</span>';
            var pathLine = (showPath && it.name && it.name !== it.display)
                ? '<div class="grey-text" style="font-size:0.78rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' + escapeHtml(it.name) + '</div>'
                : '';
            html += '<li class="collection-item" style="display:flex;align-items:center;">' +
                '<span class="file-name" style="cursor:pointer;flex:1;overflow:hidden;margin-right:8px;" data-path="' + escapeAttr(it.name) + '" data-dir="' + it.dir + '">' +
                '<span style="margin-right:8px;">' + icon + '</span><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' + escapeHtml(it.display) + '</span>' +
                pathLine + '</span>' +
                size +
                '<span class="file-actions" style="white-space:nowrap;">' +
                  (it.dir ? '' : '<a href="#" class="share-link" style="color:#009688;font-size:1.15rem;margin-left:4px;" title="Share" data-path="' + escapeAttr(it.name) + '">🔗</a>') +
                  (it.dir ? '' : '<a href="#" class="dl-link" style="color:#1976d2;font-size:1.15rem;margin-left:8px;" title="Download" data-path="' + escapeAttr(it.name) + '">⬇️</a>') +
                  '<a href="#" class="del-link" style="color:#e53935;font-size:1.15rem;margin-left:8px;" title="Delete" data-path="' + escapeAttr(it.name) + '" data-dir="' + it.dir + '">🗑️</a>' +
                '</span>' +
                '</li>';
        });
        html += '</ul>';
        $('#fileContainer').html(html);

        $('.file-name').click(function () {
            var p = $(this).data('path');
            if ($(this).data('dir') === true || $(this).data('dir') === 'true') {
                prefix = p;
                $('#fileSearch').val('');
                loadFiles();
            } else {
                var ext = extOf(p);
                if (isImage(ext)) { previewFile(p, 'image'); }
                else if (isVideo(ext)) { previewFile(p, 'video'); }
                else { downloadFile(p); }
            }
        });
        $('.dl-link').click(function (e) { e.preventDefault(); downloadFile($(this).data('path')); });
        $('.del-link').click(function (e) { e.preventDefault(); deleteFile($(this).data('path')); });
        $('.share-link').click(function (e) { e.preventDefault(); openShareModal($(this).data('path')); });
    }

    function downloadFile(path) {
        var a = document.createElement('a');
        a.href = withBucket('/file/download?path=' + encodeURIComponent(path));
        a.download = '';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    function extOf(name) {
        var i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : '';
    }
    function isImage(ext) {
        return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'tif', 'tiff', 'ico'].indexOf(ext) >= 0;
    }
    function isVideo(ext) {
        return ['mp4', 'webm', 'ogg', 'mov', 'm4v'].indexOf(ext) >= 0;
    }
    function previewFile(path, type) {
        var url = withBucket('/file/raw?path=' + encodeURIComponent(path));
        var html;
        if (type === 'image') {
            html = '<img src="' + url + '" style="max-width:100%;max-height:75vh;display:block;margin:0 auto;">';
        } else {
            html = '<video src="' + url + '" controls autoplay style="max-width:100%;max-height:75vh;display:block;margin:0 auto;"></video>';
        }
        $('#previewContent').html(html);
        var modal = M.Modal.getInstance($('#previewModal')[0]);
        if (modal) { modal.open(); }
    }

    var pendingDeletePath = null;
    function deleteFile(path) {
        pendingDeletePath = path;
        var name = path.lastIndexOf('/') >= 0 ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith('/')) { name = name.substring(0, name.length() - 1); }
        $('#confirmDeleteName').text(name || path);
        var modal = M.Modal.getInstance($('#confirmDeleteModal')[0]) || M.Modal.init($('#confirmDeleteModal')[0]);
        modal.open();
    }
    $(document).on('click', '#confirmDeleteBtn', function (e) {
        e.preventDefault();
        var path = pendingDeletePath;
        if (!path) { return; }
        pendingDeletePath = null;
        var inst = M.Modal.getInstance($('#confirmDeleteModal')[0]);
        if (inst) { inst.close(); }
        $.ajax({
            url: withBucket('/file/delete?path=' + encodeURIComponent(path)),
            method: 'GET',
            cache: false,
            success: function () { M.toast({html: 'deleted'}); },
            error: function (xhr) {
                var msg = xhr.responseText || 'delete failed';
                try { msg = JSON.parse(msg).errMsg || msg; } catch (e) {}
                M.toast({html: msg});
            },
            complete: function () { loadFiles(); }
        });
    });

    // ---- 分享 ----
    function openShareModal(path) {
        $('#shareFilePath').val(path);
        $('#shareForm').show();
        $('#shareResult').hide();
        $('#sharePassword').val('');
        $('#shareMaxCount').val('');
        // 重置 select（Materialize FormSelect 需重建以更新视觉）
        var sel = $('#shareExpire');
        var inst = M.FormSelect.getInstance(sel[0]);
        if (inst) { inst.destroy(); }
        sel.val('');
        M.FormSelect.init(sel);
        var modal = M.Modal.getInstance($('#shareModal')[0]) || M.Modal.init($('#shareModal')[0]);
        modal.open();
    }

    $('#createShareBtn').click(function (e) {
        e.preventDefault();
        var path = $('#shareFilePath').val();
        if (!path) { return; }
        var data = {path: path, expireDays: null, password: null, maxCount: null};
        var exp = $('#shareExpire').val();
        if (exp) { data.expireDays = parseInt(exp, 10); }
        var pwd = $('#sharePassword').val();
        if (pwd) { data.password = pwd; }
        var mc = $('#shareMaxCount').val();
        if (mc) { data.maxCount = parseInt(mc, 10); }
        $.ajax({
            url: '/share/create',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            success: function (info) {
                $('#shareForm').hide();
                $('#shareResult').show();
                $('#shareUrl').val(window.location.origin + info.url);
                M.toast({html: 'share link created'});
            },
            error: function (xhr) { M.toast({html: (xhr.responseText || 'create failed')}); }
        });
    });

    $('.copy-share-url').click(function (e) {
        e.preventDefault();
        copyText($('#shareUrl').val());
    });

    $('#uploadBtn').click(function () { $('#fileInput').click(); });

    $('#fileInput').change(function () { uploadFiles(this.files); });

    function uploadFiles(files) {
        if (!files || !files.length) { return; }
        var list = Array.prototype.slice.call(files);
        // 为每个文件生成独立的进度项（文件名 + 进度条 + 百分比）
        var html = '';
        list.forEach(function (f, i) {
            html += '<div class="upload-item" style="margin-bottom:10px;">' +
                '<div style="display:flex;justify-content:space-between;align-items:center;font-size:0.88rem;">' +
                  '<span class="grey-text text-darken-2" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;margin-right:8px;">' + escapeHtml(f.name) + ' <span class="grey-text">(' + formatSize(f.size) + ')</span></span>' +
                  '<span class="upload-pct-' + i + ' grey-text" style="white-space:nowrap;"><b>0%</b></span>' +
                '</div>' +
                '<div class="progress" style="height:10px;margin-top:3px;"><div class="determinate upload-bar-' + i + '" style="width:0%;"></div></div>' +
                '</div>';
        });
        $('#uploadProgress').html(html).show();
        $('#busyIndicator').show();

        var remaining = list.length;
        function oneDone() {
            remaining--;
            if (remaining <= 0) {
                $('#busyIndicator').hide();
                M.toast({html: list.length + ' file(s) uploaded'});
                $('#fileInput').val('');
                loadFiles();
                setTimeout(function () { $('#uploadProgress').hide(); }, 2000);
            }
        }

        // 并行上传，每个文件独立进度
        list.forEach(function (f, i) {
            var fd = new FormData();
            fd.append('file', f);
            $.ajax({
                xhr: function () {
                    var xhr = new XMLHttpRequest();
                    xhr.upload.addEventListener('progress', function (e) {
                        if (e.lengthComputable) {
                            var pct = Math.round(e.loaded / e.total * 100);
                            $('.upload-bar-' + i).css('width', pct + '%');
                            $('.upload-pct-' + i).html('<b>' + pct + '%</b> &nbsp; ' + formatSize(e.loaded) + ' / ' + formatSize(e.total));
                        }
                    });
                    return xhr;
                },
                url: withBucket('/file/upload' + (prefix ? '?path=' + encodeURIComponent(prefix) : '')),
                method: 'POST',
                data: fd,
                processData: false,
                contentType: false,
                success: function () {
                    $('.upload-bar-' + i).css('width', '100%');
                    $('.upload-pct-' + i).html('<b style="color:#2e7d32;">done</b>');
                    oneDone();
                },
                error: function (xhr) {
                    var msg = xhr.responseText || 'failed';
                    try { msg = JSON.parse(msg).errMsg || msg; } catch (ex) {}
                    $('.upload-pct-' + i).html('<b class="red-text">failed</b>');
                    M.toast({html: escapeHtml(f.name) + ': ' + msg});
                    oneDone();
                }
            });
        });
    }

    // 拖放上传:整个文件浏览区域作为放置区(dragover/drop 冒泡到 document)
    var $zone = $('main');
    function highlight() {
        $zone.css({outline: '3px dashed #1976d2', 'outline-offset': '-12px', 'background-color': '#e3f2fd'});
    }
    function unhighlight() {
        $zone.css({outline: '', 'outline-offset': '', 'background-color': ''});
    }
    $(document).on('dragover', function (e) {
        e.preventDefault(); // 必须 preventDefault 才允许 drop
        highlight();
    });
    $(document).on('drop', function (e) {
        e.preventDefault();
        unhighlight();
        var dt = e.originalEvent && e.originalEvent.dataTransfer;
        if (dt && dt.files && dt.files.length) { uploadFiles(dt.files); }
    });
    $(document).on('dragleave', function (e) {
        // 鼠标拖出窗口时清除高亮
        var oe = e.originalEvent;
        if (oe && oe.clientX <= 0 && oe.clientY <= 0) { unhighlight(); }
    });

    $('#mkdirBtn').click(function (e) {
        e.preventDefault();
        $('#mkdirFolderName').val('');
        var modal = M.Modal.getInstance($('#mkdirModal')[0]) || M.Modal.init($('#mkdirModal')[0]);
        modal.open();
        setTimeout(function () { $('#mkdirFolderName').focus(); }, 150);
    });
    $(document).on('click', '#mkdirConfirmBtn', function (e) {
        e.preventDefault();
        var name = ($('#mkdirFolderName').val() || '').trim();
        if (!name) { M.toast({html: 'folder name required'}); return; }
        var path = prefix ? prefix + name : name;
        var inst = M.Modal.getInstance($('#mkdirModal')[0]);
        if (inst) { inst.close(); }
        $.ajax({
            url: withBucket('/file/mkdir?path=' + encodeURIComponent(path)),
            method: 'GET',
            cache: false,
            success: function () { M.toast({html: 'folder created'}); },
            error: function (xhr) { M.toast({html: (xhr.responseText || 'mkdir failed')}); },
            complete: function () { loadFiles(); }
        });
    });
    $(document).on('keypress', '#mkdirFolderName', function (e) {
        if (e.which === 13) { e.preventDefault(); $('#mkdirConfirmBtn').trigger('click'); }
    });

    $('#refreshBtn').click(function (e) { e.preventDefault(); loadFiles(); });

    // 搜索：防抖 300ms，空查询恢复当前文件夹列表
    var searchTimer = null;
    $('#fileSearch').on('input', function () {
        var q = $(this).val().trim();
        clearTimeout(searchTimer);
        searchTimer = setTimeout(function () { doSearch(q); }, 300);
    });
    function doSearch(q) {
        if (!q) {
            loadFiles();
            return;
        }
        $('#busyIndicator').show();
        $.ajax({
            url: withBucket('/file/search?q=' + encodeURIComponent(q)),
            method: 'GET',
            cache: false,
            success: function (items) {
                $('#busyIndicator').hide();
                $('#breadcrumb').html('<span class="grey-text">Search results for: <b>' + escapeHtml(q) + '</b></span>');
                renderFiles(items || [], true);
            },
            error: function (xhr) {
                $('#busyIndicator').hide();
                var msg = xhr.responseText || 'search failed';
                try { msg = JSON.parse(msg).errMsg || msg; } catch (e) {}
                M.toast({html: msg});
            }
        });
    }

    function formatSize(bytes) {
        if (!bytes) return '0 B';
        var units = ['B', 'KB', 'MB', 'GB', 'TB'];
        var i = 0;
        while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++; }
        return bytes.toFixed(2) + ' ' + units[i];
    }

    function copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () { M.toast({html: 'Copied'}); },
                function () { fallbackCopy(text); });
        } else { fallbackCopy(text); }
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
        return String(s || '').replace(/[&<>"']/g, function (c) {
            return ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c];
        });
    }

    function escapeAttr(s) { return escapeHtml(s); }

    loadFiles();
});
