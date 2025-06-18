import json
from flask import Flask, request, render_template_string
from threading import Lock

app = Flask(__name__)
data = []
data_lock = Lock()

HTML_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <title>实时监控数据</title>
    <meta charset="utf-8">
    <style>
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
        th { background: #eee; }
        th.time, td.time { width: 10%; }
        th.username, td.username { width: 10%; }
        th.label, td.label { width: 10%; }
        th.reason, td.reason { width: 20%; }
        th.history, td.history { width: 10%; }
    </style>
</head>
<body>
    <h2>实时监控数据</h2>
    <table id="data-table">
        <thead>
            <tr>
                <th class="time">Time</th>
                <th class="username">Username</th>
                <th class="label">Label</th>
                <th class="reason">Reason</th>
                <th class="history">History</th>
            </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
<script>
function fetchData() {
    fetch('/data')
        .then(response => response.json())
        .then(rows => {
            let tbody = document.querySelector("#data-table tbody");
            tbody.innerHTML = "";
            if (!Array.isArray(rows)) {
                tbody.innerHTML = '<tr><td colspan="5" style="color:red">数据格式错误：' + escapeHtml(JSON.stringify(rows)) + '</td></tr>';
                return;
            }
            for (let row of rows) {
                try {
                    let tr = document.createElement("tr");
                    let timeStr = new Date(row.time * 1000).toLocaleString();
                    let arr = row.history;
                    if (typeof arr === 'string') {
                        try { arr = JSON.parse(arr); } catch (e) { arr = row.history; }
                    }
                    let historyBtnHtml = `<a href=\"#\" onclick='showHistoryPopupHistory(${JSON.stringify(arr)})'>查看</a>`;
                    tr.innerHTML = `<td class=\"time\">${timeStr}</td>
                                    <td class=\"username\">${row.username}</td>
                                    <td class=\"label\"></td>
                                    <td class=\"reason\">${row.reason}</td>
                                    <td class=\"history\">${historyBtnHtml}</td>`;
                    // 设置label颜色
                    let labelTd = tr.querySelector('.label');
                    labelTd.textContent = row.label;
                    if (row.label === "危险") {
                        labelTd.style.color = 'red';
                        labelTd.style.fontWeight = 'bold';
                    } else if (row.label === "中等") {
                        labelTd.style.color = 'orange';
                        labelTd.style.fontWeight = 'bold';
                    }
                    tbody.appendChild(tr);
                } catch (err) {
                    let tr = document.createElement("tr");
                    tr.innerHTML = `<td colspan='5' style='color:red'>渲染出错: ${escapeHtml(err.toString())}</td>`;
                    tbody.appendChild(tr);
                }
            }
        })
        .catch(err => {
            let tbody = document.querySelector("#data-table tbody");
            tbody.innerHTML = `<tr><td colspan='5' style='color:red'>请求/解析出错: ${escapeHtml(err.toString())}</td></tr>`;
        });
}
setInterval(fetchData, 2000);
fetchData();

function showHistoryPopupHistory(historyArr) {
    let html = '<div style="font-family:monospace">';
    if (Array.isArray(historyArr)) {
        html += '<ul style="padding-left:20px;">';
        for (let item of historyArr) {
            html += `<li><b>${item.role}:</b> <span style="white-space:pre-wrap;">${escapeHtml(item.content)}</span></li>`;
        }
        html += '</ul>';
    } else {
        html += '<pre>' + escapeHtml(JSON.stringify(historyArr, null, 2)) + '</pre>';
    }
    html += '</div><br><button id="closeHistoryPopup">关闭</button>';
    let popup = document.createElement('div');
    popup.style.position = 'fixed';
    popup.style.left = '0';
    popup.style.top = '0';
    popup.style.width = '100vw';
    popup.style.height = '100vh';
    popup.style.background = 'rgba(0,0,0,0.3)';
    popup.style.display = 'flex';
    popup.style.alignItems = 'center';
    popup.style.justifyContent = 'center';
    popup.style.zIndex = 9999;
    let inner = document.createElement('div');
    inner.style.background = '#fff';
    inner.style.padding = '24px';
    inner.style.maxWidth = '600px';
    inner.style.width = '90vw';
    inner.style.maxHeight = '70vh';
    inner.style.overflow = 'auto';
    inner.style.borderRadius = '8px';
    inner.innerHTML = html;
    popup.appendChild(inner);
    document.body.appendChild(popup);
    document.getElementById('closeHistoryPopup').onclick = function() {
        document.body.removeChild(popup);
    };
}
function escapeHtml(text) {
    if (typeof text !== 'string') return text;
    return text.replace(/[&<>"']/g, function (c) {
        return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c];
    });
}
</script>
</body>
</html>
"""

@app.route('/', methods=['GET'])
def index():
    return render_template_string(HTML_PAGE)

@app.route('/report', methods=['POST'])
def report():
    item = request.get_json(force=True)
    required = ['time', 'username', 'label', 'reason', 'history']
    if not all(k in item for k in required):
        return {'error': 'Missing fields'}, 400
    # 校验 time 字段为数字
    try:
        item['time'] = float(item['time'])
    except (ValueError, TypeError):
        return {'error': 'time 字段必须为时间戳数字'}, 400
    with data_lock:
        data.insert(0, item)
        if len(data) > 1000:
            data.pop()
    return {'status': 'ok'}

@app.route('/data', methods=['GET'])
def get_data():
    with data_lock:
        return json.dumps(data), 200, {'Content-Type': 'application/json'}

"""
curl -v -X POST http://localhost:5000/report \
     -H "Content-Type: application/json" \
     -d '{"time": 1718700000, "username": "alice", "label": "中等", "reason": "test", "history": [{"role": "user", "content": "hello"}, {"role": "system", "content": "你好"}]}'
"""

if __name__ == '__main__':
    app.run(host='0.0.0.0', debug=True, port=5000)