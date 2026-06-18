const { createApp, ref, reactive } = Vue;

const app = createApp({
    data() {
        return {
            activeTab: 'login',
            loading: false,
            errorMsg: '',
            successMsg: '',
            loginForm: {
                username: '',
                password: ''
            },
            registerForm: {
                username: '',
                nickname: '',
                password: '',
                confirmPassword: ''
            }
        };
    },
    methods: {
        async handleLogin() {
            this.errorMsg = '';
            this.successMsg = '';

            if (!this.loginForm.username || !this.loginForm.password) {
                this.errorMsg = '请填写用户名和密码';
                return;
            }

            this.loading = true;
            try {
                const resp = await axios.post('/api/v1/auth/login', {
                    username: this.loginForm.username,
                    password: this.loginForm.password
                });

                // 存储 token 和用户信息
                localStorage.setItem('token', resp.data.token);
                localStorage.setItem('user', JSON.stringify(resp.data.user));

                // 跳转到主页
                window.location.href = '/index.html';
            } catch (e) {
                this.errorMsg = e.response?.data?.message || e.message || '登录失败';
            } finally {
                this.loading = false;
            }
        },

        async handleRegister() {
            this.errorMsg = '';
            this.successMsg = '';

            if (!this.registerForm.username || !this.registerForm.password) {
                this.errorMsg = '请填写用户名和密码';
                return;
            }
            if (this.registerForm.password.length < 6) {
                this.errorMsg = '密码至少 6 位';
                return;
            }
            if (this.registerForm.password !== this.registerForm.confirmPassword) {
                this.errorMsg = '两次输入的密码不一致';
                return;
            }

            this.loading = true;
            try {
                await axios.post('/api/v1/auth/register', {
                    username: this.registerForm.username,
                    password: this.registerForm.password,
                    nickname: this.registerForm.nickname || null
                });

                this.successMsg = '注册成功，请登录';
                // 预填用户名并切换到登录 tab
                this.loginForm.username = this.registerForm.username;
                this.loginForm.password = '';
                this.activeTab = 'login';
            } catch (e) {
                this.errorMsg = e.response?.data?.message || e.message || '注册失败';
            } finally {
                this.loading = false;
            }
        }
    },
    watch: {
        activeTab() {
            this.errorMsg = '';
            this.successMsg = '';
        }
    }
});

app.mount('#app');
