import { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { MailOutlined, LockOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Login = () => {
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const onFinish = async (values) => {
        setLoading(true);
        try {
            await login(values.email, values.password);
            message.success('Welcome back!');
            navigate('/');
        } catch (error) {
            const msg = error.response?.data?.error || error.response?.data || 'Login failed';
            message.error(typeof msg === 'string' ? msg : 'Invalid credentials');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page">
            <div className="auth-card">
                <div className="auth-logo">
                    <h1>💰 ExpenseTracker</h1>
                    <p>Sign in to manage your finances</p>
                </div>

                <Form layout="vertical" onFinish={onFinish} size="large">
                    <Form.Item
                        name="email"
                        rules={[
                            { required: true, message: 'Please enter your email' },
                            { type: 'email', message: 'Invalid email format' },
                        ]}
                    >
                        <Input prefix={<MailOutlined />} placeholder="Email address" />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[{ required: true, message: 'Please enter your password' }]}
                    >
                        <Input.Password prefix={<LockOutlined />} placeholder="Password" />
                    </Form.Item>

                    <Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            block
                            style={{ borderRadius: 8, height: 44 }}
                        >
                            Sign In
                        </Button>
                    </Form.Item>
                </Form>

                <div style={{ textAlign: 'center' }}>
                    <span style={{ color: 'var(--color-text-secondary)' }}>
                        Don't have an account?{' '}
                    </span>
                    <Link to="/register" style={{ color: 'var(--color-primary)', fontWeight: 500 }}>
                        Create one
                    </Link>
                </div>
            </div>
        </div>
    );
};

export default Login;
