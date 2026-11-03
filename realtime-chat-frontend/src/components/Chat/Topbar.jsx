import { useTheme } from '../../hooks/useTheme';
import { avatarColor, initials, formatLastSeen } from '../../utils/helpers';

const Topbar = ({ connState, onlineCount, onMenuToggle, activeConversation, onlineUsers, lastSeenUsers, onOpenGroupInfo }) => {
  const { theme, toggleTheme } = useTheme();

  const isDirect = activeConversation?.type === 'DIRECT';
  const isGroup = activeConversation?.type === 'GROUP';
  const displayName = isDirect
    ? activeConversation.name || activeConversation.otherUser?.fullname || activeConversation.otherUser?.username || 'Unknown'
    : isGroup
    ? activeConversation.name || 'Group Chat'
    : '# public';

  const otherUsername = activeConversation?.otherUser?.username;
  const isOnline = isDirect && otherUsername && onlineUsers?.has(otherUsername);
  const lastSeen = otherUsername && (lastSeenUsers?.get(otherUsername) || activeConversation?.otherUser?.lastSeenAt);

  let subtitle;
  if (isDirect) {
    if (isOnline) {
      subtitle = 'Đang hoạt động';
    } else if (lastSeen) {
      subtitle = `Hoạt động ${formatLastSeen(lastSeen)}`;
    } else {
      subtitle = 'Ngoại tuyến';
    }
  } else if (isGroup) {
    const memberCount = activeConversation?.memberCount || 0;
    subtitle = `${memberCount} thành viên`;
  } else {
    subtitle = `Global chatroom • ${onlineCount} online`;
  }

  return (
    <header className="topbar">
      <div className="topbar-left">
        <button className="icon-btn menu-toggle" onClick={onMenuToggle} aria-label="Open sidebar">
          <svg viewBox="0 0 20 20" width="20" height="20" fill="none">
            <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
        </button>

        <div className="topbar-avatar">
          {isGroup ? (
            activeConversation?.avatar ? (
              <img src={activeConversation.avatar} alt={displayName} className="avatar" />
            ) : (
              <div className={`avatar ${avatarColor(displayName)}`} style={{ width: 40, height: 40, fontSize: 14 }}>
                {initials(displayName)}
              </div>
            )
          ) : !isDirect ? (
            <svg viewBox="0 0 24 24" width="22" height="22" fill="var(--accent)">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
            </svg>
          ) : activeConversation?.avatar ? (
            <img src={activeConversation.avatar} alt={displayName} className="avatar" />
          ) : (
            <div className={`avatar ${avatarColor(displayName)}`} style={{ width: 40, height: 40, fontSize: 14 }}>
              {initials(displayName)}
            </div>
          )}
        </div>

        <div className="topbar-info">
          <div className="topbar-room">{displayName}</div>
          <div className="topbar-sub">{subtitle}</div>
        </div>
      </div>

      <div className="topbar-actions">
        {isGroup && onOpenGroupInfo && (
          <button
            className="icon-btn group-info-toggle"
            onClick={onOpenGroupInfo}
            title="Thông tin nhóm"
            aria-label="Thông tin nhóm"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="16" x2="12" y2="12" />
              <line x1="12" y1="8" x2="12.01" y2="8" />
            </svg>
          </button>
        )}
        {/* NÚT TOGGLE DARK/LIGHT MODE */}
        <button className="icon-btn theme-toggle" onClick={toggleTheme} aria-label="Toggle Theme">
          {theme === 'light' ? (
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="5"></circle>
              <line x1="12" y1="1" x2="12" y2="3"></line>
              <line x1="12" y1="21" x2="12" y2="23"></line>
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
              <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
              <line x1="1" y1="12" x2="3" y2="12"></line>
              <line x1="21" y1="12" x2="23" y2="12"></line>
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
              <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
            </svg>
          )}
        </button>

        <div className={`connection-badge ${connState}`}>
          <span className="badge-dot"></span>
          <span className="badge-label">
            {connState === 'connected'
              ? 'Đã kết nối'
              : connState === 'connecting'
              ? 'Đang kết nối...'
              : connState === 'reconnecting'
              ? 'Đang kết nối lại...'
              : 'Mất kết nối'}
          </span>
        </div>
      </div>
    </header>
  );
};

export default Topbar;