import React, { createContext, useContext, useEffect, useState } from 'react';
import keycloak from '../keycloak';

interface AuthContextType {
  isAuthenticated: boolean;
  isInitializing: boolean;
  token: string | null;
  isAdmin: boolean;
  tenantId: string | null;
  username: string;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false // Disable to prevent issues with strict browsers
    }).then(authenticated => {
      setIsAuthenticated(authenticated);
      setToken(keycloak.token || null);
      setIsInitializing(false);

      // Auto refresh token before it expires
      keycloak.onTokenExpired = () => {
        keycloak.updateToken(30).then(refreshed => {
          if (refreshed) {
            setToken(keycloak.token || null);
          }
        }).catch(() => {
          keycloak.logout();
        });
      };
    }).catch(err => {
      console.error('Failed to initialize Keycloak', err);
      setIsInitializing(false);
    });
  }, []);

  if (isInitializing) {
    return (
      <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <div className="loading-spinner" style={{ width: 40, height: 40 }} />
      </div>
    );
  }

  if (!isAuthenticated) {
    return null; // Will redirect to Keycloak login page
  }

  // Parse token
  const tokenParsed = keycloak.tokenParsed as any;
  console.log('[DEBUG] Token Parsed:', tokenParsed);
  const isAdmin = tokenParsed?.realm_access?.roles?.includes('ADMIN') || false;
  
  // tenantId có thể là string hoặc mảng 1 phần tử tùy thuộc vào mapper
  let parsedTenantId = null;
  if (tokenParsed?.tenantId) {
    if (Array.isArray(tokenParsed.tenantId)) {
      parsedTenantId = tokenParsed.tenantId[0];
    } else {
      parsedTenantId = tokenParsed.tenantId;
    }
  }
  const tenantId = parsedTenantId;
  const username = tokenParsed?.preferred_username || '';

  const value: AuthContextType = {
    isAuthenticated,
    isInitializing,
    token,
    isAdmin,
    tenantId,
    username,
    login: () => keycloak.login(),
    logout: () => keycloak.logout({ redirectUri: window.location.origin })
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
