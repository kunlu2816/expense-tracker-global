import { useState, useEffect } from 'react';
import { Card, Button, List, Tag, Space, Modal, Select, message, Popconfirm, Table, Spin, Empty } from 'antd';
import { PlusOutlined, SyncOutlined, LinkOutlined, DisconnectOutlined, HistoryOutlined, BankOutlined } from '@ant-design/icons';
import api from '../api/axios';

const BankAccounts = () => {
    const [accounts, setAccounts] = useState([]);
    const [institutions, setInstitutions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [linkModalOpen, setLinkModalOpen] = useState(false);
    const [historyModalOpen, setHistoryModalOpen] = useState(false);
    const [selectedAccount, setSelectedAccount] = useState(null);
    const [syncHistory, setSyncHistory] = useState([]);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [selectedInstitution, setSelectedInstitution] = useState(null);
    const [selectedCountry, setSelectedCountry] = useState('GB');
    const [linking, setLinking] = useState(false);
    const [syncing, setSyncing] = useState({});

    const loadAccounts = async () => {
        setLoading(true);
        try {
            const response = await api.get('/banks');
            setAccounts(response.data || []);
        } catch {
            message.error('Failed to load bank accounts');
        } finally {
            setLoading(false);
        }
    };

    const loadInstitutions = async (country = 'GB') => {
        try {
            const response = await api.get('/banks/institutions', { params: { country } });
            setInstitutions(response.data || []);
        } catch {
            message.error('Failed to load banks');
        }
    };

    useEffect(() => {
        loadAccounts();
    }, []);

    const handleLink = async () => {
        if (!selectedInstitution) {
            message.warning('Please select a bank');
            return;
        }
        setLinking(true);
        try {
            const response = await api.post('/banks/link', {
                institutionId: selectedInstitution,
                countryCode: selectedCountry,
            });
            if (response.data?.link) {
                window.location.href = response.data.link;
            }
        } catch {
            message.error('Failed to start bank linking');
        } finally {
            setLinking(false);
        }
    };

    const handleSync = async (id) => {
        setSyncing((s) => ({ ...s, [id]: true }));
        try {
            const response = await api.post(`/banks/${id}/sync`, {});
            const data = response.data;
            message.success(`Synced! ${data.transactionsNew || 0} new transactions`);
            loadAccounts();
        } catch (error) {
            message.error(error.response?.data?.error || 'Sync failed');
        } finally {
            setSyncing((s) => ({ ...s, [id]: false }));
        }
    };

    const handleUnlink = async (id) => {
        try {
            await api.delete(`/banks/${id}`);
            message.success('Bank unlinked');
            loadAccounts();
        } catch {
            message.error('Failed to unlink bank');
        }
    };

    const showHistory = async (account) => {
        setSelectedAccount(account);
        setHistoryModalOpen(true);
        setHistoryLoading(true);
        try {
            const response = await api.get(`/banks/${account.id}/sync-history`, { params: { page: 0, size: 10 } });
            setSyncHistory(response.data?.content || []);
        } catch {
            message.error('Failed to load sync history');
        } finally {
            setHistoryLoading(false);
        }
    };

    const statusColor = {
        LINKED: 'green',
        PENDING: 'orange',
        EXPIRED: 'red',
        ERROR: 'red',
    };

    const historyColumns = [
        { title: 'Date', dataIndex: 'syncedAt', key: 'date', render: (d) => new Date(d).toLocaleString('en-GB') },
        { title: 'Status', dataIndex: 'status', key: 'status', render: (s) => <Tag color={s === 'SUCCESS' ? 'green' : 'red'}>{s}</Tag> },
        { title: 'Fetched', dataIndex: 'transactionsFetched', key: 'fetched' },
        { title: 'New', dataIndex: 'transactionsNew', key: 'new' },
        { title: 'Error', dataIndex: 'errorMessage', key: 'error', ellipsis: true, render: (e) => e || '—' },
    ];

    return (
        <div>
            <div className="page-header">
                <h1>Bank Accounts</h1>
                <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => { setLinkModalOpen(true); loadInstitutions(selectedCountry); }}
                    style={{ borderRadius: 8 }}
                >
                    Link Bank
                </Button>
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>
            ) : accounts.length === 0 ? (
                <Card style={{ borderRadius: 12, border: '1px solid var(--color-border)', textAlign: 'center', padding: 40 }}>
                    <Empty description="No bank accounts linked yet">
                        <Button
                            type="primary"
                            icon={<LinkOutlined />}
                            onClick={() => { setLinkModalOpen(true); loadInstitutions(selectedCountry); }}
                        >
                            Link Your First Bank
                        </Button>
                    </Empty>
                </Card>
            ) : (
                <List
                    grid={{ gutter: 24, xs: 1, sm: 1, md: 2, lg: 2 }}
                    dataSource={accounts}
                    renderItem={(account) => (
                        <List.Item>
                            <Card
                                style={{ borderRadius: 12, border: '1px solid var(--color-border)' }}
                                actions={[
                                    <Button
                                        type="text"
                                        icon={<SyncOutlined spin={syncing[account.id]} />}
                                        onClick={() => handleSync(account.id)}
                                        disabled={account.status !== 'LINKED'}
                                    >
                                        Sync
                                    </Button>,
                                    <Button type="text" icon={<HistoryOutlined />} onClick={() => showHistory(account)}>
                                        History
                                    </Button>,
                                    <Popconfirm title="Unlink this bank?" description="Transactions will be preserved." onConfirm={() => handleUnlink(account.id)}>
                                        <Button type="text" danger icon={<DisconnectOutlined />}>Unlink</Button>
                                    </Popconfirm>,
                                ]}
                            >
                                <Card.Meta
                                    avatar={
                                        account.institutionLogo
                                            ? <img src={account.institutionLogo} alt="" style={{ width: 40, height: 40, borderRadius: 8 }} />
                                            : <BankOutlined style={{ fontSize: 32, color: 'var(--color-primary)' }} />
                                    }
                                    title={
                                        <Space>
                                            <span>{account.institutionName || 'Bank Account'}</span>
                                            <Tag color={statusColor[account.status]}>{account.status}</Tag>
                                        </Space>
                                    }
                                    description={
                                        <div>
                                            {account.maskedIban && <div>IBAN: {account.maskedIban}</div>}
                                            {account.accountName && <div>Owner: {account.accountName}</div>}
                                            {account.lastSyncedAt && (
                                                <div style={{ color: 'var(--color-text-muted)', fontSize: 12, marginTop: 4 }}>
                                                    Last synced: {new Date(account.lastSyncedAt).toLocaleString('en-GB')}
                                                </div>
                                            )}
                                        </div>
                                    }
                                />
                            </Card>
                        </List.Item>
                    )}
                />
            )}

            {/* Link Bank Modal */}
            <Modal
                title="Link a Bank Account"
                open={linkModalOpen}
                onCancel={() => setLinkModalOpen(false)}
                onOk={handleLink}
                confirmLoading={linking}
                okText="Connect Bank"
            >
                <Space direction="vertical" style={{ width: '100%', marginTop: 16 }} size="middle">
                    <div>
                        <label style={{ fontWeight: 500, marginBottom: 4, display: 'block' }}>Country</label>
                        <Select
                            value={selectedCountry}
                            onChange={(val) => { setSelectedCountry(val); loadInstitutions(val); }}
                            style={{ width: '100%' }}
                            options={[
                                { label: '🇬🇧 United Kingdom', value: 'GB' },
                                { label: '🇩🇪 Germany', value: 'DE' },
                                { label: '🇫🇷 France', value: 'FR' },
                                { label: '🇳🇱 Netherlands', value: 'NL' },
                                { label: '🇮🇪 Ireland', value: 'IE' },
                                { label: '🇪🇸 Spain', value: 'ES' },
                                { label: '🇮🇹 Italy', value: 'IT' },
                            ]}
                        />
                    </div>
                    <div>
                        <label style={{ fontWeight: 500, marginBottom: 4, display: 'block' }}>Bank</label>
                        <Select
                            placeholder="Select your bank"
                            showSearch
                            optionFilterProp="label"
                            style={{ width: '100%' }}
                            onChange={setSelectedInstitution}
                            options={institutions.map((inst) => ({
                                label: inst.name,
                                value: inst.id,
                            }))}
                        />
                    </div>
                </Space>
            </Modal>

            {/* Sync History Modal */}
            <Modal
                title={`Sync History — ${selectedAccount?.institutionName || 'Bank'}`}
                open={historyModalOpen}
                onCancel={() => setHistoryModalOpen(false)}
                footer={null}
                width={700}
            >
                <Table
                    columns={historyColumns}
                    dataSource={syncHistory}
                    rowKey="id"
                    loading={historyLoading}
                    pagination={false}
                    size="small"
                />
            </Modal>
        </div>
    );
};

export default BankAccounts;
