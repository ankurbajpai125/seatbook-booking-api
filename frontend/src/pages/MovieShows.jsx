import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { formatShow, money } from '../format.js';

export default function MovieShows() {
  const { id } = useParams();
  const [movie, setMovie] = useState(null);
  const [shows, setShows] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    Promise.all([api.movie(id), api.shows(id)])
      .then(([m, s]) => {
        if (cancelled) return;
        setMovie(m);
        setShows(s);
      })
      .catch((e) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (error) return <p className="notice notice-error" role="alert">{error}</p>;
  if (!movie || !shows) return <p className="muted">Loading shows…</p>;

  return (
    <>
      <p className="crumb">
        <Link to="/">All movies</Link>
      </p>
      <h1 className="display">{movie.title}</h1>
      <p className="lede">
        {[movie.language, `${movie.durationMinutes} min`].filter(Boolean).join(', ')}
        {movie.description ? `. ${movie.description}` : ''}
      </p>

      <h2 className="section-title">Pick a show</h2>
      {shows.length === 0 ? (
        <p className="empty">No upcoming shows for this movie yet.</p>
      ) : (
        <ul className="show-list">
          {shows.map((s) => (
            <li key={s.id} className="show-row">
              <span className="show-time">{formatShow(s.startTime)}</span>
              <span className="muted">{money(s.price)} per seat</span>
              <Link className="btn btn-primary" to={`/shows/${s.id}`}>
                Choose seats
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
