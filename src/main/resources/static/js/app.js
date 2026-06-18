const { createApp, ref, reactive, computed, watch, onMounted, nextTick } = Vue;

const app = createApp({
    data() {
        return {
            // 视图路由: 'home' | 'flow' | 'detail'
            view: 'home',
            // 当前登录用户
            currentUser: null,
            // Flow 状态
            currentStep: 0,
            selectedConnectionId: null,
            analysisResult: null,
            currentSql: '',
            fieldRules: [],
            currentSqlScriptId: null,
            // Detail 状态
            detailTaskId: null,
            // Stepper 定义
            steps: [
                { key: 'connection', title: '数据库连接' },
                { key: 'sql', title: 'SQL 解析' },
                { key: 'rules', title: '规则配置' },
                { key: 'generate', title: '数据生成' },
            ],
        };
    },
    methods: {
        // 导航
        goHome() {
            this.view = 'home';
        },
        startNewTask() {
            this.view = 'flow';
            this.currentStep = 0;
            this.selectedConnectionId = null;
            this.analysisResult = null;
            this.currentSql = '';
            this.fieldRules = [];
            this.currentSqlScriptId = null;
        },
        viewTaskDetail(taskId) {
            this.detailTaskId = taskId;
            this.view = 'detail';
        },
        // Stepper 导航
        goToStep(index) {
            if (index <= this.currentStep) {
                this.currentStep = index;
                return;
            }
            if (index >= 1 && !this.selectedConnectionId) {
                Toast.warning('请先选择一个数据库连接');
                return;
            }
            if (index >= 2 && !this.analysisResult) {
                Toast.warning('请先完成 SQL 解析');
                return;
            }
            this.currentStep = index;
        },
        nextStep() {
            if (this.currentStep < this.steps.length - 1) {
                this.currentStep++;
            }
        },
        prevStep() {
            if (this.currentStep > 0) {
                this.currentStep--;
            }
        },
        // 数据回调
        onConnectionSelected(connectionId) {
            this.selectedConnectionId = connectionId;
        },
        onAnalysisDone(result, sql) {
            this.analysisResult = result;
            this.currentSql = sql;
            this.fieldRules = [];
        },
        onUpdateFieldRules(rules) {
            this.fieldRules = rules;
        },
        onSqlScriptSelected(scriptId) {
            this.currentSqlScriptId = scriptId;
        },
        // 退出登录
        logout() {
            API.logout();
        }
    },
    mounted() {
        // 检查登录状态
        if (!API.isLoggedIn()) {
            window.location.href = '/login.html';
            return;
        }
        // 加载用户信息
        this.currentUser = API.getCurrentUser();
        // 尝试从服务端刷新用户信息
        API.getMe().then(user => {
            this.currentUser = user;
            localStorage.setItem('user', JSON.stringify(user));
        }).catch(() => {
            // 如果获取失败，使用缓存的用户信息
        });
    },
});

app.component('home-page', HomePage);
app.component('schema-graph', SchemaGraph);
app.component('task-detail-page', TaskDetailPage);
app.component('connection-page', ConnectionPage);
app.component('sql-input-page', SqlInputPage);
app.component('rule-config-page', RuleConfigPage);
app.component('generate-page', GeneratePage);

app.mount('#app');
