import { useState, useRef, useCallback } from 'react';
import { CONFIG } from '../../utils/config';

const EMOJIS = [
  "😀", "😂", "😍", "😎", "🤔", "😅", "🙏", "👍", "❤️", "🔥", "✅", "🎉",
  "😭", "🤣", "😊", "🥺", "👀", "💯", "🚀", "⭐", "😡", "🤯", "😴", "🤗",
  "😷", "🤩", "🥳", "😬", "🤮", "🤫", "🦋", "🌈", "🌟", "💎", "🎯", "🏆",
  "💪", "👋", "🤝", "🙌", "🌺", "☕", "🍕", "🎮", "🎵", "📚", "💻", "📱",
  "🌙", "☀️",
];

const MessageInput = ({ onSendMessage, onTyping, disabled }) => {
  const [text, setText] = useState("");
  const [showEmoji, setShowEmoji] = useState(false);
  const typingTimeoutRef = useRef(null);
  const textareaRef = useRef(null);

  const handleChange = (e) => {
    setText(e.target.value);
    e.target.style.height = "auto";
    e.target.style.height = Math.min(e.target.scrollHeight, 140) + "px";

    onTyping(true);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => onTyping(false), 1500);
  };

  const handleSend = useCallback(() => {
    if (!text.trim() || disabled) return;
    onSendMessage(text);
    setText("");
    onTyping(false);
    if (textareaRef.current) textareaRef.current.style.height = "auto";
  }, [text, disabled, onSendMessage, onTyping]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleEmojiClick = (em) => {
    setText(prev => prev + em);
    setShowEmoji(false);
  };

  const rem = CONFIG.MSG_MAX_LENGTH - text.length;

  return (
    <footer className="input-bar">
      <div className="input-wrap">
        <button className="icon-btn emoji-btn" onClick={() => setShowEmoji(!showEmoji)}>
           😀 
        </button>
        <textarea
          ref={textareaRef}
          className="msg-input"
          placeholder="Message #public…"
          rows="1"
          maxLength={CONFIG.MSG_MAX_LENGTH}
          value={text}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          disabled={disabled}
        />
        <button className="icon-btn send-btn" onClick={handleSend} disabled={!text.trim() || disabled}>
          Send
        </button>
      </div>
      <div className={`char-count ${rem < 200 ? 'warn' : ''} ${rem < 50 ? 'limit' : ''}`}>{rem}</div>

      {showEmoji && (
        <div className="emoji-picker">
          <div className="emoji-grid">
            {EMOJIS.map(em => (
              <button key={em} className="emoji-btn-item" onClick={() => handleEmojiClick(em)}>{em}</button>
            ))}
          </div>
        </div>
      )}
    </footer>
  );
};
export default MessageInput;