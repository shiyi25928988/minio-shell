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

    // 磁盘用量：拉取 MinIO 集群剩余空间，在文件浏览器顶部展示用量条
    function loadStorage() {
        $.ajax({
            url: '/file/storage',
            method: 'GET',
            cache: false,
            success: function (u) {
                if (!u || !u.totalBytes) {
                    $('#storageInfo').html('');
                    return;
                }
                var pct = u.utilizationPercent || 0;
                var barColor = pct >= 90 ? '#e53935' : (pct >= 70 ? '#fb8c00' : '#43a047');
                var txtColor = pct >= 90 ? 'red-text' : (pct >= 70 ? 'orange-text' : 'green-text');
                var html = '<div class="card-panel" style="padding:12px 16px;">' +
                    '<div style="display:flex;justify-content:space-between;align-items:center;font-size:0.9rem;flex-wrap:wrap;">' +
                      '<span class="grey-text text-darken-2"><b>Storage</b> &nbsp; ' + formatSize(u.usedBytes) + ' / ' + formatSize(u.totalBytes) + ' used</span>' +
                      '<span class="' + txtColor + '" style="font-weight:bold;">' + pct.toFixed(1) + '% &middot; ' + formatSize(u.availableBytes) + ' free</span>' +
                    '</div>' +
                    '<div class="progress" style="height:8px;margin:8px 0 0 0;"><div class="determinate" style="width:' + pct + '%;background-color:' + barColor + ';"></div></div>' +
                    '</div>';
                $('#storageInfo').html(html);
            },
            error: function () { $('#storageInfo').html(''); }
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

    // 列表排序：key = name(首字母)/size/time，dir = asc/desc；文件夹始终排在文件前
    var sortKey = 'name', sortDir = 'asc';

    function formatTime(iso) {
        if (!iso) { return ''; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return iso; }
        function p(n) { return (n < 10 ? '0' : '') + n; }
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }

    function renderFiles(items, showPath) {
        if (items.length === 0) {
            $('#fileContainer').html('<p class="grey-text center-align" style="margin-top:40px;">Empty folder &mdash; drag &amp; drop files here to upload</p>');
            return;
        }
        items.sort(function (a, b) {
            var r;
            if (a.dir !== b.dir) {
                r = a.dir ? -1 : 1;
            } else if (sortKey === 'size') {
                r = (a.size || 0) - (b.size || 0);
            } else if (sortKey === 'time') {
                r = String(a.lastModified || '').localeCompare(String(b.lastModified || ''));
            } else {
                r = a.display.localeCompare(b.display);
            }
            return sortDir === 'desc' ? -r : r;
        });
        var html = '<ul class="collection">';
        items.forEach(function (it) {
            var icon = fileIcon(it.display, it.dir);
            var size = it.dir ? '' : '<span class="grey-text" style="margin-right:12px;">' + formatSize(it.size) + '</span>';
            var time = '<span class="grey-text" style="margin-right:12px;font-size:0.8rem;white-space:nowrap;">' + formatTime(it.lastModified) + '</span>';
            var pathLine = (showPath && it.name && it.name !== it.display)
                ? '<div class="grey-text" style="font-size:0.78rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' + escapeHtml(it.name) + '</div>'
                : '';
            html += '<li class="collection-item" style="display:flex;align-items:center;">' +
                '<label style="display:flex;align-items:center;flex:none;margin-right:10px;cursor:pointer;">' +
                    '<input type="checkbox" class="item-check" data-path="' + escapeAttr(it.name) + '" data-dir="' + it.dir + '">' +
                    '<span style="height:25px;"></span></label>' +
                '<span class="file-name" style="cursor:pointer;flex:1;overflow:hidden;margin-right:8px;" data-path="' + escapeAttr(it.name) + '" data-dir="' + it.dir + '" data-size="' + (it.size || 0) + '">' +
                '<span style="margin-right:8px;">' + icon + '</span><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' + escapeHtml(it.display) + '</span>' +
                pathLine + '</span>' +
                size +
                time +
                '<span class="file-actions" style="white-space:nowrap;">' +
                  (it.dir ? '' : '<a href="#" class="share-link" style="margin-left:4px;" title="Share" data-path="' + escapeAttr(it.name) + '"><img src="/images/link.svg" alt="Share" style="width:18px;height:18px;vertical-align:middle;"></a>') +
                  (it.dir ? '' : '<a href="#" class="dl-link" style="margin-left:8px;" title="Download" data-path="' + escapeAttr(it.name) + '"><img src="/images/download.svg" alt="Download" style="width:18px;height:18px;vertical-align:middle;"></a>') +
                  '<a href="#" class="del-link" style="margin-left:8px;" title="Delete" data-path="' + escapeAttr(it.name) + '" data-dir="' + it.dir + '"><img src="/images/delete.svg" alt="Delete" style="width:18px;height:18px;vertical-align:middle;"></a>' +
                '</span>' +
                '</li>';
        });
        html += '</ul>';
        $('#fileContainer').html(html);

        $('.item-check').change(updateBatchBar);
        updateBatchBar();

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
                else if (isPdf(ext)) { previewFile(p, 'pdf'); }
                else if (isAudio(ext)) { previewFile(p, 'audio'); }
                else if (isText(ext)) {
                    var sz = $(this).data('size');
                    if (typeof sz === 'number' && sz > 2 * 1024 * 1024) {
                        M.toast({html: 'File too large to preview, downloading'});
                        downloadFile(p);
                    } else { previewText(p, ext); }
                }
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
        // tif/tiff 不列：主流浏览器均不支持内联渲染 TIFF，点击直接下载
        return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].indexOf(ext) >= 0;
    }
    function isVideo(ext) {
        return ['mp4', 'webm', 'ogg', 'mov', 'm4v'].indexOf(ext) >= 0;
    }
    function isPdf(ext) {
        return ext === 'pdf';
    }
    function isAudio(ext) {
        return ['mp3', 'wav', 'ogg', 'oga', 'm4a', 'flac', 'aac', 'opus'].indexOf(ext) >= 0;
    }
    function isText(ext) {
        return ['txt', 'md', 'markdown', 'log', 'csv', 'json', 'xml', 'yml', 'yaml', 'ini', 'conf', 'properties',
                'sql', 'js', 'ts', 'java', 'py', 'go', 'rs', 'c', 'cpp', 'cc', 'h', 'hpp',
                'html', 'htm', 'css', 'scss', 'sh', 'bat', 'rb', 'php', 'kt', 'swift', 'vue', 'toml'].indexOf(ext) >= 0;
    }
    function previewFile(path, type) {
        var url = withBucket('/file/raw?path=' + encodeURIComponent(path));
        var html;
        if (type === 'image') {
            html = '<img src="' + url + '" style="max-width:100%;max-height:75vh;display:block;margin:0 auto;">';
        } else if (type === 'pdf') {
            // 浏览器内置 PDF 查看器在 iframe 中内联渲染
            html = '<iframe src="' + url + '" style="width:100%;height:75vh;border:0;"></iframe>';
        } else if (type === 'audio') {
            html = '<audio src="' + url + '" controls autoplay style="width:100%;display:block;margin:0 auto;"></audio>';
        } else {
            html = '<video src="' + url + '" controls autoplay style="max-width:100%;max-height:75vh;display:block;margin:0 auto;"></video>';
        }
        $('#previewContent').html(html);
        var modal = M.Modal.getInstance($('#previewModal')[0]);
        if (modal) { modal.open(); }
    }
    function previewText(path, ext) {
        // 文本类预览：XHR 以 text 拉取后放入 <pre>，不受对象存储 content-type 影响
        var url = withBucket('/file/raw?path=' + encodeURIComponent(path));
        $('#previewContent').html('<p class="grey-text center-align">Loading...</p>');
        var modal = M.Modal.getInstance($('#previewModal')[0]);
        if (modal) { modal.open(); }
        $.ajax({
            url: url,
            method: 'GET',
            dataType: 'text',
            cache: false,
            success: function (data) {
                var text = (data === null || data === undefined) ? '' : String(data);
                var truncated = false;
                if (text.length > 300000) { text = text.substring(0, 300000); truncated = true; }
                if (ext === 'json') {
                    try { text = JSON.stringify(JSON.parse(data), null, 2); } catch (ex) { /* 保持原文 */ }
                }
                var note = truncated
                    ? '<p class="grey-text left-align" style="font-size:0.8rem;">Preview truncated &mdash; download to view full content.</p>'
                    : '';
                $('#previewContent').html(note +
                    '<pre style="white-space:pre-wrap;word-break:break-word;text-align:left;max-height:70vh;overflow:auto;background:#f5f5f5;padding:12px;border-radius:4px;font-size:0.85rem;">' +
                    escapeHtml(text) + '</pre>');
            },
            error: function (xhr) {
                var msg = 'preview failed';
                try { msg = JSON.parse(xhr.responseText).errMsg || msg; } catch (ex) {}
                M.toast({html: msg});
                var inst = M.Modal.getInstance($('#previewModal')[0]);
                if (inst) { inst.close(); }
            }
        });
    }

    // ---- 删除（单个与批量共用一个确认弹窗）----
    var pendingDeletePaths = null;
    function displayNameOf(path) {
        var name = path.lastIndexOf('/') >= 0 ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith('/')) { name = name.substring(0, name.length - 1); }
        return name;
    }
    function deleteFile(path) { deleteFiles([path]); }
    function deleteFiles(paths) {
        if (!paths || !paths.length) { return; }
        pendingDeletePaths = paths;
        var label = paths.length === 1 ? (displayNameOf(paths[0]) || paths[0]) : paths.length + ' items';
        $('#confirmDeleteName').text(label);
        var modal = M.Modal.getInstance($('#confirmDeleteModal')[0]) || M.Modal.init($('#confirmDeleteModal')[0]);
        modal.open();
    }
    $(document).on('click', '#confirmDeleteBtn', function (e) {
        e.preventDefault();
        var paths = pendingDeletePaths;
        if (!paths || !paths.length) { return; }
        pendingDeletePaths = null;
        var inst = M.Modal.getInstance($('#confirmDeleteModal')[0]);
        if (inst) { inst.close(); }
        var ok = 0, fail = 0, failMsg = '', done = 0;
        paths.forEach(function (p) {
            $.ajax({
                url: withBucket('/file/delete?path=' + encodeURIComponent(p)),
                method: 'GET',
                cache: false,
                success: function () { ok++; },
                error: function (xhr) {
                    fail++;
                    if (!failMsg) {
                        failMsg = xhr.responseText || 'delete failed';
                        try { failMsg = JSON.parse(failMsg).errMsg || failMsg; } catch (ex) {}
                    }
                },
                complete: function () {
                    done++;
                    if (done === paths.length) {
                        M.toast({html: ok + ' deleted' + (fail ? ', ' + fail + ' failed: ' + failMsg : '')});
                        loadFiles();
                        loadStorage();
                    }
                }
            });
        });
    });

    // ---- 勾选与批量操作 ----
    function selectedItems() {
        var items = [];
        $('.item-check:checked').each(function () {
            items.push({
                path: $(this).data('path'),
                dir: $(this).data('dir') === true || $(this).data('dir') === 'true'
            });
        });
        return items;
    }
    function updateBatchBar() {
        var boxes = $('.item-check');
        var checked = $('.item-check:checked');
        $('#selCount').text(checked.length ? checked.length + ' selected' : '');
        $('#batchDownloadBtn').toggleClass('disabled', checked.length === 0);
        $('#batchDeleteBtn').toggleClass('disabled', checked.length === 0);
        $('#selectAllCheck').prop('checked', boxes.length > 0 && checked.length === boxes.length);
    }
    $('#selectAllCheck').change(function () {
        $('.item-check').prop('checked', $(this).prop('checked'));
        updateBatchBar();
    });
    $('#batchDownloadBtn').click(function (e) {
        e.preventDefault();
        if ($(this).hasClass('disabled')) { return; }
        var files = selectedItems().filter(function (it) { return !it.dir; });
        if (!files.length) {
            M.toast({html: 'no files selected (folders cannot be downloaded)'});
            return;
        }
        // 逐个触发下载，稍作间隔避免浏览器拦截连续下载
        files.forEach(function (f, i) {
            setTimeout(function () { downloadFile(f.path); }, i * 400);
        });
        M.toast({html: files.length + ' download(s) started'});
    });
    $('#batchDeleteBtn').click(function (e) {
        e.preventDefault();
        if ($(this).hasClass('disabled')) { return; }
        deleteFiles(selectedItems().map(function (it) { return it.path; }));
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

    // 手动中断：点击某个上传项的 ✕ 调用 jqXHR.abort()，服务端连接断开、对象不会写入
    var currentUploads = null;
    $(document).on('click', '.upload-cancel', function (e) {
        e.preventDefault();
        var i = $(this).data('i');
        if (currentUploads && currentUploads[i]) { currentUploads[i].abort(); }
    });

    function uploadFiles(files) {
        if (!files || !files.length) { return; }
        // 上传进行中不叠加新批次：进度面板即指示器，避免取消句柄被覆盖
        if (currentUploads) {
            M.toast({html: 'Upload already in progress'});
            $('#fileInput').val('');
            return;
        }
        var list = Array.prototype.slice.call(files);
        // 为每个文件生成独立的进度项（文件名 + 进度条 + 百分比 + 取消按钮）
        var html = '';
        list.forEach(function (f, i) {
            html += '<div class="upload-item" style="margin-bottom:10px;">' +
                '<div style="display:flex;justify-content:space-between;align-items:center;font-size:0.88rem;">' +
                  '<span class="grey-text text-darken-2" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;margin-right:8px;">' + escapeHtml(f.name) + ' <span class="grey-text">(' + formatSize(f.size) + ')</span></span>' +
                  '<a href="#!" class="upload-cancel" data-i="' + i + '" title="Cancel upload" style="margin:0 8px;color:#e53935;font-size:1rem;">✕</a>' +
                  '<span class="upload-pct-' + i + ' grey-text" style="white-space:nowrap;"><b>0%</b></span>' +
                '</div>' +
                '<div class="progress" style="height:10px;margin-top:3px;"><div class="determinate upload-bar-' + i + '" style="width:0%;"></div></div>' +
                '</div>';
        });
        $('#uploadProgress').html(html).show();

        var remaining = list.length;
        var okCount = 0;
        currentUploads = [];
        function oneDone() {
            remaining--;
            if (remaining <= 0) {
                currentUploads = null;
                M.toast({html: okCount + ' of ' + list.length + ' file(s) uploaded'});
                $('#fileInput').val('');
                loadFiles();
                loadStorage();
                setTimeout(function () { $('#uploadProgress').hide(); }, 2000);
            }
        }

        // 并行上传，每个文件独立进度
        list.forEach(function (f, i) {
            var fd = new FormData();
            fd.append('file', f);
            currentUploads[i] = $.ajax({
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
                    $('.upload-cancel[data-i="' + i + '"]').hide();
                    $('.upload-bar-' + i).css('width', '100%');
                    $('.upload-pct-' + i).html('<b style="color:#2e7d32;">done</b>');
                    okCount++;
                    oneDone();
                },
                error: function (xhr, textStatus) {
                    $('.upload-cancel[data-i="' + i + '"]').hide();
                    if (textStatus === 'abort') {
                        // 手动中断：标记为 canceled，不算失败
                        $('.upload-pct-' + i).html('<b style="color:#fb8c00;">canceled</b>');
                    } else {
                        var msg = xhr.responseText || 'failed';
                        try { msg = JSON.parse(msg).errMsg || msg; } catch (ex) {}
                        $('.upload-pct-' + i).html('<b class="red-text">failed</b>');
                        M.toast({html: escapeHtml(f.name) + ': ' + msg});
                    }
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

    // 排序：切换排序键或方向后重新加载并渲染当前视图（搜索状态下沿用当前查询词）
    $('#sortKeySelect').on('change', function () {
        sortKey = $(this).val() || 'name';
        var q = $('#fileSearch').val() ? $('#fileSearch').val().trim() : '';
        if (q) { doSearch(q); } else { loadFiles(); }
    });
    $('#sortDirBtn').click(function (e) {
        e.preventDefault();
        sortDir = (sortDir === 'asc') ? 'desc' : 'asc';
        $(this).text(sortDir === 'asc' ? '↑' : '↓');
        var q = $('#fileSearch').val() ? $('#fileSearch').val().trim() : '';
        if (q) { doSearch(q); } else { loadFiles(); }
    });

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
    loadStorage();
});
