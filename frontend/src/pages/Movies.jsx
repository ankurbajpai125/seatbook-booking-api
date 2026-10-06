import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';

export default function Movies() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    api
      .movies(page)
      .then((d) => !cancelled && setData(d))
      .catch((e) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [page]);

  if (error) return <p className="notice notice-error" role="alert">{error}</p>;
  if (!data) return <p className="muted">Loading movies…</p>;

  return (
    <>
      <h1 className="display">Now showing</h1>

      {data.content.length === 0 ? (
        <p className="empty">
          No movies yet.{' '}
          {user?.role === 'ADMIN' ? <Link to="/admin">Add the first movie</Link> : 'Check back soon.'}
        </p>
      ) : (
        <ul className="movie-list">
          {data.content.map((m) => (
            <li key={m.id}>
              <Link to={`/movies/${m.id}`} className="movie-row">
                <span className="movie-title">{m.title}</span>
                <span className="movie-meta">
                  {m.language && <span>{m.language}</span>}
                  <span>{m.durationMinutes} min</span>
                </span>
                {m.description && <span className="movie-desc">{m.description}</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data.totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-quiet" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span className="muted">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <button className="btn btn-quiet" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Next
          </button>
        </div>
      )}
    </>
  );
}
