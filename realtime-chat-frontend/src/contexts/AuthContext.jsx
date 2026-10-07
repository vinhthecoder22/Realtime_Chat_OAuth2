import { createContext, useState, useEffect, useCallback } from 'react';
import { Api } from '../utils/api';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(Api.getStoredUsername());
  const [isAuthenticated, setIsAuthenticated] = useState(Api.isLoggedIn());

  const login = useCallback((token, username) => {
    Api.saveSession(token, username);
    setUser(username);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(() => {
    Api.clearSession();
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  useEffect(() => {
    const handleAuthExpired = () => logout();
    window.addEventListener("auth-expired", handleAuthExpired);
    return () => window.removeEventListener("auth-expired", handleAuthExpired);
  }, [logout]);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};