import { useEffect, useContext, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { AuthContext } from '../contexts/AuthContext';

const OAuth2RedirectPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { login } = useContext(AuthContext);
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    const hash = location.hash.substring(1);
    const params = new URLSearchParams(hash);
    const token = params.get("token");
    const error = params.get("error");
    const usernameFromUrl = params.get("username");

    if (token) {
      const parts = token.split(".");
      if (parts.length !== 3) {
        setErrorMsg("Token không hợp lệ. Vui lòng thử lại.");
        return;
      }

      let username = usernameFromUrl || "";
      try {
        const payload = JSON.parse(atob(parts[1]));
        username = payload.username || payload.sub || username;
      } catch (e) {
        console.error("JWT Decode error", e);
      }

      login(token, username); // Lưu Context & LocalStorage
      
      setTimeout(() => {
        navigate('/chat', { replace: true });
      }, 1200);

    } else if (error) {
      setErrorMsg(decodeURIComponent(error));
    } else {
      navigate('/login', { replace: true });
    }
  }, [location, navigate, login]);

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
      <div className="redirect-card" style={{ textAlign: 'center', padding: '48px 40px', background: 'var(--glass-bg)', borderRadius: '24px', border: '1px solid var(--glass-border)' }}>
        {!errorMsg ? (
          <>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--text-primary)' }}>Authenticating…</h2>
            <p style={{ color: 'var(--text-muted)' }}>Please wait while we complete your sign-in.</p>
          </>
        ) : (
          <>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--danger)' }}>Sign-in failed</h2>
            <p style={{ color: 'var(--text-muted)' }}>{errorMsg}</p>
            <button onClick={() => navigate('/login')} style={{ color: 'var(--accent)', marginTop: '16px', fontWeight: '500' }}>← Back to sign in</button>
          </>
        )}
      </div>
    </div>
  );
};
export default OAuth2RedirectPage;