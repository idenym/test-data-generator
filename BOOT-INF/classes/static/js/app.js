const { createApp, ref, reactive, computed, watch, onMounted, nextTick } = Vue;

const app = createApp({
    data() {
        return {
            currentStep: 0,
            showHistory: false,
            selectedConnectionId: null,
            analysisResult: null,
            currentSql: '',
            fieldRules: [],
            steps: [
                { key: 'connection', title: '数据库连接' },
                { key: 'sql', title: 'SQL 解析' },
                { key: 'rules', title: '规则配置' },
                { key: 'generate', title: '数据生成' },
            ],
        };
    },
    methods: {
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
        toggleHistory() {
            this.showHistory = !this.showHistory;
        },
        backToFlow() {
            this.showHistory = false;
        },
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
    },
});

app.component('connection-page', ConnectionPage);
app.component('sql-input-page', SqlInputPage);
app.component('rule-config-page', RuleConfigPage);
app.component('generate-page', GeneratePage);
app.component('history-page', HistoryPage);

app.mount('#app');
