import { useEffect, useRef } from 'react';
import MessageItem from './MessageItem'; // Import Component vừa tách

const MessageList = ({ messages, currentUser, typers }) => {
  const bottomRef = useRef(null);

  // Tự động cuộn xuống cuối cùng khi có tin nhắn mới hoặc có người đang gõ
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, typers]);

  return (
    <div className="messages-wrap" id="messages-wrap">
      <div className="messages">
        {/* Lời chào hệ thống */}
        <div className="msg-system">
          <div className="sys-line"></div>
          <span>Welcome to ChatWithMe</span>
          <div className="sys-line"></div>
        </div>
        
        {/* Render danh sách tin nhắn */}
        {messages.map((msg, index) => {
          const prevMsg = messages[index - 1];
          let isConsecutive = false;

          if (prevMsg && prevMsg.sender === msg.sender && msg.type === "CHAT") {
            const parseTime = (ts) => {
              if (!ts) return 0;
              if (Array.isArray(ts)) return new Date(ts[0], ts[1] - 1, ts[2], ts[3], ts[4], ts[5] || 0).getTime();
              return new Date(ts).getTime();
            };

            const prevTime = parseTime(prevMsg.timestamp);
            const currTime = parseTime(msg.timestamp);
            
            const diff = currTime - prevTime;
            
            if (diff >= 0 && diff < 60000) {
              isConsecutive = true;
            }
          }

          return (
            <MessageItem 
              key={msg.internalId || index} 
              msg={msg} 
              isMe={msg.sender === currentUser} 
              isConsecutive={isConsecutive} 
            />
          );
        })}

        {/* Typing Indicator */}
        {typers.size > 0 && (
          <div className="typing-row">
            <div className="typing-bubble">
              <span></span><span></span><span></span>
            </div>
            <span className="typing-text">
              {Array.from(typers).join(', ')} {typers.size > 1 ? 'are' : 'is'} typing...
            </span>
          </div>
        )}
        
        {/* Element ẩn dùng để neo điểm cuộn trang (scroll to bottom) */}
        <div ref={bottomRef} />
      </div>
    </div>
  );
};

export default MessageList;