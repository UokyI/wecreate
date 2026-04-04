// 添加键盘快捷键监听器
document.addEventListener('keydown', function(event) {
    // 检查是否按下了 F2 键
    if (event.key === 'F2') {
        event.preventDefault();
        window.location.href = '/';
    }
    // 检查是否按下了 F8 键切换主题
    else if (event.key === 'F8') {
        event.preventDefault();
        document.body.classList.toggle('dark-theme');
        // 保存主题偏好到 localStorage
        const isDarkTheme = document.body.classList.contains('dark-theme');
        localStorage.setItem('theme', isDarkTheme ? 'dark' : 'light');
    }
});

// 页面加载时恢复主题设置
window.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark') {
        document.body.classList.add('dark-theme');
    }
});

let currentPage = 1;
const pageSize = 10;
// 保存当前的查询条件
let currentQuery = {};
// 保存项目映射（id -> projectName）
let projectMap = {};

// 页面加载时获取执行记录列表
window.onload = function() {
    loadAllIps(); // 初始化IP列表
    loadAllProjects(); // 初始化项目列表
    loadPrompts();

    // 初始化时间按钮状态（默认选中"自定义"，但不触发查询）
    const now = new Date();
    document.getElementById('timeCustomBtn').classList.add('active');
    document.getElementById('customTimeRow').style.display = 'grid';

    // 添加评分选项点击事件监听器
    document.addEventListener('click', function(e) {
        if (e.target.classList.contains('score-option')) {
            // 移除其他选项的选中状态
            document.querySelectorAll('.score-option').forEach(option => {
                option.classList.remove('selected');
            });

            // 为当前点击的选项添加选中状态
            e.target.classList.add('selected');

            // 设置隐藏输入框的值
            document.getElementById('scoreValue').value = e.target.getAttribute('data-value');
        }
    });
};

// 获取所有IP地址并填充到下拉框
function loadAllIps() {
    fetch('/api/prompts/ips')
        .then(response => response.json())
        .then(ips => {
            const ipSelect = document.getElementById('searchIp');
            // 保留"全部IP"选项，清除其他选项
            ipSelect.innerHTML = '<option value="">全部IP</option>';
            // 添加所有IP选项
            ips.forEach(ip => {
                const option = document.createElement('option');
                option.value = ip;
                option.textContent = ip;
                ipSelect.appendChild(option);
            });
        })
        .catch(error => {
            console.error('获取IP列表失败:', error);
        });
}

// 获取所有项目并填充到下拉框
function loadAllProjects() {
    fetch('/api/projects')
        .then(response => response.json())
        .then(result => {
            if (result.code === 200 && result.data) {
                const projectSelect = document.getElementById('searchProject');
                // 保留"全部项目"选项，清除其他选项
                projectSelect.innerHTML = '<option value="">全部项目</option>';
                // 清空并重建项目映射
                projectMap = {};
                // 添加所有项目选项
                result.data.forEach(project => {
                    const option = document.createElement('option');
                    const projectIdStr = String(project.id);
                    option.value = projectIdStr;
                    option.textContent = project.projectName;
                    projectSelect.appendChild(option);
                    // 保存项目映射
                    projectMap[projectIdStr] = project.projectName;
                });
            }
        })
        .catch(error => {
            console.error('获取项目列表失败:', error);
        });
}

// 加载执行记录列表
function loadPrompts(page = 1) {
    currentPage = page;
    
    // 使用当前保存的查询条件
    const queryDTO = Object.assign({}, currentQuery, {
        pageNum: page,
        pageSize: pageSize
    });
    
    fetchPost('/api/prompts/page', queryDTO);
}

// 搜索执行记录
function searchPrompts() {
    const keyword = document.getElementById('searchKeyword').value;
    const ip = document.getElementById('searchIp').value;
    const projectId = document.getElementById('searchProject').value;
    const startTime = document.getElementById('startTime').value;
    const endTime = document.getElementById('endTime').value;

    // 转换时间格式
    let formattedStartTime = null;
    let formattedEndTime = null;

    if (startTime) {
        // 将 YYYY-MM-DDTHH:mm 格式转换为 yyyy-MM-dd HH:mm:ss
        formattedStartTime = startTime.replace('T', ' ') + ':00';
    }

    if (endTime) {
        // 将 YYYY-MM-DDTHH:mm 格式转换为 yyyy-MM-dd HH:mm:ss
        formattedEndTime = endTime.replace('T', ' ') + ':59';
    }

    const queryDTO = {
        keyword: keyword,
        ip: ip,
        projectId: projectId,
        startTime: formattedStartTime,
        endTime: formattedEndTime,
        pageNum: 1,
        pageSize: pageSize
    };

    // 保存当前查询条件
    currentQuery = {
        keyword: keyword,
        ip: ip,
        projectId: projectId,
        startTime: formattedStartTime,
        endTime: formattedEndTime
    };

    currentPage = 1;
    fetchPost('/api/prompts/page', queryDTO);
}

// 清空搜索条件
function clearSearchConditions() {
    document.getElementById('searchKeyword').value = '';
    document.getElementById('searchIp').value = '';
    document.getElementById('searchProject').value = '';
    document.getElementById('startTime').value = '';
    document.getElementById('endTime').value = '';

    // 移除所有时间按钮的激活状态，选中"自定义"
    document.querySelectorAll('.time-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById('timeCustomBtn').classList.add('active');
}

// 设置快捷时间范围
function setTimeRange(range) {
    const now = new Date();
    
    // 移除所有按钮的激活状态
    document.querySelectorAll('.time-btn').forEach(btn => btn.classList.remove('active'));
    
    // 激活对应的按钮
    if (range === 'custom') {
        document.getElementById('timeCustomBtn').classList.add('active');
        // 清空时间，让用户手动输入
        document.getElementById('startTime').value = '';
        document.getElementById('endTime').value = '';
    } else if (range === '1d') {
        // 最近一天
        document.getElementById('time1dBtn').classList.add('active');
        const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        document.getElementById('startTime').value = formatDateForInput(oneDayAgo);
        document.getElementById('endTime').value = formatDateForInput(now);
        // 自动查询
        searchPrompts();
    } else if (range === '1w') {
        // 最近一周
        document.getElementById('time1wBtn').classList.add('active');
        const oneWeekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        document.getElementById('startTime').value = formatDateForInput(oneWeekAgo);
        document.getElementById('endTime').value = formatDateForInput(now);
        // 自动查询
        searchPrompts();
    } else if (range === '1m') {
        // 最近一月
        document.getElementById('time1mBtn').classList.add('active');
        const oneMonthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        document.getElementById('startTime').value = formatDateForInput(oneMonthAgo);
        document.getElementById('endTime').value = formatDateForInput(now);
        // 自动查询
        searchPrompts();
    }
}

// 格式化日期用于 datetime-local 输入框
function formatDateForInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
}

// 通用POST请求函数
function fetchPost(url, data) {
    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
    .then(response => response.json())
    .then(data => {
        renderPromptsTable(data.records);
        // 保存总记录数以便在renderPagination中使用
        renderPagination.totalRecords = data.total;
        // 更新总记录数显示
        const totalCountElement = document.getElementById('totalCount');
        if (totalCountElement) {
            totalCountElement.textContent = `共 ${data.total} 条记录`;
        }
        renderPagination(data.pages, data.current);
    })
    .catch(error => {
        console.error('Error:', error);
        alert('操作失败: ' + error.message);
    });
}

// 渲染执行记录表格
function renderPromptsTable(prompts) {
    const tbody = document.getElementById('promptsTableBody');
    tbody.innerHTML = '';
    
    prompts.forEach(prompt => {
        const row = document.createElement('tr');
        
        // 处理时间格式（包含毫秒）
        const createTime = prompt.createTime;
        
        // 提示词内容显示（最多3行）
        const contentCell = `
            <div class="prompt-content">${prompt.promptContent || ''}</div>
        `;
        
        // 将执行时间从毫秒转换为时分秒格式
        let executionTimeFormatted = '';
        if (prompt.executionTime !== undefined && prompt.executionTime !== null) {
            const totalSeconds = Math.floor(prompt.executionTime / 1000);
            const hours = Math.floor(totalSeconds / 3600);
            const minutes = Math.floor((totalSeconds % 3600) / 60);
            const seconds = totalSeconds % 60;
            
            executionTimeFormatted = 
                String(hours).padStart(2, '0') + ':' +
                String(minutes).padStart(2, '0') + ':' +
                String(seconds).padStart(2, '0');
        }
        
        // 显示评分信息
        let usabilityDisplay = '';
        if (prompt.score !== undefined && prompt.score !== null) {
            // 计算百分比 (评分范围是0-10，转换为0-100%)
            const percentage = Math.round(prompt.score * 10);
            
            // 根据分数决定颜色：50%以上为绿色，以下为红色
            const color = percentage >= 50 ? 'green' : 'red';
            
            // 显示百分比形式的分数
            usabilityDisplay = `<span style="color: ${color}; font-weight: bold;">${percentage}%</span>`;
        } else {
            // 如果没有评分，显示"未评分"
            usabilityDisplay = '<span>未评分</span>';
        }
        
        const scoreUserDisplay = prompt.scoreUser !== undefined && prompt.scoreUser !== null ? 
            `<div class="score-user" title="${prompt.scoreUser}">${prompt.scoreUser}</div>` : '';

        let detailButtonHtml = '';
        detailButtonHtml = `<button class="btn btn-view" onclick="viewDetails('${prompt.id}')">详情</button>`;
        
        // 使用字符串拼接避免ID精度问题
        var actionsHtml = '<td class="actions">';
        actionsHtml += detailButtonHtml; // 根据appId条件显示/隐藏Detail按钮
        actionsHtml += '<button class="btn btn-score" onclick="openScoreModal(\'' + prompt.traceId + '\')" style="display: none;">Score</button>';
        actionsHtml += '<button class="btn btn-delete" onclick="deletePrompt(\'' + prompt.id + '\')">删除</button>';
        actionsHtml += '</td>';
        
        row.innerHTML = `
            <td style="display: none;">${prompt.id || ''}</td>
            <td>${createTime}</td>
            <td>${getProjectName(prompt.projectId)}</td>
            <td>${contentCell}</td>
            <td>${executionTimeFormatted}</td>
            <td style="display: none;">${prompt.workDirectory || ''}</td>
            <td>${prompt.traceId || ''}</td>
            <td>${prompt.requestIp || ''}</td>
            <td style="display: none;">${usabilityDisplay}</td>
            <td style="display: none;">${scoreUserDisplay}</td>
            ${actionsHtml}
        `;

        tbody.appendChild(row);
    });
}

// 渲染分页控件
function renderPagination(totalPages, currentPage) {
    const pagination = document.getElementById('pagination');
    pagination.innerHTML = '';
    
    // 上一页按钮
    if (currentPage > 1) {
        const prevButton = document.createElement('button');
        prevButton.textContent = '上一页';
        prevButton.onclick = () => loadPrompts(currentPage - 1);
        pagination.appendChild(prevButton);
    }
    
    // 页码按钮 - 只显示部分页码，避免过多页码导致界面混乱
    const maxVisiblePages = 10; // 最大显示页码数
    let startPage, endPage;
    
    if (totalPages <= maxVisiblePages) {
        // 总页数少于最大显示页数，显示所有页码
        startPage = 1;
        endPage = totalPages;
    } else {
        // 总页数大于最大显示页数，计算显示范围
        const maxPagesBeforeCurrent = Math.floor(maxVisiblePages / 2);
        const maxPagesAfterCurrent = Math.ceil(maxVisiblePages / 2) - 1;
        
        if (currentPage <= maxPagesBeforeCurrent) {
            // 当前页靠近开头
            startPage = 1;
            endPage = maxVisiblePages;
        } else if (currentPage + maxPagesAfterCurrent >= totalPages) {
            // 当前页靠近结尾
            startPage = totalPages - maxVisiblePages + 1;
            endPage = totalPages;
        } else {
            // 当前页在中间
            startPage = currentPage - maxPagesBeforeCurrent;
            endPage = currentPage + maxPagesAfterCurrent;
        }
    }
    
    // 显示第一页按钮和省略号（如果需要）
    if (startPage > 1) {
        const firstPageButton = document.createElement('button');
        firstPageButton.textContent = '1';
        firstPageButton.onclick = () => loadPrompts(1);
        pagination.appendChild(firstPageButton);
        
        if (startPage > 2) {
            const ellipsis = document.createElement('span');
            ellipsis.textContent = '...';
            ellipsis.style.margin = '0 5px';
            pagination.appendChild(ellipsis);
        }
    }
    
    // 显示页码按钮
    for (let i = startPage; i <= endPage; i++) {
        const pageButton = document.createElement('button');
        pageButton.textContent = i;
        pageButton.className = i === currentPage ? 'active' : '';
        pageButton.onclick = () => loadPrompts(i);
        pagination.appendChild(pageButton);
    }
    
    // 显示最后一页按钮和省略号（如果需要）
    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            const ellipsis = document.createElement('span');
            ellipsis.textContent = '...';
            ellipsis.style.margin = '0 5px';
            pagination.appendChild(ellipsis);
        }
        
        const lastPageButton = document.createElement('button');
        lastPageButton.textContent = totalPages;
        lastPageButton.onclick = () => loadPrompts(totalPages);
        pagination.appendChild(lastPageButton);
    }
    
    // 下一页按钮
    if (currentPage < totalPages) {
        const nextButton = document.createElement('button');
        nextButton.textContent = '下一页';
        nextButton.onclick = () => loadPrompts(currentPage + 1);
        pagination.appendChild(nextButton);
    }
    
    // 添加总记录数在同一行显示
    const totalInfo = document.createElement('span');
    totalInfo.textContent = ` 共 ${renderPagination.totalRecords || 0} 条记录`;
    totalInfo.style.marginLeft = '10px';
    totalInfo.style.fontSize = '14px';
    totalInfo.style.lineHeight = '32px'; // 与分页按钮高度一致
    totalInfo.style.display = 'inline-block';
    totalInfo.style.verticalAlign = 'top';
    pagination.appendChild(totalInfo);
}

// 查看详情
function viewDetails(promptId) {
    console.log("正在获取ID为 " + promptId + " 的日志详情");
    fetch(`/api/prompts/logs`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({id: promptId})
    })
        .then(response => {
            console.log("收到响应状态: " + response.status);
            return response.json();
        })
        .then(logs => {
            console.log("收到日志数据: ", logs);
            const logsContainer = document.getElementById('logsContainer');
            logsContainer.innerHTML = '';
            
            // 首先获取提示词的详细信息
            fetch(`/api/prompts/${promptId}`)
                .then(response => response.json())
                .then(prompt => {
                    // 检查是否有有效的描述信息
                    if (prompt.description !== undefined && prompt.description !== null && prompt.description.trim() !== '') {
                        // 创建描述信息展示区域
                        const descriptionContainer = document.createElement('div');
                        descriptionContainer.className = 'basic-data-container';

                        // 构建描述信息显示内容
                        let descriptionHtml = `
                            <div class="basic-data-section">
                                <h3>描述信息</h3>
                                <div class="data-item">
                                    <div class="prompt-content-detail">${prompt.description}</div>
                                </div>
                            </div>
                        `;

                        descriptionContainer.innerHTML = descriptionHtml;
                        logsContainer.appendChild(descriptionContainer);
                    }

                    // 检查是否有有效的Git变更信息
                    const hasValidGitInfo = 
                        (prompt.gitChangedFiles !== undefined && prompt.gitChangedFiles !== null) ||
                        (prompt.gitInsertions !== undefined && prompt.gitInsertions !== null) ||
                        (prompt.gitDeletions !== undefined && prompt.gitDeletions !== null) ||
                        (prompt.gitTotalChanges !== undefined && prompt.gitTotalChanges !== null);
                    
                    // 只有当存在有效Git信息时才显示
                    if (hasValidGitInfo) {
                        // 创建Git变更信息展示区域
                        const gitDataContainer = document.createElement('div');
                        gitDataContainer.className = 'basic-data-container';
                        
                        // 构建Git变更信息显示内容
                        let gitDataHtml = `
                            <div class="basic-data-section">
                                <h3>Git变更信息</h3>
                                <div class="basic-data-grid">
                                    <div class="data-item">
                                        <span class="data-label">变更文件数:</span>
                                        <span class="data-value">${prompt.gitChangedFiles !== null ? prompt.gitChangedFiles : '无数据'}</span>
                                    </div>
                                    <div class="data-item">
                                        <span class="data-label">新增行数:</span>
                                        <span class="data-value">${prompt.gitInsertions !== null ? prompt.gitInsertions : '无数据'}</span>
                                    </div>
                                    <div class="data-item">
                                        <span class="data-label">删除行数:</span>
                                        <span class="data-value">${prompt.gitDeletions !== null ? prompt.gitDeletions : '无数据'}</span>
                                    </div>
                                    <div class="data-item">
                                        <span class="data-label">总变更行数:</span>
                                        <span class="data-value">${prompt.gitTotalChanges !== null ? prompt.gitTotalChanges : '无数据'}</span>
                                    </div>
                                </div>
                            </div>
                        `;
                        
                        gitDataContainer.innerHTML = gitDataHtml;
                        logsContainer.appendChild(gitDataContainer);
                    }
                    
                    // 不再添加"日志详情"标题
                    
                    if (logs.length === 0) {
                        logsContainer.innerHTML += '<p class="no-logs">暂无相关日志</p>';
                    } else {
                        logs.forEach(log => {
                            const logElement = document.createElement('div');
                            logElement.className = 'log-item';

                            // 处理时间格式
                            const createTime = log.createTime ? new Date(log.createTime).toLocaleString('zh-CN', {
                                year: 'numeric',
                                month: '2-digit',
                                day: '2-digit',
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit'
                            }) : '';

                            logElement.innerHTML = `
                                <div class="log-header">
                                    类型: ${log.type || ''} | 时间: ${createTime}
                                </div>
                                <div class="log-content">${log.content || ''}</div>
                            `;
                            
                            logsContainer.appendChild(logElement);
                        });
                    }
                    
                    document.getElementById('detailModal').style.display = 'block';
                })
                .catch(error => {
                    console.error('Error loading prompt details:', error);
                    // 如果获取详细信息失败，仍然显示日志
                    showLogContentOnly(logs, logsContainer);
                });
        })
        .catch(error => {
            console.error('Error loading logs:', error);
            const logsContainer = document.getElementById('logsContainer');
            logsContainer.innerHTML = `<p class="error-message">加载日志详情失败: ${error.message}</p>`;
            document.getElementById('detailModal').style.display = 'block';
        });
}

// 关闭详情模态框
function closeModal() {
    document.getElementById('detailModal').style.display = 'none';
}

// 打开评分模态框
function openScoreModal(traceId) {
    document.getElementById('scoreTraceId').value = traceId;
    document.getElementById('scoreValue').value = '';
    
    // 清除之前选中的分数
    const scoreOptions = document.querySelectorAll('.score-option');
    scoreOptions.forEach(option => option.classList.remove('selected'));
    
    document.getElementById('scoreModal').style.display = 'block';
}

// 关闭评分模态框
function closeScoreModal() {
    document.getElementById('scoreModal').style.display = 'none';
}

// 处理评分表单提交
document.getElementById('scoreForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const traceId = document.getElementById('scoreTraceId').value;
    const score = document.getElementById('scoreValue').value;
    
    // 发送评分请求
    fetch('/api/prompts/score', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            traceId: traceId,
            score: score
        })
    })
    .then(response => response.json())
    .then(result => {
        if (result.code === 200) {
            alert('评分成功');
            closeScoreModal();
            loadPrompts(currentPage); // 重新加载当前页
        } else {
            alert('评分失败: ' + result.msg);
        }
    })
    .catch(error => {
        console.error('评分失败:', error);
        alert('评分失败: ' + error.message);
    });
});

// 删除提示词
function deletePrompt(promptId) {
    if (confirm('确定要删除这个提示词吗？')) {
        fetch(`/api/prompts/${promptId}`, {
            method: 'DELETE'
        })
        .then(response => response.json())
        .then(result => {
            if (result) {
                alert('删除成功');
                loadPrompts(currentPage); // 重新加载当前页
            } else {
                alert('删除失败');
            }
        })
        .catch(error => {
            console.error('Error deleting prompt:', error);
            alert('删除提示词失败');
        });
    }
}

// 点击模态框外部区域关闭模态框
window.onclick = function(event) {
    const detailModal = document.getElementById('detailModal');
    const scoreModal = document.getElementById('scoreModal');
    if (event.target == detailModal) {
        closeModal();
    } else if (event.target == scoreModal) {
        closeScoreModal();
    }
}

// 格式化执行时间
function formatExecutionTime(executionTime) {
    if (executionTime === undefined || executionTime === null) {
        return '无数据';
    }
    
    const totalSeconds = Math.floor(executionTime / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    
    return hours.toString().padStart(2, '0') + ':' +
           minutes.toString().padStart(2, '0') + ':' +
           seconds.toString().padStart(2, '0');
}

// 格式化可用性评分
function formatUsabilityScore(score) {
    if (score === undefined || score === null) {
        return '未评分';
    }
    
    // 计算百分比 (评分范围是0-10，转换为0-100%)
    const percentage = Math.round(score * 10);
    
    // 根据分数决定颜色：50%以上为绿色，以下为红色
    const color = percentage >= 50 ? 'green' : 'red';
    
    // 显示百分比形式的分数
    return `<span style="color: ${color}; font-weight: bold;">${percentage}%</span>`;
}

// 仅显示日志内容（当获取详细信息失败时使用）
function showLogContentOnly(logs, container) {
    container.innerHTML = '';

    // 不再添加"日志详情"标题

    if (logs.length === 0) {
        container.innerHTML += '<p class="no-logs">暂无相关日志</p>';
    } else {
        logs.forEach(log => {
            const logElement = document.createElement('div');
            logElement.className = 'log-item';

            // 处理时间格式
            const createTime = log.createTime ? new Date(log.createTime).toLocaleString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            }) : '';

            logElement.innerHTML = `
                <div class="log-header">
                    类型: ${log.type || ''} | 时间: ${createTime}
                </div>
                <div class="log-content">${log.content || ''}</div>
            `;

            container.appendChild(logElement);
        });
    }

    document.getElementById('detailModal').style.display = 'block';
}

// 根据项目ID获取项目名称
function getProjectName(projectId) {
    if (!projectId) {
        return '<span style="color: #999;">-</span>';
    }
    const projectIdStr = String(projectId);
    return projectMap[projectIdStr] || `<span title="${projectIdStr}">${projectIdStr}</span>`;
}