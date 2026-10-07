import { useTheme } from '../../hooks/useTheme';

const Topbar = ({ connState, onlineCount, onMenuToggle }) => {
  // Gọi hook quản lý theme
  const { theme, toggleTheme } = useTheme();

  return (
    <header className="topbar">
      <button className="icon-btn menu-toggle" onClick={onMenuToggle} aria-label="Open sidebar">
        <svg viewBox="0 0 20 20"><path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
      </button>
      
      <div className="topbar-center">
        <span className="topbar-room"># public</span>
        <span className="topbar-dot"></span>
        <span className="topbar-sub">{onlineCount} online</span>
      </div>
      
      <div className="topbar-actions">
        {/* NÚT TOGGLE DARK/LIGHT MODE */}
        <button className="icon-btn theme-toggle" onClick={toggleTheme} aria-label="Toggle Theme">
          {theme === 'light' ? (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>
          ) : (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>
          )}
        </button>

        <div className={`connection-badge ${connState}`}>
          <span className="badge-dot"></span>
          <span className="badge-label">{connState.charAt(0).toUpperCase() + connState.slice(1)}</span>
        </div>
      </div>
    </header>
  );
};
export default Topbar;