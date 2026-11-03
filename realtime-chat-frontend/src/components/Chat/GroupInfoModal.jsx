import { useState, useEffect, useMemo } from 'react';
import { Api } from '../../utils/api';
import { avatarColor, initials, formatLastSeen } from '../../utils/helpers';
import { showToast } from '../../utils/toast';

const GroupInfoModal = ({
  conversation,
  currentUser,
  onlineUsers,
  onClose,
  onUpdateGroup,
  onLeaveGroup,
  onMemberCountChange,
}) => {
  const [activeTab, setActiveTab] = useState('members'); // 'members' | 'settings' | 'add_member'
  const [members, setMembers] = useState([]);
  const [loadingMembers, setLoadingMembers] = useState(true);
  const [memberError, setMemberError] = useState(null);

  // Group settings edit state
  const [editName, setEditName] = useState(conversation?.name || '');
  const [editAvatar, setEditAvatar] = useState(conversation?.avatar || '');
  const [savingSettings, setSavingSettings] = useState(false);

  // Add members state
  const [allFriends, setAllFriends] = useState([]);
  const [loadingFriends, setLoadingFriends] = useState(false);
  const [selectedFriendIds, setSelectedFriendIds] = useState(new Set());
  const [friendSearch, setFriendSearch] = useState('');
  const [addingMembers, setAddingMembers] = useState(false);

  // Confirm actions state
  const [confirmAction, setConfirmAction] = useState(null); // { type: 'KICK' | 'TRANSFER' | 'LEAVE', targetMember?: object }
  const [successorAdminId, setSuccessorAdminId] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const isAdmin = useMemo(() => {
    // Prioritize the fresh member list (updated after transfer/kick) over potentially stale conversation.currentUserRole
    const myMember = members.find((m) => m.username === currentUser);
    if (myMember) {
      return myMember.role === 'ADMIN';
    }
    return conversation?.currentUserRole === 'ADMIN';
  }, [conversation?.currentUserRole, members, currentUser]);

  const fetchMembers = async (isMounted = true) => {
    if (isMounted) {
      setLoadingMembers(true);
      setMemberError(null);
    }
    try {
      const data = await Api.getGroupMembers(conversation.id);
      if (isMounted) {
        setMembers(data || []);
        if (data) {
          onMemberCountChange?.(conversation.id, data.length);
        }
      }
    } catch (err) {
      if (isMounted) {
        setMemberError(err.message || 'Không thể tải danh sách thành viên');
      }
    } finally {
      if (isMounted) {
        setLoadingMembers(false);
      }
    }
  };

  useEffect(() => {
    let isMounted = true;
    if (conversation?.id) {
      fetchMembers(isMounted);
      setEditName(conversation.name || '');
      setEditAvatar(conversation.avatar || '');
    }
    return () => {
      isMounted = false;
    };
  }, [conversation?.id]);

  // Load friends for "Add Members" tab
  const fetchFriendsForAdd = async () => {
    setLoadingFriends(true);
    try {
      const data = await Api.getFriends(0, 100);
      setAllFriends(data?.content || []);
    } catch (err) {
      showToast(err.message || 'Không thể tải danh sách bạn bè', 'error');
    } finally {
      setLoadingFriends(false);
    }
  };

  const handleOpenAddMembers = () => {
    setActiveTab('add_member');
    setSelectedFriendIds(new Set());
    setFriendSearch('');
    fetchFriendsForAdd();
  };

  // Available friends (friends who are NOT already active in the group)
  const availableFriends = useMemo(() => {
    const existingMemberIds = new Set(members.map((m) => m.userId));
    const q = friendSearch.trim().toLowerCase();
    return allFriends.filter((f) => {
      if (existingMemberIds.has(f.id)) return false;
      if (!q) return true;
      return (
        f.username?.toLowerCase().includes(q) ||
        f.fullname?.toLowerCase().includes(q)
      );
    });
  }, [allFriends, members, friendSearch]);

  const toggleSelectFriend = (id) => {
    setSelectedFriendIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleAddMembersSubmit = async () => {
    if (selectedFriendIds.size === 0) return;
    setAddingMembers(true);
    try {
      const updatedMembers = await Api.addGroupMembers(
        conversation.id,
        Array.from(selectedFriendIds)
      );
      setMembers(updatedMembers || []);
      if (updatedMembers) {
        onMemberCountChange?.(conversation.id, updatedMembers.length);
      }
      showToast('Đã thêm thành viên vào nhóm!', 'success');
      setActiveTab('members');
    } catch (err) {
      showToast(err.message || 'Không thể thêm thành viên', 'error');
    } finally {
      setAddingMembers(false);
    }
  };

  const handleSaveSettings = async (e) => {
    e.preventDefault();
    const trimmed = editName.trim();
    if (!trimmed) {
      showToast('Tên nhóm không được để trống', 'error');
      return;
    }
    if (trimmed.length > 100) {
      showToast('Tên nhóm không được vượt quá 100 ký tự', 'error');
      return;
    }

    setSavingSettings(true);
    try {
      await onUpdateGroup(conversation.id, {
        name: trimmed,
        avatar: editAvatar.trim(), // Send "" so backend can detect and clear the avatar
      });
      setActiveTab('members');
    } catch (_err) {
      // Error toast is already shown by onUpdateGroup
    } finally {
      setSavingSettings(false);
    }
  };

  // Action confirmations
  const handleKickMember = async () => {
    if (!confirmAction?.targetMember) return;
    setActionLoading(true);
    try {
      await Api.removeGroupMember(conversation.id, confirmAction.targetMember.userId);
      setMembers((prev) => {
        const next = prev.filter((m) => m.userId !== confirmAction.targetMember.userId);
        onMemberCountChange?.(conversation.id, next.length);
        return next;
      });
      showToast(`Đã xóa ${confirmAction.targetMember.username} khỏi nhóm`, 'info');
      setConfirmAction(null);
    } catch (err) {
      showToast(err.message || 'Không thể xóa thành viên', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleTransferAdmin = async () => {
    if (!confirmAction?.targetMember) return;
    setActionLoading(true);
    try {
      await Api.transferAdmin(conversation.id, confirmAction.targetMember.userId);
      // Refresh members list so roles update accurately
      await fetchMembers();
      showToast(`Đã chuyển quyền Admin cho ${confirmAction.targetMember.username}`, 'success');
      setConfirmAction(null);
    } catch (err) {
      showToast(err.message || 'Không thể chuyển quyền Admin', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleLeaveGroup = async () => {
    setActionLoading(true);
    try {
      const newAdminId = successorAdminId ? Number(successorAdminId) : null;
      await onLeaveGroup(conversation.id, newAdminId);
      onClose();
    } catch (_err) {
      // Error handled by onLeaveGroup
    } finally {
      setActionLoading(false);
    }
  };

  const otherActiveMembers = useMemo(
    () => members.filter((m) => m.username !== currentUser),
    [members, currentUser]
  );

  return (
    <div className="user-search-overlay" onClick={onClose}>
      <div
        className="user-search-modal group-info-modal"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '500px', maxHeight: '85vh', display: 'flex', flexDirection: 'column' }}
      >
        {/* Header */}
        <div className="user-search-header" style={{ borderBottom: 'none', paddingBottom: 0 }}>
          <h3>Thông tin nhóm</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Đóng">
            <svg viewBox="0 0 20 20" width="20" height="20" fill="none">
              <path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </button>
        </div>

        {/* Group Hero Banner */}
        <div className="group-hero" style={{ padding: '16px 20px', textAlign: 'center', borderBottom: '1px solid var(--glass-border)' }}>
          <div style={{ display: 'inline-block', position: 'relative', marginBottom: '10px' }}>
            {conversation?.avatar ? (
              <img
                src={conversation.avatar}
                alt={conversation.name}
                className="avatar"
                style={{ width: 64, height: 64, borderRadius: '50%', objectFit: 'cover' }}
              />
            ) : (
              <div
                className={`avatar ${avatarColor(conversation?.name || 'Group')}`}
                style={{ width: 64, height: 64, fontSize: 24, borderRadius: '50%' }}
              >
                {initials(conversation?.name || 'Group')}
              </div>
            )}
            {isAdmin && (
              <span
                style={{
                  position: 'absolute',
                  bottom: -2,
                  right: -4,
                  background: 'var(--accent)',
                  color: '#fff',
                  fontSize: '10px',
                  fontWeight: 700,
                  padding: '2px 6px',
                  borderRadius: '10px',
                }}
              >
                ADMIN
              </span>
            )}
          </div>
          <h4 style={{ margin: '0 0 4px', fontSize: '1.2rem', color: 'var(--text-primary)' }}>
            {conversation?.name || 'Group Chat'}
          </h4>
          <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            {members.length > 0 ? members.length : conversation?.memberCount || 0} thành viên
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="friends-tabs" style={{ display: 'flex', borderBottom: '1px solid var(--border)' }}>
          <button
            className={`tab-btn ${activeTab === 'members' ? 'active' : ''}`}
            onClick={() => setActiveTab('members')}
          >
            Thành viên ({members.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'settings' ? 'active' : ''}`}
            onClick={() => setActiveTab('settings')}
          >
            {isAdmin ? 'Cài đặt nhóm' : 'Thông tin'}
          </button>
          {isAdmin && (
            <button
              className={`tab-btn ${activeTab === 'add_member' ? 'active' : ''}`}
              onClick={handleOpenAddMembers}
            >
              + Thêm người
            </button>
          )}
        </div>

        {/* Content Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px 16px', minHeight: '260px' }}>
          {/* TAB 1: MEMBERS */}
          {activeTab === 'members' && (
            <div>
              {loadingMembers && (
                <div className="search-state">
                  <div className="search-spinner spin"></div>
                  <span>Đang tải danh sách thành viên...</span>
                </div>
              )}

              {memberError && (
                <div className="search-state search-error">
                  <span>{memberError}</span>
                  <button className="retry-btn" onClick={fetchMembers} style={{ marginTop: '8px' }}>
                    Thử lại
                  </button>
                </div>
              )}

              {!loadingMembers && !memberError && members.map((member) => {
                const isOnline = onlineUsers?.has(member.username) || member.isOnline;
                const isMemberAdmin = member.role === 'ADMIN';
                const isMe = member.username === currentUser;
                const displayName = member.fullname || member.username;

                return (
                  <div
                    key={member.userId}
                    className="search-result-item member-item"
                    style={{ cursor: 'default', padding: '10px 12px' }}
                  >
                    <div style={{ position: 'relative' }}>
                      {member.picture ? (
                        <img src={member.picture} alt={displayName} className="avatar" style={{ width: 40, height: 40 }} />
                      ) : (
                        <div
                          className={`avatar ${avatarColor(member.username)}`}
                          style={{ width: 40, height: 40, fontSize: 14 }}
                        >
                          {initials(displayName)}
                        </div>
                      )}
                      {isOnline && <span className="status-dot online"></span>}
                    </div>

                    <div className="search-result-info" style={{ marginLeft: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span className="search-result-username">{member.username}</span>
                        {isMe && <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>(Bạn)</span>}
                        {isMemberAdmin && (
                          <span className="role-badge admin">Admin</span>
                        )}
                      </div>
                      <div className="search-result-fullname">
                        {member.fullname || (isOnline ? 'Đang hoạt động' : member.lastSeenAt ? `Hoạt động ${formatLastSeen(member.lastSeenAt)}` : 'Ngoại tuyến')}
                      </div>
                    </div>

                    {/* Admin Actions on this member */}
                    {isAdmin && !isMe && (
                      <div style={{ display: 'flex', gap: '6px' }}>
                        {!isMemberAdmin && (
                          <button
                            type="button"
                            className="btn btn-sm"
                            title="Chuyển quyền Admin"
                            onClick={() => setConfirmAction({ type: 'TRANSFER', targetMember: member })}
                            style={{ fontSize: '11px', padding: '4px 8px' }}
                          >
                            Admin
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn-sm danger"
                          title="Xóa khỏi nhóm"
                          onClick={() => setConfirmAction({ type: 'KICK', targetMember: member })}
                          style={{ fontSize: '11px', padding: '4px 8px' }}
                        >
                          Xóa
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* TAB 2: SETTINGS / INFO */}
          {activeTab === 'settings' && (
            <div>
              {isAdmin ? (
                <form onSubmit={handleSaveSettings} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-group-name">
                      Tên nhóm <span style={{ color: 'var(--danger)' }}>*</span>
                    </label>
                    <input
                      id="edit-group-name"
                      type="text"
                      className="group-input"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      maxLength={100}
                      disabled={savingSettings}
                    />
                    <div className="char-count">{editName.trim().length}/100</div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-group-avatar">
                      Ảnh đại diện nhóm (URL)
                    </label>
                    <input
                      id="edit-group-avatar"
                      type="url"
                      className="group-input"
                      placeholder="https://example.com/avatar.png"
                      value={editAvatar}
                      onChange={(e) => setEditAvatar(e.target.value)}
                      maxLength={255}
                      disabled={savingSettings}
                    />
                  </div>

                  <button
                    type="submit"
                    className="btn primary"
                    disabled={savingSettings || !editName.trim()}
                    style={{ alignSelf: 'flex-start', marginTop: '4px' }}
                  >
                    {savingSettings ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </button>
                </form>
              ) : (
                <div style={{ color: 'var(--text-secondary)', fontSize: '14px', lineHeight: 1.6 }}>
                  <p><strong>Tên nhóm:</strong> {conversation?.name}</p>
                  <p><strong>Số lượng thành viên:</strong> {members.length}</p>
                  <p><em>Chỉ Quản trị viên (Admin) mới có quyền đổi tên và ảnh đại diện của nhóm.</em></p>
                </div>
              )}

              <hr style={{ margin: '20px 0', border: 'none', borderTop: '1px solid var(--glass-border)' }} />

              {/* Danger Zone: Leave Group */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontWeight: 600, color: 'var(--danger)' }}>Rời nhóm</div>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    Bạn sẽ không còn nhận được tin nhắn từ nhóm này.
                  </div>
                </div>
                <button
                  type="button"
                  className="btn danger"
                  onClick={() => setConfirmAction({ type: 'LEAVE' })}
                >
                  Rời nhóm
                </button>
              </div>
            </div>
          )}

          {/* TAB 3: ADD MEMBERS */}
          {activeTab === 'add_member' && (
            <div>
              <div style={{ marginBottom: '10px' }}>
                <div className="search-fake-input" style={{ cursor: 'default' }}>
                  <svg viewBox="0 0 20 20" width="16" height="16">
                    <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="2" fill="none" />
                    <path d="M13.5 13.5L17 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                  </svg>
                  <input
                    type="text"
                    placeholder="Tìm kiếm bạn bè..."
                    value={friendSearch}
                    onChange={(e) => setFriendSearch(e.target.value)}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      outline: 'none',
                      color: 'inherit',
                      width: '100%',
                      fontSize: '14px',
                    }}
                    disabled={addingMembers}
                  />
                </div>
              </div>

              {loadingFriends && (
                <div className="search-state">
                  <div className="search-spinner spin"></div>
                  <span>Đang tải bạn bè...</span>
                </div>
              )}

              {!loadingFriends && availableFriends.length === 0 && (
                <div className="search-state search-empty">
                  <span>Không còn bạn bè nào để thêm vào nhóm.</span>
                </div>
              )}

              {!loadingFriends &&
                availableFriends.map((friend) => {
                  const isSelected = selectedFriendIds.has(friend.id);
                  const displayName = friend.fullname || friend.username;
                  return (
                    <div
                      key={friend.id}
                      className={`search-result-item friend-select-item ${isSelected ? 'selected' : ''}`}
                      onClick={() => !addingMembers && toggleSelectFriend(friend.id)}
                      style={{ cursor: addingMembers ? 'not-allowed' : 'pointer' }}
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

              <div style={{ marginTop: '14px', display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button
                  type="button"
                  className="btn"
                  onClick={() => setActiveTab('members')}
                  disabled={addingMembers}
                >
                  Hủy
                </button>
                <button
                  type="button"
                  className="btn primary"
                  onClick={handleAddMembersSubmit}
                  disabled={addingMembers || selectedFriendIds.size === 0}
                >
                  {addingMembers ? 'Đang thêm...' : `Thêm (${selectedFriendIds.size})`}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* CONFIRMATION OVERLAY MODAL */}
        {confirmAction && (
          <div
            className="confirm-action-overlay"
            style={{
              position: 'absolute',
              inset: 0,
              background: 'rgba(0,0,0,0.7)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 300,
              padding: '20px',
              borderRadius: 'var(--radius-lg)',
            }}
          >
            <div
              className="confirm-card"
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--glass-border)',
                borderRadius: 'var(--radius-md)',
                padding: '20px',
                width: '100%',
                maxWidth: '360px',
              }}
            >
              {/* KICK CONFIRMATION */}
              {confirmAction.type === 'KICK' && (
                <>
                  <h4 style={{ margin: '0 0 8px', color: 'var(--danger)' }}>Xóa thành viên</h4>
                  <p style={{ margin: '0 0 16px', fontSize: '14px', color: 'var(--text-secondary)' }}>
                    Bạn có chắc chắn muốn xóa <strong>{confirmAction.targetMember.username}</strong> khỏi nhóm?
                  </p>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                    <button className="btn" onClick={() => setConfirmAction(null)} disabled={actionLoading}>
                      Hủy
                    </button>
                    <button className="btn danger" onClick={handleKickMember} disabled={actionLoading}>
                      {actionLoading ? 'Đang xóa...' : 'Xóa'}
                    </button>
                  </div>
                </>
              )}

              {/* TRANSFER ADMIN CONFIRMATION */}
              {confirmAction.type === 'TRANSFER' && (
                <>
                  <h4 style={{ margin: '0 0 8px', color: 'var(--text-primary)' }}>Chuyển quyền Admin</h4>
                  <p style={{ margin: '0 0 16px', fontSize: '14px', color: 'var(--text-secondary)' }}>
                    Bạn có chắc chắn muốn chuyển quyền Quản trị viên nhóm cho <strong>{confirmAction.targetMember.username}</strong>? Sau khi chuyển, bạn sẽ trở thành thành viên thông thường.
                  </p>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                    <button className="btn" onClick={() => setConfirmAction(null)} disabled={actionLoading}>
                      Hủy
                    </button>
                    <button className="btn primary" onClick={handleTransferAdmin} disabled={actionLoading}>
                      {actionLoading ? 'Đang chuyển...' : 'Xác nhận chuyển'}
                    </button>
                  </div>
                </>
              )}

              {/* LEAVE GROUP CONFIRMATION */}
              {confirmAction.type === 'LEAVE' && (
                <>
                  <h4 style={{ margin: '0 0 8px', color: 'var(--danger)' }}>Rời khỏi nhóm</h4>
                  <p style={{ margin: '0 0 12px', fontSize: '14px', color: 'var(--text-secondary)' }}>
                    Bạn có chắc chắn muốn rời khỏi nhóm này không?
                  </p>

                  {/* If caller is ADMIN and there are other active members, optionally pick successor admin */}
                  {isAdmin && otherActiveMembers.length > 0 && (
                    <div style={{ marginBottom: '16px' }}>
                      <label className="form-label" style={{ fontSize: '12px' }}>
                        Chỉ định Admin mới (Tùy chọn):
                      </label>
                      <select
                        className="group-input"
                        value={successorAdminId}
                        onChange={(e) => setSuccessorAdminId(e.target.value)}
                        style={{ fontSize: '13px', padding: '6px 10px' }}
                      >
                        <option value="">Tự động chỉ định (thành viên lâu nhất)</option>
                        {otherActiveMembers.map((m) => (
                          <option key={m.userId} value={m.userId}>
                            {m.username} {m.fullname ? `(${m.fullname})` : ''}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                    <button className="btn" onClick={() => setConfirmAction(null)} disabled={actionLoading}>
                      Hủy
                    </button>
                    <button className="btn danger" onClick={handleLeaveGroup} disabled={actionLoading}>
                      {actionLoading ? 'Đang rời...' : 'Rời nhóm'}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default GroupInfoModal;
