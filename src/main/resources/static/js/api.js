const API = {
    baseURL: '/api/v1',

    async request(method, path, data) {
        try {
            const config = {
                method,
                url: this.baseURL + path,
            };
            if (data && (method === 'post' || method === 'put')) {
                config.data = data;
            }
            const response = await axios(config);
            return response.data;
        } catch (error) {
            const msg = error.response?.data?.message || error.message || '请求失败';
            throw new Error(msg);
        }
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

    // SQL Script APIs
    listScripts() { return this.request('get', '/scripts'); },
    createScript(data) { return this.request('post', '/scripts', data); },
    updateScript(id, data) { return this.request('put', `/scripts/${id}`, data); },
    deleteScript(id) { return this.request('delete', `/scripts/${id}`); },

    // Data Generation
    getAvailableModels() { return this.request('get', '/generate/models'); },
    previewData(data) { return this.request('post', '/generate/preview', data); },
    executeGeneration(data) { return this.request('post', '/generate/execute', data); },
    writePreviewData(data) { return this.request('post', '/generate/write', data); },

    // History
    listHistory() { return this.request('get', '/history'); },
    getHistory(id) { return this.request('get', `/history/${id}`); },
    deleteHistory(id) { return this.request('delete', `/history/${id}`); },
};
