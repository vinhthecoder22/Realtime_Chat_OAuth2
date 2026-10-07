import { memo } from 'react';
import { avatarColor, initials } from '../../utils/helpers';

const MessageItem = memo(({ msg, isMe, isConsecutive }) => {
  // Xử lý render cho tin nhắn hệ thống (Join/Leave)
  if (msg.type === "JOIN" || msg.type === "LEAVE") {
    const isJoin = msg.type === "JOIN";
    return (
      <div className={`msg-event ${isJoin ? 'join' : 'leave'}`}>
        <span className="event-icon">{isJoin ? "→" : "←"}</span>
        {msg.sender} {isJoin ? "joined" : "left"} the room
      </div>
    );
  }

  // Xử lý render cho tin nhắn Chat bình thường
  return (
    <div className={`msg-group ${isMe ? 'is-me' : ''} ${isConsecutive ? 'consecutive' : ''}`}>
      <div className={`msg-avatar ${avatarColor(msg.sender)}`}>
        {initials(msg.sender)}
      </div>
      <div className="msg-body">
        <div className="msg-meta">
          <span className={`msg-sender ${isMe ? 'is-me' : ''}`}>
            {msg.sender} {isMe && "(you)"}
          </span>
          <span className="msg-time">{msg.time}</span>
        </div>
        <div className="msg-bubble">{msg.content}</div>
      </div>
    </div>
  );
});

// Đặt displayName để dễ debug trong React DevTools
MessageItem.displayName = 'MessageItem';

export default MessageItem;