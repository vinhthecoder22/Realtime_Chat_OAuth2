import { useState, useContext } from 'react';
import { Api } from '../../utils/api';
import { AuthContext } from '../../contexts/AuthContext';
import { showToast } from '../../utils/toast';
import { CONFIG } from '../../utils/config';

const LoginForm = () => {
  const [username, setUsername] = useState(Api.getStoredUsername() || "");
  const [password, setPassword] = useState("");
  const [showPwd, setShowPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const { login } = useContext(AuthContext);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setFieldErrors({});
    let valid = true;
    let errors = {};

    if (!username.trim()) { errors.username = "Username is required"; valid = false; }
    if (!password) { errors.password = "Password is required"; valid = false; }
    
    if (!valid) {
      setFieldErrors(errors);
      return;
    }

    setLoading(true);
    try {
      const { token } = await Api.login(username, password);
      showToast("Welcome back!", "success");
      login(token, username); // Gọi hàm login từ Context để điều hướng
    } catch (err) {
      setError(err.message || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  const loginGoogle = () => {
    window.location.href = `${CONFIG.API_BASE_URL}/oauth2/authorization/google`;
  };

  return (
    <form className="auth-form active" onSubmit={handleSubmit} noValidate>
      <div className="field">
        <label>Username</label>
        <div className="input-wrap">
          <svg className="input-icon" viewBox="0 0 20 20"><path d="M10 10a4 4 0 100-8 4 4 0 000 8zm-7 8a7 7 0 1114 0H3z" fill="currentColor"/></svg>
          <input type="text" placeholder="your username" value={username} onChange={e => setUsername(e.target.value)} className={fieldErrors.username ? "error" : ""} />
        </div>
        {fieldErrors.username && <span className="field-error">{fieldErrors.username}</span>}
      </div>

      <div className="field">
        <label>Password</label>
        <div className="input-wrap">
          <svg className="input-icon" viewBox="0 0 20 20"><path d="M10 2a4 4 0 00-4 4v2H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-1V6a4 4 0 00-4-4zm0 2a2 2 0 012 2v2H8V6a2 2 0 012-2zm0 8a1 1 0 110 2 1 1 0 010-2z" fill="currentColor"/></svg>
          <input type={showPwd ? "text" : "password"} placeholder="••••••••" value={password} onChange={e => setPassword(e.target.value)} className={fieldErrors.password ? "error" : ""} />
          <button type="button" className="eye-toggle" onClick={() => setShowPwd(!showPwd)} style={{ opacity: showPwd ? 1 : 0.5 }}>
            <svg viewBox="0 0 20 20"><path d="M10 4C5 4 1.73 7.11 1 10c.73 2.89 4 6 9 6s8.27-3.11 9-6c-.73-2.89-4-6-9-6zm0 10a4 4 0 110-8 4 4 0 010 8zm0-6a2 2 0 100 4 2 2 0 000-4z" fill="currentColor"/></svg>
          </button>
        </div>
        {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
      </div>

      {error && <div className="form-error visible">{error}</div>}

      <button type="submit" className="btn-primary" disabled={loading}>
        {!loading ? <span className="btn-text">Sign in</span> : <span className="btn-loader"><svg viewBox="0 0 24 24" className="spin"><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" fill="none" strokeDasharray="31.4" strokeDashoffset="10"/></svg></span>}
      </button>

      <div className="divider"><span>or continue with</span></div>

      <button type="button" className="btn-oauth" onClick={loginGoogle}>
         {/* Giữ nguyên SVG Google icon từ file gốc */}
         <svg viewBox="0 0 24 24" width="20" height="20"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
         Continue with Google
      </button>
    </form>
  );
};
export default LoginForm;