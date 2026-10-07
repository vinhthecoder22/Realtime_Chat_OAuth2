import { useState, useEffect } from 'react';

const ToastContainer = () => {
  const [toasts, setToasts] = useState([]);

  useEffect(() => {
    const handleShowToast = (e) => {
      const { message, type } = e.detail;
      const id = Date.now() + Math.random();
      
      setToasts(prev => [{ id, message, type }, ...prev]);

      // Tự động xóa sau 3.5s
      setTimeout(() => {
        setToasts(prev => prev.filter(t => t.id !== id));
      }, 3500);
    };

    window.addEventListener('show-toast', handleShowToast);
    return () => window.removeEventListener('show-toast', handleShowToast);
  }, []);

  return (
    <div className="toast-stack" id="toast-stack">
      {toasts.map(toast => (
        <div key={toast.id} className={`toast ${toast.type}`}>
          {toast.message}
        </div>
      ))}
    </div>
  );
};
export default ToastContainer;