import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, AuthContext } from './contexts/AuthContext';
import { useContext } from 'react';
import LoginPage from './pages/LoginPage';
import ChatPage from './pages/ChatPage';
import OAuth2RedirectPage from './pages/OAuth2RedirectPage';
import ToastContainer from './components/Common/ToastContainer';

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated } = useContext(AuthContext);
  return isAuthenticated ? children : <Navigate to="/login" replace />;
};

const PublicRoute = ({ children }) => {
  const { isAuthenticated } = useContext(AuthContext);
  return !isAuthenticated ? children : <Navigate to="/chat" replace />;
};

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
      <Route path="/oauth2-redirect" element={<OAuth2RedirectPage />} />
      <Route path="/chat" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
        {/* ToastContainer đặt ở root để có thể hiển thị ở mọi page */}
        <ToastContainer /> 
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;