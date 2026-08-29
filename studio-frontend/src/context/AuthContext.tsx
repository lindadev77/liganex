import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import { clearTokens, getAccessToken, setTokens } from '../api/client';

interface AuthContextValue {
  token: string | null;
  email: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const EMAIL_KEY = 'liganex.email';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getAccessToken());
  const [email, setEmail] = useState<string | null>(() => localStorage.getItem(EMAIL_KEY));

  const login = useCallback(async (emailValue: string, password: string) => {
    const t = await authApi.login(emailValue, password);
    setTokens(t.accessToken, t.refreshToken);
    setToken(t.accessToken);
    setEmail(emailValue);
    localStorage.setItem(EMAIL_KEY, emailValue);
  }, []);

  const register = useCallback(
    async (emailValue: string, password: string, displayName: string) => {
      await authApi.register(emailValue, password, displayName);
    },
    [],
  );

  const logout = useCallback(() => {
    clearTokens();
    setToken(null);
    setEmail(null);
    localStorage.removeItem(EMAIL_KEY);
  }, []);

  return (
    <AuthContext.Provider value={{ token, email, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return ctx;
}
