import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { formatShow, money } from '../format.js';

const STATUS_LABEL = {
  PENDING: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
};

export default function MyBookings() {
  const [bookings, setBookings] = useState(null);
  const [details, setDetails] = useState({}); // showId -> { show, movie }
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    try {
      const list = await api.myBookings();
      setBookings(list);

      const showIds = [...new Set(list.map((b) => b.showId))];
      const shows = await Promise.all(showIds.map((id) => api.show(id).catch(() => null)));
      const movieIds = [...new Set(shows.filter(Boolean).map((s) => s.movieId))];
      const movies = await Promise.all(movieIds.map((id) => api.movie(id).catch(() => null)));
      const movieById = Object.fromEntries(movies.filter(Boolean).map((m) => [m.id, m]));

      const next = {};
      shows.filter(Boolean).forEach((s) => {
        next[s.id] = { show: s, movie: movieById[s.movieId] };
      });
      setDetails(next);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function cancel(booking) {
    if (!window.confirm('Cancel this booking? Your seats will be released.')) return;
    setBusyId(booking.id);
    setError(null);
    try {
      await api.cancel(booking.id);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  if (!bookings && !error) return <p className="muted">Loading your bookings…</p>;

  return (
    <>
      <h1 className="display">My bookings</h1>
      {error && (
        <p className="notice notice-error" role="alert">
          {error}
        </p>
      )}
      {bookings?.length === 0 && (
        <p className="empty">
          You haven't booked anything yet. <Link to="/">Find a movie</Link>
        </p>
      )}
      <ul className="booking-list">
        {bookings?.map((b) => {
          const d = details[b.showId];
          const cancellable = b.status === 'PENDING' || b.status === 'CONFIRMED';
          return (
            <li key={b.id} className="booking-row">
              <div>
                <p className="booking-title">{d?.movie?.title ?? `Show ${b.showId}`}</p>
                <p className="muted">{d?.show ? formatShow(d.show.startTime) : ''}</p>
                <p className="booking-seats">Seats {b.seatLabels.join(', ') || 'released'}</p>
              </div>
              <div className="booking-side">
                <span className={`badge badge-${b.status.toLowerCase()}`}>{STATUS_LABEL[b.status]}</span>
                <span className="booking-total">{money(b.totalAmount)}</span>
                {cancellable && (
                  <button className="btn btn-quiet" disabled={busyId === b.id} onClick={() => cancel(b)}>
                    Cancel booking
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
