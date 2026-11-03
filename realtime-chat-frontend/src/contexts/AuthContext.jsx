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
    const handleAuthExpired = () => {
      // Only clear React state here — do NOT call Api.clearSession() / logout()
      // because handleUnauthorized in api.js already cleared localStorage
      // and dispatched this "auth-expired" event. Calling logout() would
      // call Api.clearSession() → handleUnauthorized → dispatch "auth-expired"
      // again → infinite loop → "Maximum call stack size exceeded".
      setUser(null);
      setIsAuthenticated(false);
    };
    window.addEventListener("auth-expired", handleAuthExpired);
    return () => window.removeEventListener("auth-expired", handleAuthExpired);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
