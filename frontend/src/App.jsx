import { Link, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import Movies from './pages/Movies.jsx';
import MovieShows from './pages/MovieShows.jsx';
import SeatMap from './pages/SeatMap.jsx';
import MyBookings from './pages/MyBookings.jsx';
import AuthPage from './pages/AuthPage.jsx';
import Admin from './pages/Admin.jsx';

function RequireAuth({ children, admin = false }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />;
  return children;
}

function Header() {
  const { user, logout } = useAuth();
  return (
    <header className="site-header">
      <Link to="/" className="wordmark">
        SeatBook
      </Link>
      <nav className="nav" aria-label="Main">
        <NavLink to="/" end>
          Movies
        </NavLink>
        {user && <NavLink to="/bookings">My bookings</NavLink>}
        {user?.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
      </nav>
      <div className="account">
        {user ? (
          <>
            <span className="account-name">{user.name}</span>
            <button className="btn btn-quiet" onClick={logout}>
              Sign out
            </button>
          </>
        ) : (
          <Link className="btn btn-quiet" to="/login">
            Sign in
          </Link>
        )}
      </div>
    </header>
  );
}

export default function App() {
  return (
    <>
      <Header />
      <main className="page">
        <Routes>
          <Route path="/" element={<Movies />} />
          <Route path="/movies/:id" element={<MovieShows />} />
          <Route path="/shows/:id" element={<SeatMap />} />
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route
            path="/bookings"
            element={
              <RequireAuth>
                <MyBookings />
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAuth admin>
                <Admin />
              </RequireAuth>
            }
          />
          <Route path="*" element={<p className="empty">That page doesn't exist. <Link to="/">Back to movies</Link></p>} />
        </Routes>
      </main>
    </>
  );
}
