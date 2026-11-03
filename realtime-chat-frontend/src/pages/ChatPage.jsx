import { useContext, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AuthContext } from '../contexts/AuthContext';
import { useChat } from '../hooks/useChat';
import Sidebar from '../components/Chat/Sidebar';
import Topbar from '../components/Chat/Topbar';
import MessageList from '../components/Chat/MessageList';
import MessageInput from '../components/Chat/MessageInput';
import DisconnectOverlay from '../components/Chat/DisconnectOverlay';
import CreateGroupModal from '../components/Chat/CreateGroupModal';
import GroupInfoModal from '../components/Chat/GroupInfoModal';

const ChatPage = () => {
  const { user, logout } = useContext(AuthContext);
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showCreateGroup, setShowCreateGroup] = useState(false);
  const [showGroupInfo, setShowGroupInfo] = useState(false);

  const handleNavigate = useCallback((targetId) => {
    if (targetId) {
      navigate(`/chat/${targetId}`);
    } else {
      navigate('/chat');
    }
  }, [navigate]);

  const chatState = useChat(user, logout, conversationId, handleNavigate);

  const activeConversation = chatState.conversations.find(
    (c) => c.id === chatState.activeConversationId
  );

  return (
    <>
      <Sidebar
        user={user}
        onlineUsers={chatState.onlineUsers}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onLogout={logout}
        conversations={chatState.conversations}
        loadingConversations={chatState.loadingConversations}
        conversationError={chatState.conversationError}
        onRetry={chatState.loadConversations}
        activeConversationId={chatState.activeConversationId}
        onSelectConversation={(id) => {
          chatState.selectConversation(id, true);
        }}
        onCreateDirectChat={chatState.createDirectChat}
        onCreateGroup={() => setShowCreateGroup(true)}
      />
      <div className="chat-root" style={{ marginLeft: sidebarOpen ? 0 : undefined }}>
        <Topbar
          connState={chatState.connState}
          onlineCount={chatState.onlineUsers.size}
          onlineUsers={chatState.onlineUsers}
          lastSeenUsers={chatState.lastSeenUsers}
          onMenuToggle={() => setSidebarOpen(true)}
          activeConversation={activeConversation}
          onOpenGroupInfo={() => setShowGroupInfo(true)}
        />
        <MessageList
          messages={chatState.messages}
          currentUser={user}
          typers={chatState.typers}
          loading={chatState.loadingMessages}
          activeConversation={activeConversation}
        />
        <MessageInput
          onSendMessage={chatState.sendMessage}
          onTyping={chatState.sendTyping}
          disabled={chatState.connState !== 'connected'}
        />
      </div>
      {chatState.isOverlayVisible && <DisconnectOverlay />}

      {showCreateGroup && (
        <CreateGroupModal
          onClose={() => setShowCreateGroup(false)}
          onCreateGroup={async (name, memberUserIds) => {
            await chatState.createGroup(name, memberUserIds);
          }}
        />
      )}

      {showGroupInfo && activeConversation && activeConversation.type === 'GROUP' && (
        <GroupInfoModal
          conversation={activeConversation}
          currentUser={user}
          onlineUsers={chatState.onlineUsers}
          onClose={() => setShowGroupInfo(false)}
          onUpdateGroup={chatState.updateGroup}
          onMemberCountChange={chatState.updateMemberCount}
          onLeaveGroup={async (convId, successorAdminId) => {
            await chatState.leaveGroup(convId, successorAdminId);
            setShowGroupInfo(false);
          }}
        />
      )}
    </>
  );
};

export default ChatPage;