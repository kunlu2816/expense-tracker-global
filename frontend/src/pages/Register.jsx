import { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { MailOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Register = () => {
    const [loading, setLoading] = useState(false);
    const { register } = useAuth();
    const navigate = useNavigate();

    const onFinish = async (values) => {
        setLoading(true);
        try {
            await register(values.email, values.password, values.fullName);
            message.success('Account created! Please sign in.');
            navigate('/login');
        } catch (error) {
            const errors = error.response?.data;
            if (typeof errors === 'object') {
                Object.values(errors).forEach((msg) => message.error(msg));
            } else {
                message.error(typeof errors === 'string' ? errors : 'Registration failed');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page">
            <div className="auth-card">
                <div className="auth-logo">
                    <h1>💰 ExpenseTracker</h1>
                    <p>Create your account to get started</p>
                </div>

                <Form layout="vertical" onFinish={onFinish} size="large">
                    <Form.Item
                        name="fullName"
                        rules={[{ required: true, message: 'Please enter your name' }]}
                    >
                        <Input prefix={<UserOutlined />} placeholder="Full name" />
                    </Form.Item>

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
                        rules={[
                            { required: true, message: 'Please enter a password' },
                            { min: 8, message: 'Password must be at least 8 characters' },
                        ]}
                    >
                        <Input.Password prefix={<LockOutlined />} placeholder="Password (min 8 chars)" />
                    </Form.Item>

                    <Form.Item
                        name="confirmPassword"
                        dependencies={['password']}
                        rules={[
                            { required: true, message: 'Please confirm your password' },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('password') === value) return Promise.resolve();
                                    return Promise.reject(new Error('Passwords do not match'));
                                },
                            }),
                        ]}
                    >
                        <Input.Password prefix={<LockOutlined />} placeholder="Confirm password" />
                    </Form.Item>

                    <Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            block
                            style={{ borderRadius: 8, height: 44 }}
                        >
                            Create Account
                        </Button>
                    </Form.Item>
                </Form>

                <div style={{ textAlign: 'center' }}>
                    <span style={{ color: 'var(--color-text-secondary)' }}>
                        Already have an account?{' '}
                    </span>
                    <Link to="/login" style={{ color: 'var(--color-primary)', fontWeight: 500 }}>
                        Sign in
                    </Link>
                </div>
            </div>
        </div>
    );
};

export default Register;
