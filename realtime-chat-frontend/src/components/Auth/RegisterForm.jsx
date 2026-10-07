import { useState, useMemo } from 'react';
import { Api } from '../../utils/api';
import { showToast } from '../../utils/toast';

const calcStrength = (pw) => {
  if (!pw) return 0;
  let s = 0;
  if (pw.length >= 6) s++;
  if (pw.length >= 10) s++;
  if (/[A-Z]/.test(pw) && /[0-9]/.test(pw)) s++;
  if (/[^A-Za-z0-9]/.test(pw)) s++;
  return Math.min(s, 4);
};

const RegisterForm = ({ onSwitchToLogin }) => {
  const [formData, setFormData] = useState({ username: '', email: '', password: '', confirm: '' });
  const [showPwd, setShowPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  const strength = useMemo(() => calcStrength(formData.password), [formData.password]);
  const strengthColors = ["", "#ef4444", "#f59e0b", "#3b82f6", "#22c55e"];
  const strengthWidths = [0, 25, 50, 75, 100];

  const handleChange = (e) => setFormData({ ...formData, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(""); setFieldErrors({});
    let valid = true, errors = {};

    if (!formData.username || formData.username.length < 3) { errors.username = "Min 3 characters"; valid = false; }
    if (!formData.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) { errors.email = "Invalid email"; valid = false; }
    if (!formData.password || formData.password.length < 6) { errors.password = "Min 6 characters"; valid = false; }
    if (formData.password !== formData.confirm) { errors.confirm = "Passwords do not match"; valid = false; }

    if (!valid) return setFieldErrors(errors);

    setLoading(true);
    try {
      await Api.register(formData.username, formData.email, formData.password, formData.confirm);
      showToast("Account created! Please sign in.", "success");
      onSwitchToLogin(); // Tự động chuyển qua tab Login
    } catch (err) {
      setError(err.message || "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <form className="auth-form active" onSubmit={handleSubmit} noValidate>
      <div className="field-row">
        <div className="field">
          <label>Username</label>
          <div className="input-wrap">
            <input type="text" name="username" placeholder="username" onChange={handleChange} className={fieldErrors.username ? "error" : ""} />
          </div>
          {fieldErrors.username && <span className="field-error">{fieldErrors.username}</span>}
        </div>
        <div className="field">
          <label>Email</label>
          <div className="input-wrap">
            <input type="email" name="email" placeholder="your@email.com" onChange={handleChange} className={fieldErrors.email ? "error" : ""} />
          </div>
          {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
        </div>
      </div>

      <div className="field">
        <label>Password</label>
        <div className="input-wrap">
          <input type={showPwd ? "text" : "password"} name="password" placeholder="min 6 characters" onChange={handleChange} className={fieldErrors.password ? "error" : ""} />
          <button type="button" className="eye-toggle" onClick={() => setShowPwd(!showPwd)}>
             <svg viewBox="0 0 20 20" style={{ opacity: showPwd ? 1 : 0.5 }}><path d="M10 4C5 4 1.73 7.11 1 10c.73 2.89 4 6 9 6s8.27-3.11 9-6c-.73-2.89-4-6-9-6zm0 10a4 4 0 110-8 4 4 0 010 8zm0-6a2 2 0 100 4 2 2 0 000-4z" fill="currentColor"/></svg>
          </button>
        </div>
        <div className="strength-bar">
          <div className="strength-fill" style={{ width: `${strengthWidths[strength]}%`, background: strengthColors[strength] }}></div>
        </div>
        {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
      </div>

      <div className="field">
        <label>Confirm password</label>
        <div className="input-wrap">
          <input type="password" name="confirm" placeholder="repeat password" onChange={handleChange} className={fieldErrors.confirm ? "error" : ""} />
        </div>
        {fieldErrors.confirm && <span className="field-error">{fieldErrors.confirm}</span>}
      </div>

      {error && <div className="form-error visible">{error}</div>}

      <button type="submit" className="btn-primary" disabled={loading}>
        {!loading ? <span className="btn-text">Create account</span> : <span className="btn-loader">...</span>}
      </button>
    </form>
  );
};
export default RegisterForm;