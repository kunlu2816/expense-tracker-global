import { useState, useEffect } from 'react';
import { Row, Col, Card, Table, Spin } from 'antd';
import {
    PieChart, Pie, Cell, ResponsiveContainer, Tooltip,
} from 'recharts';
import api from '../api/axios';

const COLORS = ['#0D9F6E', '#10B981', '#34D399', '#6EE7B7', '#A7F3D0', '#087F5B', '#059669', '#047857'];

const formatCurrency = (val) =>
    new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(val || 0);

const Dashboard = () => {
    const [stats, setStats] = useState(null);
    const [recentTxns, setRecentTxns] = useState([]);
    const [categoryData, setCategoryData] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [dashRes, txnRes, catRes] = await Promise.all([
                api.get('/transactions/dashboard'),
                api.get('/transactions', { params: { page: 0, size: 5 } }),
                api.get('/transactions/category-summary'),
            ]);
            setStats(dashRes.data);
            setRecentTxns(txnRes.data.content || []);
            // Build pie data from category summary (expenses only)
            const expenseData = (catRes.data || [])
                .filter((c) => c.type === 'OUT')
                .map((c) => ({ name: c.categoryName, value: c.total }));
            setCategoryData(expenseData);
        } catch (error) {
            console.error('Failed to load dashboard:', error);
        } finally {
            setLoading(false);
        }
    };

    const columns = [
        {
            title: 'Description',
            dataIndex: 'description',
            key: 'description',
            render: (text, record) => (
                <div>
                    <div style={{ fontWeight: 500 }}>{text || record.category?.name || '—'}</div>
                    <div style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>
                        {record.category?.name}
                    </div>
                </div>
            ),
        },
        {
            title: 'Date',
            dataIndex: 'transactionDate',
            key: 'date',
            render: (date) => new Date(date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
        },
        {
            title: 'Amount',
            dataIndex: 'amount',
            key: 'amount',
            align: 'right',
            render: (amount, record) => (
                <span className={record.type === 'IN' ? 'amount-in' : 'amount-out'}>
                    {record.type === 'IN' ? '+' : '-'}{formatCurrency(amount)}
                </span>
            ),
        },
    ];

    if (loading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}>
                <Spin size="large" />
            </div>
        );
    }

    return (
        <div>
            <div className="page-header">
                <h1>Dashboard</h1>
            </div>

            {/* Stat Cards */}
            <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
                <Col xs={24} sm={8}>
                    <div className="stat-card">
                        <div className="stat-label">Total Income</div>
                        <div className="stat-value income">{formatCurrency(stats?.totalIncome)}</div>
                    </div>
                </Col>
                <Col xs={24} sm={8}>
                    <div className="stat-card">
                        <div className="stat-label">Total Expense</div>
                        <div className="stat-value expense">{formatCurrency(stats?.totalExpense)}</div>
                    </div>
                </Col>
                <Col xs={24} sm={8}>
                    <div className="stat-card">
                        <div className="stat-label">Balance</div>
                        <div className="stat-value">{formatCurrency(stats?.balance)}</div>
                    </div>
                </Col>
            </Row>

            {/* Charts + Recent Transactions */}
            <Row gutter={[24, 24]}>
                {/* Pie Chart */}
                <Col xs={24} lg={10}>
                    <Card
                        title="Spending by Category"
                        style={{ borderRadius: 12, border: '1px solid var(--color-border)' }}
                        styles={{ header: { borderBottom: '1px solid var(--color-border)' } }}
                    >
                        {categoryData.length > 0 ? (
                            <ResponsiveContainer width="100%" height={280}>
                                <PieChart>
                                    <Pie
                                        data={categoryData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={100}
                                        paddingAngle={4}
                                        dataKey="value"
                                        label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                                    >
                                        {categoryData.map((_, idx) => (
                                            <Cell key={idx} fill={COLORS[idx % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip formatter={(val) => formatCurrency(val)} />
                                </PieChart>
                            </ResponsiveContainer>
                        ) : (
                            <div style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-muted)' }}>
                                No expense data yet
                            </div>
                        )}
                    </Card>
                </Col>

                {/* Recent Transactions */}
                <Col xs={24} lg={14}>
                    <Card
                        title="Recent Transactions"
                        style={{ borderRadius: 12, border: '1px solid var(--color-border)' }}
                        styles={{ header: { borderBottom: '1px solid var(--color-border)' } }}
                    >
                        <Table
                            columns={columns}
                            dataSource={recentTxns}
                            rowKey="id"
                            pagination={false}
                            size="small"
                            locale={{ emptyText: 'No transactions yet. Add your first one!' }}
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default Dashboard;
