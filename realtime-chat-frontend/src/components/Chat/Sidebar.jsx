import { useState } from 'react';
import { avatarColor, initials, formatRelativeTime } from '../../utils/helpers';
import UserSearch from './UserSearch';
import FriendsModal from './FriendsModal';

const Sidebar = ({
  user,
  onlineUsers,
  isOpen,
  onClose,
  onLogout,
  conversations,
  loadingConversations,
  conversationError,
  onRetry,
  activeConversationId,
  onSelectConversation,
  onCreateDirectChat,
  onCreateGroup,
}) => {
  const [showSearch, setShowSearch] = useState(false);
  const [showFriends, setShowFriends] = useState(false);

  return (
    <>
      <aside className={`sidebar ${isOpen ? 'open' : ''}`} id="sidebar">
        {/* 1. Navigation Rail */}
        <div className="sidebar-nav-rail">
          <div className="nav-top">
            <div className="nav-me-profile" title={user}>
              <div className={`avatar ${avatarColor(user)}`}>{initials(user)}</div>
              <span className="status-dot online"></span>
            </div>

            <button className="nav-icon-btn active" title="Chats">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                <path d="M12 3C6.477 3 2 7.151 2 12.27c0 2.92 1.5 5.518 3.82 7.234.195.144.316.37.316.613v2.805c0 .484.55.753.935.45l3.072-2.42c.164-.13.374-.194.584-.184 1.042.053 2.13.082 3.273.082 5.523 0 10-4.151 10-9.27C24 7.151 19.523 3 12 3zm1.104 12.448l-2.617-2.793c-.288-.308-.755-.333-1.071-.057l-3.35 2.935c-.378.33-.895-.125-.615-.53l4.317-6.242c.28-.406.87-.456 1.206-.104l2.64 2.81c.285.303.743.327 1.054.054l3.33-2.92c.382-.335.908.132.617.54l-4.3 6.203c-.276.398-.862.45-1.211.104z" />
              </svg>
            </button>

            <button className="nav-icon-btn" title="Friends & Requests" onClick={() => setShowFriends(true)}>
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                <path d="M16 11c2.206 0 4-1.794 4-4s-1.794-4-4-4-4 1.794-4 4 1.794 4 4 4zm-9 2c-2.757 0-5 2.243-5 5v2h10v-2c0-2.757-2.243-5-5-5zm15 5c0-2.757-2.243-5-5-5h-2.126c.712.984 1.126 2.195 1.126 3.5v3.5h6v-2z" />
              </svg>
            </button>

            <button
              className="nav-icon-btn"
              title="Tạo nhóm mới"
              onClick={() => onCreateGroup && onCreateGroup()}
            >
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                <path d="M16.5 13c-1.2 0-3.07.34-4.5 1-1.43-.66-3.3-1-4.5-1C5.33 13 1 14.08 1 16.25V19h22v-2.75c0-2.17-4.33-3.25-6.5-3.25zm-9-2c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3zm9 0c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3z" />
              </svg>
            </button>
          </div>

          <div className="nav-bottom">
            <button className="nav-icon-btn" onClick={onLogout} title="Log Out">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                <path d="M16 13v-2H7V8l-5 4 5 4v-3h9zM20 3h-9c-1.103 0-2 .897-2 2v4h2V5h9v14h-9v-4H9v4c0 1.103.897 2 2 2h9c1.103 0 2-.897 2-2V5c0-1.103-.897-2-2-2z" />
              </svg>
            </button>
          </div>
        </div>

        {/* 2. Conversations Panel */}
        <div className="sidebar-panel">
          <div className="sidebar-header">
            <h2>ChatWithMe</h2>
            <button className="icon-btn sidebar-close" onClick={onClose} aria-label="Close sidebar">
              <svg viewBox="0 0 20 20" width="20" height="20" fill="none">
                <path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </div>

          <div className="sidebar-search">
            <div className="search-fake-input" onClick={() => setShowSearch(true)}>
              <svg viewBox="0 0 20 20" width="16" height="16">
                <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="2" fill="none" />
                <path d="M13.5 13.5L17 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <span>Search Messenger</span>
            </div>
          </div>

          <div className="sidebar-list">
            <div className="list-section-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingRight: '8px' }}>
              <div className="list-section-title">Conversations</div>
              {onCreateGroup && (
                <button
                  className="section-action-btn"
                  onClick={onCreateGroup}
                  title="Tạo nhóm mới"
                  aria-label="Tạo nhóm mới"
                >
                  <svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                    <line x1="10" y1="4" x2="10" y2="16"></line>
                    <line x1="4" y1="10" x2="16" y2="10"></line>
                  </svg>
                  <span>Tạo nhóm</span>
                </button>
              )}
            </div>

            {/* Skeleton Loading State */}
            {loadingConversations && (
              <div className="skeleton-wrap">
                {[1, 2, 3, 4].map((n) => (
                  <div key={n} className="skeleton-item">
                    <div className="skeleton-avatar"></div>
                    <div className="skeleton-info">
                      <div className="skeleton-line short"></div>
                      <div className="skeleton-line long"></div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Error State */}
            {!loadingConversations && conversationError && (
              <div className="sidebar-state">
                <p>{conversationError}</p>
                {onRetry && (
                  <button className="retry-btn" onClick={onRetry}>
                    Thử lại
                  </button>
                )}
              </div>
            )}

            {/* Empty State */}
            {!loadingConversations && !conversationError && conversations?.length === 0 && (
              <div className="sidebar-state">
                <p>Chưa có cuộc trò chuyện nào</p>
              </div>
            )}

            {/* Conversation Items */}
            {!loadingConversations &&
              !conversationError &&
              conversations?.map((conv) => {
                const isActive = conv.id === activeConversationId;
                const isDirect = conv.type === 'DIRECT';
                const isGroup = conv.type === 'GROUP';
                const displayName = conv.name || (isDirect ? 'Unknown User' : isGroup ? 'Nhóm không tên' : 'Global Chat');
                const otherUsername = conv.otherUser?.username;
                const isOnline = isDirect && otherUsername && onlineUsers.has(otherUsername);

                let previewText = '';
                if (conv.lastMessage) {
                  if (conv.lastMessageSender === user) {
                    previewText = `Bạn: ${conv.lastMessage}`;
                  } else if (isDirect) {
                    previewText = conv.lastMessage;
                  } else {
                    const senderLabel = conv.lastMessageSender ? `${conv.lastMessageSender}: ` : '';
                    previewText = `${senderLabel}${conv.lastMessage}`;
                  }
                } else {
                  previewText = isDirect
                    ? 'Bắt đầu cuộc trò chuyện'
                    : isGroup
                    ? 'Bắt đầu trò chuyện nhóm'
                    : 'Phòng chat cộng đồng';
                }

                const unreadCount = Number(conv.unreadCount || 0);
                const hasUnread = unreadCount > 0;
                const timeDisplay = formatRelativeTime(conv.lastMessageTimestamp || conv.lastActivityAt || conv.createdAt);

                return (
                  <div
                    key={conv.id}
                    className={`list-item ${isActive ? 'active' : ''} ${hasUnread ? 'has-unread' : ''}`}
                    onClick={() => {
                      onSelectConversation(conv.id);
                      if (onClose) onClose();
                    }}
                  >
                    <div className="list-avatar">
                      {isGroup ? (
                        conv.avatar ? (
                          <img src={conv.avatar} alt={displayName} className="avatar" />
                        ) : (
                          <div className={`avatar ${avatarColor(displayName)}`} style={{ position: 'relative' }}>
                            {initials(displayName)}
                            <span className="group-badge-icon" title="Nhóm">
                              <svg viewBox="0 0 20 20" width="10" height="10" fill="currentColor">
                                <path d="M13 6a3 3 0 11-6 0 3 3 0 016 0zM18 8a2 2 0 11-4 0 2 2 0 014 0zM14 15a4 4 0 00-8 0v1h8v-1zM6 8a2 2 0 11-4 0 2 2 0 014 0zM16 16v-1a4 4 0 00-3-3.87 5.97 5.97 0 011 3.87v1h2zM4 16h-2v-1a5.97 5.97 0 011-3.87A4 4 0 000 15v1h4z" />
                              </svg>
                            </span>
                          </div>
                        )
                      ) : !isDirect ? (
                        <div className="avatar-group">
                          <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--accent)">
                            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                          </svg>
                        </div>
                      ) : conv.avatar ? (
                        <>
                          <img src={conv.avatar} alt={displayName} className="avatar" />
                          {isOnline && <span className="status-dot online"></span>}
                        </>
                      ) : (
                        <>
                          <div className={`avatar ${avatarColor(displayName)}`}>{initials(displayName)}</div>
                          {isOnline && <span className="status-dot online"></span>}
                        </>
                      )}
                    </div>
                    <div className="list-info">
                      <div className="list-header-row">
                        <div className={`list-name ${hasUnread ? 'unread' : ''}`} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{displayName}</span>
                          {isGroup && conv.memberCount > 0 && (
                            <span className="member-count-pill" title={`${conv.memberCount} thành viên`}>
                              {conv.memberCount}
                            </span>
                          )}
                        </div>
                        {timeDisplay && <span className={`list-time ${hasUnread ? 'unread' : ''}`}>{timeDisplay}</span>}
                      </div>
                      <div className="list-preview-row">
                        <span className={`list-preview-text ${hasUnread ? 'unread' : ''}`}>{previewText}</span>
                        {hasUnread && (
                          <span className="unread-badge">
                            {unreadCount > 99 ? '99+' : unreadCount}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
          </div>
        </div>
      </aside>

      {showSearch && <UserSearch onClose={() => setShowSearch(false)} />}
      {showFriends && (
        <FriendsModal
          onClose={() => setShowFriends(false)}
          onCreateDirectChat={onCreateDirectChat}
        />
      )}
    </>
  );
};

export default Sidebar;