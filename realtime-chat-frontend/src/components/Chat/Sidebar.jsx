import { avatarColor, initials } from '../../utils/helpers';

const Sidebar = ({ user, onlineUsers, isOpen, onClose, onLogout }) => {
  const onlineList = Array.from(onlineUsers.keys());

  return (
    <aside className={`sidebar ${isOpen ? 'open' : ''}`} id="sidebar">
      <div className="sidebar-header">
        <div className="brand">
          <svg width="28" height="28" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
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
          <span className="brand-name">ChatWithMe</span>
        </div>
        <button className="icon-btn sidebar-close" onClick={onClose} aria-label="Close sidebar">
          <svg viewBox="0 0 20 20"><path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
        </button>
      </div>

      <div className="sidebar-section">
        <div className="section-label">
          <span>Online</span>
          <span className="count-pill">{onlineList.length}</span>
        </div>
        <ul className="user-list">
          {onlineList.map(username => (
            <li key={username} className="user-item">
              <div className={`avatar ${avatarColor(username)}`}>{initials(username)}</div>
              <span className={username === user ? "is-me" : ""}>
                {username} {username === user && "(you)"}
              </span>
            </li>
          ))}
        </ul>
      </div>

      <div className="sidebar-section sidebar-bottom">
        <div className="room-info">
          <div className="room-dot"></div>
          <div>
            <div className="room-name"># public</div>
            <div className="room-sub">Global chatroom</div>
          </div>
        </div>
      </div>

      <div className="me-card">
        <div className={`avatar ${avatarColor(user)}`}>{initials(user)}</div>
        <div className="me-info">
          <div className="me-name">{user || 'Loading…'}</div>
          <div className="me-status">
            <span className="status-dot online"></span>
            <span>Online</span>
          </div>
        </div>
        <button className="icon-btn" onClick={onLogout} aria-label="Logout" title="Sign out">
          <svg viewBox="0 0 20 20">
            <path d="M13 3H7a2 2 0 00-2 2v2h2V5h6v10H7v-2H5v2a2 2 0 002 2h6a2 2 0 002-2V5a2 2 0 00-2-2zM8 13l-1.5-1.5L8 10l1.5 1.5L11 10l1.5 1.5L11 13H8z" fill="currentColor" />
            <path d="M3 10l4-3v2h6v2H7v2L3 10z" fill="currentColor" />
          </svg>
        </button>
      </div>
    </aside>
  );
};
export default Sidebar;