import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from './api.js';

const STORAGE_KEY = 'seatbook.auth';
const AuthContext = createContext(null);

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(load); // { token, name, role } or null

  const save = useCallback((auth) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
    setUser(auth);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setUser(null);
  }, []);

  const login = useCallback(async (email, password) => save(await api.login({ email, password })), [save]);
  const register = useCallback(
    async (name, email, password) => save(await api.register({ name, email, password })),
    [save],
  );

  useEffect(() => {
    window.addEventListener('seatbook:signed-out', logout);
    return () => window.removeEventListener('seatbook:signed-out', logout);
  }, [logout]);

  const value = useMemo(() => ({ user, login, register, logout }), [user, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
