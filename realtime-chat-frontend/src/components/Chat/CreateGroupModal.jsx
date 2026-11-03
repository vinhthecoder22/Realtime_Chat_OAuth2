import { useState, useEffect, useMemo } from 'react';
import { Api } from '../../utils/api';
import { avatarColor, initials } from '../../utils/helpers';

const CreateGroupModal = ({ onClose, onCreateGroup }) => {
  const [groupName, setGroupName] = useState('');
  const [friends, setFriends] = useState([]);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [loadingFriends, setLoadingFriends] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;
    const fetchFriends = async () => {
      setLoadingFriends(true);
      setError(null);
      try {
        const data = await Api.getFriends(0, 100);
        if (isMounted) {
          setFriends(data?.content || []);
        }
      } catch (err) {
        if (isMounted) {
          setError(err.message || 'Không thể tải danh sách bạn bè');
        }
      } finally {
        if (isMounted) {
          setLoadingFriends(false);
        }
      }
    };

    fetchFriends();
    return () => {
      isMounted = false;
    };
  }, []);

  const filteredFriends = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return friends;
    return friends.filter(
      (f) =>
        f.username?.toLowerCase().includes(q) ||
        f.fullname?.toLowerCase().includes(q)
    );
  }, [friends, searchQuery]);

  const toggleSelect = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = groupName.trim();
    if (!trimmed) {
      setError('Vui lòng nhập tên nhóm.');
      return;
    }
    if (trimmed.length > 100) {
      setError('Tên nhóm không được vượt quá 100 ký tự.');
      return;
    }
    if (selectedIds.size === 0) {
      setError('Vui lòng chọn ít nhất 1 bạn bè để tạo nhóm.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onCreateGroup(trimmed, Array.from(selectedIds));
      onClose();
    } catch (err) {
      setError(err.message || 'Không thể tạo nhóm.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="user-search-overlay" onClick={onClose}>
      <div
        className="user-search-modal group-modal"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '440px' }}
      >
        <div className="user-search-header">
          <h3>Tạo nhóm mới</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Đóng" disabled={submitting}>
            <svg viewBox="0 0 20 20" width="20" height="20" fill="none">
              <path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="group-form">
          <div className="form-group" style={{ padding: '12px 16px 0' }}>
            <label className="form-label" htmlFor="group-name-input">
              Tên nhóm <span style={{ color: 'var(--danger)' }}>*</span>
            </label>
            <input
              id="group-name-input"
              type="text"
              className="group-input"
              placeholder="Nhập tên nhóm trò chuyện..."
              value={groupName}
              onChange={(e) => {
                setGroupName(e.target.value);
                if (error) setError(null);
              }}
              maxLength={100}
              disabled={submitting}
              autoFocus
            />
            <div className="char-count" style={{ marginTop: '4px' }}>
              {groupName.trim().length}/100
            </div>
          </div>

          <div style={{ padding: '8px 16px' }}>
            <label className="form-label">
              Thêm bạn bè ({selectedIds.size} đã chọn)
            </label>
            <div className="search-fake-input" style={{ cursor: 'default' }}>
              <svg viewBox="0 0 20 20" width="16" height="16">
                <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="2" fill="none" />
                <path d="M13.5 13.5L17 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <input
                type="text"
                placeholder="Tìm kiếm bạn bè..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  outline: 'none',
                  color: 'inherit',
                  width: '100%',
                  fontSize: '14px',
                }}
                disabled={submitting}
              />
            </div>
          </div>

          {error && (
            <div className="group-error-banner" style={{ margin: '0 16px 8px' }}>
              <span>{error}</span>
            </div>
          )}

          <div
            className="user-search-results"
            style={{
              padding: '0 16px',
              maxHeight: '220px',
              minHeight: '120px',
              overflowY: 'auto',
            }}
          >
            {loadingFriends && (
              <div className="search-state">
                <div className="search-spinner spin"></div>
                <span>Đang tải danh sách bạn bè...</span>
              </div>
            )}

            {!loadingFriends && friends.length === 0 && (
              <div className="search-state search-empty">
                <span>Bạn chưa có bạn bè nào để tạo nhóm. Hãy kết bạn trước nhé!</span>
              </div>
            )}

            {!loadingFriends && friends.length > 0 && filteredFriends.length === 0 && (
              <div className="search-state search-empty">
                <span>Không tìm thấy bạn bè phù hợp.</span>
              </div>
            )}

            {!loadingFriends &&
              filteredFriends.map((friend) => {
                const isSelected = selectedIds.has(friend.id);
                const displayName = friend.fullname || friend.username;
                return (
                  <div
                    key={friend.id}
                    className={`search-result-item friend-select-item ${isSelected ? 'selected' : ''}`}
                    onClick={() => !submitting && toggleSelect(friend.id)}
                    style={{ cursor: submitting ? 'not-allowed' : 'pointer' }}
                  >
                    <div className="checkbox-wrap">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => {}}
                        aria-label={`Chọn ${displayName}`}
                        tabIndex={-1}
                      />
                    </div>
                    {friend.picture ? (
                      <img src={friend.picture} alt={displayName} className="avatar" style={{ width: 36, height: 36 }} />
                    ) : (
                      <div
                        className={`avatar ${avatarColor(friend.username)}`}
                        style={{ width: 36, height: 36, fontSize: 13 }}
                      >
                        {initials(displayName)}
                      </div>
                    )}
                    <div className="search-result-info">
                      <div className="search-result-username">{friend.username}</div>
                      {friend.fullname && (
                        <div className="search-result-fullname">{friend.fullname}</div>
                      )}
                    </div>
                  </div>
                );
              })}
          </div>

          <div className="modal-footer" style={{ padding: '16px', display: 'flex', gap: '8px', justifyContent: 'flex-end', borderTop: '1px solid var(--glass-border)' }}>
            <button
              type="button"
              className="btn"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn primary"
              disabled={submitting || !groupName.trim() || selectedIds.size === 0}
            >
              {submitting ? 'Đang tạo...' : 'Tạo nhóm'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateGroupModal;
