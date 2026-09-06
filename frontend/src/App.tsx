import { useState } from 'react';
import { LoginPage } from './components/auth/LoginPage';
import { ApplicationWorkspace } from './components/workspace/ApplicationWorkspace';
import { clearToken, getToken, setToken } from './shared/api/client';

export default function App() {
  const [authenticated, setAuthenticated] = useState(Boolean(getToken()));

  const handleAuthenticated = (token: string) => {
    setToken(token);
    setAuthenticated(true);
  };

  const handleLogout = () => {
    clearToken();
    setAuthenticated(false);
  };

  return authenticated
    ? <ApplicationWorkspace onLogout={handleLogout} />
    : <LoginPage onAuthenticated={handleAuthenticated} />;
}
