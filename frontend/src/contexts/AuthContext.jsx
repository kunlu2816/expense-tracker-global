import { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/axios';

const AuthContext = createContext(null);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth must be used within AuthProvider');
    return context;
};

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    // On mount, check if token exists and load user profile
    useEffect(() => {
        const token = localStorage.getItem('token');
        if (token) {
            loadProfile();
        } else {
            setLoading(false);
        }
    }, []);

    const loadProfile = async () => {
        try {
            const response = await api.get('/user/profile');
            setUser(response.data);
        } catch {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
        } finally {
            setLoading(false);
        }
    };

    const login = async (email, password) => {
        const response = await api.post('/auth/login', { email, password });
        const { token, fullName } = response.data;
        localStorage.setItem('token', token);
        localStorage.setItem('user', JSON.stringify({ fullName, email }));
        await loadProfile();
        return response.data;
    };

    const register = async (email, password, fullName) => {
        const response = await api.post('/auth/register', { email, password, fullName });
        return response.data;
    };

    const logout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        setUser(null);
    };

    return (
        <AuthContext.Provider
            value={{
                user,
                loading,
                isAuthenticated: !!user,
                login,
                register,
                logout,
                loadProfile,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
};
