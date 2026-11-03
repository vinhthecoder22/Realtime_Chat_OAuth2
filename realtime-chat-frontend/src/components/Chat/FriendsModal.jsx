import { useState, useEffect } from 'react';
import { Api } from '../../utils/api';
import { avatarColor, initials } from '../../utils/helpers';

const FriendsModal = ({ onClose, onCreateDirectChat }) => {
  const [activeTab, setActiveTab] = useState('friends');
  const [friends, setFriends] = useState([]);
  const [received, setReceived] = useState([]);
  const [sent, setSent] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchFriends = async () => {
    setLoading(true);
    try {
      const data = await Api.getFriends();
      setFriends(data?.content || []);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchReceived = async () => {
    setLoading(true);
    try {
      const data = await Api.getReceivedRequests();
      setReceived(data?.content || []);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchSent = async () => {
    setLoading(true);
    try {
      const data = await Api.getSentRequests();
      setSent(data?.content || []);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'friends') fetchFriends();
    else if (activeTab === 'received') fetchReceived();
    else if (activeTab === 'sent') fetchSent();
  }, [activeTab]);

  const handleAccept = async (id) => {
    try {
      await Api.acceptRequest(id);
      fetchReceived();
    } catch (err) {
      alert(err.message);
    }
  };

  const handleReject = async (id) => {
    try {
      await Api.rejectRequest(id);
      fetchReceived();
    } catch (err) {
      alert(err.message);
    }
  };

  const handleCancel = async (id) => {
    try {
      await Api.cancelRequest(id);
      fetchSent();
    } catch (err) {
      alert(err.message);
    }
  };

  const handleMessage = async (friendId) => {
    if (onCreateDirectChat) {
      try {
        await onCreateDirectChat(friendId);
        onClose();
      } catch (err) {
        alert(err.message);
      }
    }
  };

  return (
    <div className="user-search-overlay" onClick={onClose}>
      <div className="user-search-modal" onClick={e => e.stopPropagation()}>
        <div className="user-search-header">
          <h3>Friends & Requests</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <svg viewBox="0 0 20 20"><path d="M6 6l8 8M14 6l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
          </button>
        </div>

        <div className="friends-tabs" style={{ display: 'flex', borderBottom: '1px solid var(--border)', marginBottom: '10px' }}>
          <button className={`tab-btn ${activeTab === 'friends' ? 'active' : ''}`} onClick={() => setActiveTab('friends')}>
            Friends
          </button>
          <button className={`tab-btn ${activeTab === 'received' ? 'active' : ''}`} onClick={() => setActiveTab('received')}>
            Received
          </button>
          <button className={`tab-btn ${activeTab === 'sent' ? 'active' : ''}`} onClick={() => setActiveTab('sent')}>
            Sent
          </button>
        </div>

        <div className="user-search-results" style={{ padding: '0 10px', height: '350px', overflowY: 'auto' }}>
          {loading && (
            <div className="search-state">
              <div className="search-spinner spin"></div>
              <span>Loading…</span>
            </div>
          )}
          
          {error && <div className="search-state search-error"><span>{error}</span></div>}

          {!loading && !error && activeTab === 'friends' && friends.length === 0 && (
             <div className="search-state search-empty"><span>No friends found.</span></div>
          )}
          {!loading && !error && activeTab === 'friends' && friends.map(user => (
             <div key={user.id} className="search-result-item" style={{cursor: 'default'}}>
               <div className={`avatar ${avatarColor(user.username)}`}>{initials(user.username)}</div>
               <div className="search-result-info">
                 <div className="search-result-username">{user.username}</div>
                 {user.fullname && <div className="search-result-fullname">{user.fullname}</div>}
               </div>
               <button className="btn primary" onClick={() => handleMessage(user.id)} style={{padding: '5px 10px', fontSize: '12px'}}>Message</button>
             </div>
          ))}

          {!loading && !error && activeTab === 'received' && received.length === 0 && (
             <div className="search-state search-empty"><span>No received requests.</span></div>
          )}
          {!loading && !error && activeTab === 'received' && received.map(req => (
             <div key={req.id} className="search-result-item" style={{cursor: 'default'}}>
               <div className={`avatar ${avatarColor(req.sender.username)}`}>{initials(req.sender.username)}</div>
               <div className="search-result-info">
                 <div className="search-result-username">{req.sender.username}</div>
                 <div className="search-result-fullname">Wants to be friends</div>
               </div>
               <div style={{display: 'flex', gap: '5px'}}>
                 <button className="btn primary" onClick={() => handleAccept(req.id)} style={{padding: '5px 10px', fontSize: '12px'}}>Accept</button>
                 <button className="btn" onClick={() => handleReject(req.id)} style={{padding: '5px 10px', fontSize: '12px'}}>Reject</button>
               </div>
             </div>
          ))}

          {!loading && !error && activeTab === 'sent' && sent.length === 0 && (
             <div className="search-state search-empty"><span>No sent requests.</span></div>
          )}
          {!loading && !error && activeTab === 'sent' && sent.map(req => (
             <div key={req.id} className="search-result-item" style={{cursor: 'default'}}>
               <div className={`avatar ${avatarColor(req.receiver.username)}`}>{initials(req.receiver.username)}</div>
               <div className="search-result-info">
                 <div className="search-result-username">{req.receiver.username}</div>
                 <div className="search-result-fullname">Request Pending</div>
               </div>
               <button className="btn" onClick={() => handleCancel(req.id)} style={{padding: '5px 10px', fontSize: '12px'}}>Cancel</button>
             </div>
          ))}
        </div>
      </div>
      <style>{`
        .friends-tabs .tab-btn {
          flex: 1;
          background: none;
          border: none;
          padding: 10px;
          color: var(--text-secondary);
          cursor: pointer;
          font-weight: 500;
          border-bottom: 2px solid transparent;
        }
        .friends-tabs .tab-btn:hover {
          color: var(--text-primary);
        }
        .friends-tabs .tab-btn.active {
          color: var(--accent);
          border-bottom-color: var(--accent);
        }
      `}</style>
    </div>
  );
};

export default FriendsModal;
