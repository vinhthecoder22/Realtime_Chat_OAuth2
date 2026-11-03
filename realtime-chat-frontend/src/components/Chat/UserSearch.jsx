import { useState, useEffect, useRef } from 'react';
import { useDebounce } from '../../hooks/useDebounce';
import { Api } from '../../utils/api';
import { avatarColor, initials } from '../../utils/helpers';

const UserSearch = ({ onClose }) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedUser, setSelectedUser] = useState(null);
  const debouncedQuery = useDebounce(query, 400);
  const abortRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!debouncedQuery || debouncedQuery.trim().length < 2) {
      setResults([]);
      setError(null);
      return;
    }

    // Cancel any in-flight request
    if (abortRef.current) abortRef.current.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setLoading(true);
    setError(null);

    Api.searchUsers(debouncedQuery.trim(), 0, 20, controller.signal)
      .then(data => {
        setResults(data?.content || []);
        setLoading(false);
      })
      .catch(err => {
        if (err.name === 'AbortError') return;
        setError(err.message);
        setLoading(false);
      });

    return () => controller.abort();
  }, [debouncedQuery]);

  const [friendReqStatus, setFriendReqStatus] = useState('');

  const handleViewProfile = (user) => {
    setSelectedUser(user);
    setFriendReqStatus('');
  };

  const handleBackToResults = () => {
    setSelectedUser(null);
    setFriendReqStatus('');
  };

  const handleAddFriend = async () => {
    try {
      setFriendReqStatus('sending');
      await Api.sendFriendRequest(selectedUser.id);
      setFriendReqStatus('pending');
    } catch (err) {
      setFriendReqStatus(err.message || 'Error');
    }
  };

  return (
    <div className="user-search-overlay" onClick={onClose}>
      <div className="user-search-modal" onClick={e => e.stopPropagation()}>
        <div className="user-search-header">
          {selectedUser ? (
            <>
              <button className="icon-btn" onClick={handleBackToResults} aria-label="Back">
                <svg viewBox="0 0 20 20"><path d="M12 4l-6 6 6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none"/></svg>
              </button>
              <h3>User Profile</h3>
            </>
          ) : (
            <h3>Search Users</h3>
          )}
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <svg viewBox="0 0 20 20"><path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
          </button>
        </div>

        {!selectedUser && (
          <>
            <div className="user-search-input-wrap">
              <svg className="search-icon" viewBox="0 0 20 20" width="18" height="18">
                <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="2" fill="none"/>
                <path d="M13.5 13.5L17 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
              </svg>
              <input
                ref={inputRef}
                className="user-search-input"
                type="text"
                placeholder="Search by username or name..."
                value={query}
                onChange={e => setQuery(e.target.value)}
                autoComplete="off"
              />
              {query && (
                <button className="icon-btn search-clear" onClick={() => setQuery('')} aria-label="Clear">
                  <svg viewBox="0 0 20 20"><path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
                </button>
              )}
            </div>

            <div className="user-search-results">
              {loading && (
                <div className="search-state">
                  <div className="search-spinner spin"></div>
                  <span>Searching…</span>
                </div>
              )}

              {error && (
                <div className="search-state search-error">
                  <span>{error}</span>
                </div>
              )}

              {!loading && !error && debouncedQuery.trim().length >= 2 && results.length === 0 && (
                <div className="search-state search-empty">
                  <span>No users found for "{debouncedQuery}"</span>
                </div>
              )}

              {!loading && !error && debouncedQuery.trim().length < 2 && query.length > 0 && (
                <div className="search-state search-hint">
                  <span>Type at least 2 characters to search</span>
                </div>
              )}

              {!loading && results.map(user => (
                <div key={user.id} className="search-result-item" onClick={() => handleViewProfile(user)}>
                  <div className={`avatar ${avatarColor(user.username)}`}>{initials(user.username)}</div>
                  <div className="search-result-info">
                    <div className="search-result-username">{user.username}</div>
                    {user.fullname && <div className="search-result-fullname">{user.fullname}</div>}
                  </div>
                  <svg className="search-result-arrow" viewBox="0 0 20 20" width="16" height="16">
                    <path d="M8 4l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
                  </svg>
                </div>
              ))}
            </div>
          </>
        )}

        {selectedUser && (
          <div className="user-profile-view">
            <div className="profile-card">
              {selectedUser.picture ? (
                <img className="profile-avatar-img" src={selectedUser.picture} alt={selectedUser.username} />
              ) : (
                <div className={`profile-avatar ${avatarColor(selectedUser.username)}`}>
                  {initials(selectedUser.username)}
                </div>
              )}
              <div className="profile-username">{selectedUser.username}</div>
              {selectedUser.fullname && (
                <div className="profile-fullname">{selectedUser.fullname}</div>
              )}

              <div className="profile-actions">
                {friendReqStatus === 'sending' ? (
                  <button className="btn primary" disabled>Sending...</button>
                ) : friendReqStatus === 'pending' ? (
                  <button className="btn" disabled>Request Pending</button>
                ) : (
                  <button className="btn primary" onClick={handleAddFriend}>Add Friend</button>
                )}
                
                {friendReqStatus && friendReqStatus !== 'sending' && friendReqStatus !== 'pending' && (
                   <div className="search-error" style={{marginTop: '10px', fontSize: '14px'}}>{friendReqStatus}</div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default UserSearch;
