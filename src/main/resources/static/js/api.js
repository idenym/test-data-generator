const API = {
    baseURL: '/api/v1',

    // 检查是否已登录
    isLoggedIn() {
        return !!localStorage.getItem('token');
    },

    // 获取当前 token
    getToken() {
        return localStorage.getItem('token');
    },

    // 设置 token
    setToken(token) {
        localStorage.setItem('token', token);
    },

    // 清除登录信息
    clearAuth() {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    },

    // 获取当前用户信息（从 localStorage 缓存）
    getCurrentUser() {
        const userStr = localStorage.getItem('user');
        return userStr ? JSON.parse(userStr) : null;
    },

    // 跳转到登录页
    redirectToLogin() {
        this.clearAuth();
        window.location.href = '/login.html';
    },

    async request(method, path, data) {
        try {
            const config = {
                method,
                url: this.baseURL + path,
            };
            if (data && (method === 'post' || method === 'put')) {
                config.data = data;
            }
            // 自动附加 Authorization header
            const token = this.getToken();
            if (token) {
                config.headers = config.headers || {};
                config.headers['Authorization'] = 'Bearer ' + token;
            }
            const response = await axios(config);
            return response.data;
        } catch (error) {
            // 401 响应时跳转登录页
            if (error.response && error.response.status === 401) {
                this.redirectToLogin();
                throw new Error('登录已过期，请重新登录');
            }
            const msg = error.response?.data?.message || error.message || '请求失败';
            throw new Error(msg);
        }
    },

    // Auth APIs
    login(data) { return this.request('post', '/auth/login', data); },
    register(data) { return this.request('post', '/auth/register', data); },
    getMe() { return this.request('get', '/auth/me'); },
    logout() {
        this.clearAuth();
        this.redirectToLogin();
    },

    // Connection APIs
    listConnections() { return this.request('get', '/connections'); },
    createConnection(data) { return this.request('post', '/connections', data); },
    updateConnection(id, data) { return this.request('put', `/connections/${id}`, data); },
    deleteConnection(id) { return this.request('delete', `/connections/${id}`); },
    testConnection(data) { return this.request('post', '/connections/test', data); },

    // Metadata APIs
    listTables(connId) { return this.request('get', `/metadata/${connId}/tables`); },
    getTableMetadata(connId, tableName) { return this.request('get', `/metadata/${connId}/tables/${tableName}`); },

    // SQL Analysis
    analyzeSql(data) { return this.request('post', '/sql/analyze', data); },

    // Rule APIs
    listRules() { return this.request('get', '/rules'); },
    createRule(data) { return this.request('post', '/rules', data); },
    updateRule(id, data) { return this.request('put', `/rules/${id}`, data); },
    deleteRule(id) { return this.request('delete', `/rules/${id}`); },
    suggestRules(connId, tableName) { return this.request('post', `/rules/suggest/${connId}/${tableName}`); },
    getFieldRuleHistory(tableName, columnName, sqlScriptId) {
        let url = `/rules/history?tableName=${encodeURIComponent(tableName)}&columnName=${encodeURIComponent(columnName)}`;
        if (sqlScriptId) url += `&sqlScriptId=${sqlScriptId}`;
        return this.request('get', url);
    },
    autoFillRules(data) { return this.request('post', '/rules/auto-fill', data); },

    // SQL Script APIs
    listScripts() { return this.request('get', '/scripts'); },
    createScript(data) { return this.request('post', '/scripts', data); },
    updateScript(id, data) { return this.request('put', `/scripts/${id}`, data); },
    deleteScript(id) { return this.request('delete', `/scripts/${id}`); },

    // Data Generation
    getAvailableModels() { return this.request('get', '/generate/models'); },
    previewData(data) { return this.request('post', '/generate/preview', data); },
    submitPreview(data) { return this.request('post', '/generate/preview/submit', data); },
    getPreviewStatus(taskId) { return this.request('get', `/generate/preview/status/${taskId}`); },
    getPreviewProgressByDbId(dbTaskId) { return this.request('get', `/generate/preview/progress/${dbTaskId}`); },
    cancelPreview(taskId) { return this.request('post', `/generate/preview/cancel/${taskId}`); },
    executeGeneration(data) { return this.request('post', '/generate/execute', data); },
    writePreviewData(data) { return this.request('post', '/generate/write', data); },
    regenerateColumns(data) { return this.request('post', '/generate/regenerate-columns', data); },

    // History
    listHistory() { return this.request('get', '/history'); },
    getHistory(id) { return this.request('get', `/history/${id}`); },
    getGeneratedData(id) { return this.request('get', `/history/${id}/data`); },
    deleteHistory(id) { return this.request('delete', `/history/${id}`); },
    getAdoptionStatistics() { return this.request('get', '/history/statistics'); },
};
