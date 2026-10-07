import { useContext, useState } from 'react';
import { AuthContext } from '../contexts/AuthContext';
import { useChat } from '../hooks/useChat';
import Sidebar from '../components/Chat/Sidebar';
import Topbar from '../components/Chat/Topbar';
import MessageList from '../components/Chat/MessageList';
import MessageInput from '../components/Chat/MessageInput';
import DisconnectOverlay from '../components/Chat/DisconnectOverlay';

const ChatPage = () => {
  const { user, logout } = useContext(AuthContext);
  const chatState = useChat(user, logout);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <>
      <Sidebar 
        user={user} 
        onlineUsers={chatState.onlineUsers} 
        isOpen={sidebarOpen} 
        onClose={() => setSidebarOpen(false)} 
        onLogout={logout} 
      />
      <div className="chat-root" style={{ marginLeft: sidebarOpen ? 0 : undefined }}>
        <Topbar 
          connState={chatState.connState} 
          onlineCount={chatState.onlineUsers.size} 
          onMenuToggle={() => setSidebarOpen(true)} 
        />
        <MessageList 
          messages={chatState.messages} 
          currentUser={user} 
          typers={chatState.typers} 
        />
        <MessageInput 
          onSendMessage={chatState.sendMessage} 
          onTyping={chatState.sendTyping} 
          disabled={chatState.connState !== 'connected'} 
        />
      </div>
      {chatState.isOverlayVisible && <DisconnectOverlay />}
    </>
  );
};
export default ChatPage;