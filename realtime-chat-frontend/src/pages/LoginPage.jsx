import { useState } from 'react';
import LoginForm from '../components/Auth/LoginForm';
import RegisterForm from '../components/Auth/RegisterForm';

const LoginPage = () => {
  const [activeTab, setActiveTab] = useState('login');

  return (
    <>
      <div className="bg-canvas">
        <div className="orb orb-1"></div><div className="orb orb-2"></div><div className="orb orb-3"></div>
        <div className="grid-lines"></div>
      </div>

      <main className="auth-root">
        <header className="brand">
          <div className="brand-mark">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path 
                d="M16 29C14.7 29 13.5 28.8 12.3 28.5L5 31V24.8C3 22.5 2 19.5 2 16C2 8.3 8.3 2 16 2C23.7 2 30 8.3 30 16C30 23.7 23.7 29 16 29Z" 
                fill="var(--accent)" 
                opacity="0.15" 
              />
              <path 
                d="M16 29C14.7 29 13.5 28.8 12.3 28.5L5 31V24.8C3 22.5 2 19.5 2 16C2 8.3 8.3 2 16 2C23.7 2 30 8.3 30 16C30 23.7 23.7 29 16 29Z" 
                stroke="var(--accent)" 
                strokeWidth="2.5" 
                strokeLinejoin="round" 
              />
              <path 
                d="M10 16H10.01M16 16H16.01M22 16H22.01" 
                stroke="currentColor" 
                strokeWidth="3.5" 
                strokeLinecap="round" 
              />
            </svg>
          </div>
          <span className="brand-name">ChatWithMe</span>
        </header>

        <div className="auth-card">
          <div className="auth-tabs" role="tablist">
            <button className={`tab ${activeTab === 'login' ? 'active' : ''}`} onClick={() => setActiveTab('login')}>Sign in</button>
            <button className={`tab ${activeTab === 'register' ? 'active' : ''}`} onClick={() => setActiveTab('register')}>Sign up</button>
            <div className={`tab-indicator ${activeTab === 'register' ? 'right' : ''}`}></div>
          </div>

          {activeTab === 'login' ? (
            <LoginForm />
          ) : (
            <RegisterForm onSwitchToLogin={() => setActiveTab('login')} />
          )}
        </div>
      </main>
    </>
  );
};
export default LoginPage;