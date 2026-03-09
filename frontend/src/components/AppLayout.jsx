import { useState } from 'react';
import { Layout, Menu, Button, Input, Badge, Avatar, Dropdown } from 'antd';
import {
    DashboardOutlined,
    TransactionOutlined,
    AppstoreOutlined,
    BankOutlined,
    UserOutlined,
    LogoutOutlined,
    SearchOutlined,
    BellOutlined,
    PlusOutlined,
    WalletOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const { Sider, Header, Content } = Layout;

const AppLayout = () => {
    const [collapsed, setCollapsed] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();

    const menuItems = [
        { key: '/', icon: <DashboardOutlined />, label: 'Dashboard' },
        { key: '/transactions', icon: <TransactionOutlined />, label: 'Transactions' },
        { key: '/categories', icon: <AppstoreOutlined />, label: 'Categories' },
        { key: '/banks', icon: <BankOutlined />, label: 'Bank Accounts' },
        { key: '/profile', icon: <UserOutlined />, label: 'Profile' },
    ];

    const userMenuItems = [
        { key: 'profile', icon: <UserOutlined />, label: 'Profile', onClick: () => navigate('/profile') },
        { type: 'divider' },
        { key: 'logout', icon: <LogoutOutlined />, label: 'Logout', danger: true, onClick: () => { logout(); navigate('/login'); } },
    ];

    return (
        <Layout className="app-layout" style={{ minHeight: '100vh' }}>
            {/* Sidebar */}
            <Sider
                trigger={null}
                collapsible
                collapsed={collapsed}
                width={240}
                style={{
                    background: '#fff',
                    borderRight: '1px solid var(--color-border)',
                    position: 'fixed',
                    height: '100vh',
                    left: 0,
                    top: 0,
                    zIndex: 100,
                }}
            >
                {/* Logo */}
                <div className="sidebar-logo">
                    {collapsed ? (
                        <WalletOutlined className="logo-icon" />
                    ) : (
                        <h2>💰 ExpenseTracker</h2>
                    )}
                </div>

                {/* Navigation */}
                <Menu
                    mode="inline"
                    selectedKeys={[location.pathname]}
                    items={menuItems}
                    onClick={({ key }) => navigate(key)}
                    style={{ borderInlineEnd: 'none', marginTop: 8 }}
                />
            </Sider>

            {/* Main Area */}
            <Layout style={{ marginLeft: collapsed ? 80 : 240, transition: 'margin-left 0.2s' }}>
                {/* Header */}
                <Header style={{
                    background: '#fff',
                    borderBottom: '1px solid var(--color-border)',
                    padding: '0 24px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    position: 'sticky',
                    top: 0,
                    zIndex: 99,
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                        <Button
                            type="text"
                            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                            onClick={() => setCollapsed(!collapsed)}
                        />
                        <Input
                            placeholder="Search transactions, categories..."
                            prefix={<SearchOutlined style={{ color: 'var(--color-text-muted)' }} />}
                            style={{
                                width: 320,
                                borderRadius: 8,
                                background: 'var(--color-bg-page)',
                            }}
                        />
                    </div>

                    <div className="header-actions">
                        <Badge count={0} showZero={false}>
                            <Button type="text" icon={<BellOutlined style={{ fontSize: 18 }} />} />
                        </Badge>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => navigate('/transactions')}
                            style={{ borderRadius: 8 }}
                        >
                            Add Transaction
                        </Button>
                        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
                            <Avatar
                                style={{ backgroundColor: 'var(--color-primary)', cursor: 'pointer' }}
                                icon={<UserOutlined />}
                            />
                        </Dropdown>
                    </div>
                </Header>

                {/* Content */}
                <Content className="app-content">
                    <Outlet />
                </Content>
            </Layout>
        </Layout>
    );
};

export default AppLayout;
