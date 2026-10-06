import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { formatClock, formatShow, money, newIdempotencyKey, parseLocal } from '../format.js';

const MAX_SEATS = 6;
const REFRESH_MS = 8000;

export default function SeatMap() {
  const { id } = useParams();
  const showId = Number(id);
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [show, setShow] = useState(null);
  const [movie, setMovie] = useState(null);
  const [seats, setSeats] = useState([]);
  const [loadError, setLoadError] = useState(null);

  const [selected, setSelected] = useState([]); // labels the user is picking
  const [booking, setBooking] = useState(null); // the held, unpaid booking
  const [deadline, setDeadline] = useState(null); // client clock time when the hold ends
  const [remaining, setRemaining] = useState(0);
  const [confirmed, setConfirmed] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null); // { type: 'error' | 'info', text }
  const [simulateFailure, setSimulateFailure] = useState(false);
  const payKey = useRef(null);

  const refreshSeats = useCallback(async () => {
    try {
      setSeats(await api.seats(showId));
    } catch {
      /* keep showing the last known map */
    }
  }, [showId]);

  // Initial load.
  useEffect(() => {
    let cancelled = false;
    setLoadError(null);
    (async () => {
      try {
        const [s, seatList] = await Promise.all([api.show(showId), api.seats(showId)]);
        if (cancelled) return;
        setShow(s);
        setSeats(seatList);
        const m = await api.movie(s.movieId);
        if (!cancelled) setMovie(m);
      } catch (e) {
        if (!cancelled) setLoadError(e.message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [showId]);

  // Keep the map fresh so other people's holds and bookings show up.
  useEffect(() => {
    if (confirmed) return undefined;
    const t = setInterval(refreshSeats, REFRESH_MS);
    return () => clearInterval(t);
  }, [refreshSeats, confirmed]);

  const resetHold = useCallback(
    (note) => {
      setBooking(null);
      setDeadline(null);
      setSelected([]);
      payKey.current = null;
      if (note) setMessage(note);
      refreshSeats();
    },
    [refreshSeats],
  );

  // Countdown for the hold. It runs off a duration so it stays correct even if the
  // server and this browser are in different time zones.
  useEffect(() => {
    if (!deadline) return undefined;
    const tick = () => {
      const left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setRemaining(left);
      if (left === 0) {
        resetHold({ type: 'info', text: 'Your hold ran out and the seats were released. Pick your seats again.' });
      }
    };
    tick();
    const t = setInterval(tick, 500);
    return () => clearInterval(t);
  }, [deadline, resetHold]);

  const mine = useMemo(() => new Set(booking?.seatLabels ?? []), [booking]);

  const rows = useMemo(() => {
    const byRow = new Map();
    for (const s of seats) {
      const row = s.label[0];
      if (!byRow.has(row)) byRow.set(row, []);
      byRow.get(row).push(s);
    }
    return [...byRow.entries()];
  }, [seats]);

  const stateOf = (s) => {
    if (mine.has(s.label)) return 'mine';
    if (selected.includes(s.label)) return 'selected';
    if (s.status === 'BOOKED') return 'booked';
    if (s.status === 'HELD') return 'held';
    return 'free';
  };

  function toggle(seat) {
    if (booking || confirmed || stateOf(seat) === 'booked' || stateOf(seat) === 'held') return;
    setMessage(null);
    if (selected.includes(seat.label)) {
      setSelected(selected.filter((l) => l !== seat.label));
    } else if (selected.length >= MAX_SEATS) {
      setMessage({ type: 'info', text: `You can book up to ${MAX_SEATS} seats at a time.` });
    } else {
      setSelected([...selected, seat.label]);
    }
  }

  async function hold() {
    if (!user) {
      navigate('/login', { state: { from: location.pathname } });
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      const b = await api.hold(showId, selected);
      let secs = Math.round((parseLocal(b.holdExpiresAt) - parseLocal(b.createdAt)) / 1000);
      if (!Number.isFinite(secs) || secs <= 0) secs = 300;
      payKey.current = null;
      setBooking(b);
      setSelected([]);
      setDeadline(Date.now() + secs * 1000);
      refreshSeats();
    } catch (e) {
      setMessage({ type: 'error', text: e.message });
      setSelected([]);
      refreshSeats();
    } finally {
      setBusy(false);
    }
  }

  async function pay() {
    if (!payKey.current) payKey.current = newIdempotencyKey();
    setBusy(true);
    setMessage(null);
    try {
      const result = await api.pay(booking.id, payKey.current, simulateFailure);
      payKey.current = null;
      if (result.status === 'SUCCESS') {
        setConfirmed(result.booking);
        setBooking(null);
        setDeadline(null);
        refreshSeats();
      } else {
        setMessage({ type: 'error', text: 'Payment failed. Your seats are still held, so you can try again.' });
      }
    } catch (e) {
      if (e.status === 410 || e.status === 409) {
        resetHold({ type: 'error', text: e.message });
      } else {
        // Network or server error: keep the same key so a retry can never charge twice.
        setMessage({ type: 'error', text: e.message });
      }
    } finally {
      setBusy(false);
    }
  }

  async function release() {
    setBusy(true);
    try {
      await api.cancel(booking.id);
      resetHold({ type: 'info', text: 'Seats released.' });
    } catch (e) {
      setMessage({ type: 'error', text: e.message });
    } finally {
      setBusy(false);
    }
  }

  if (loadError) return <p className="notice notice-error" role="alert">{loadError}</p>;
  if (!show) return <p className="muted">Loading seats…</p>;

  const price = Number(show.price);
  const selectedTotal = selected.length * price;

  return (
    <>
      <p className="crumb">
        {movie ? <Link to={`/movies/${movie.id}`}>{movie.title}</Link> : <Link to="/">All movies</Link>}
      </p>
      <h1 className="display display-sm">{movie?.title ?? 'Choose seats'}</h1>
      <p className="lede">
        {formatShow(show.startTime)}, {money(price)} per seat
      </p>

      {confirmed ? (
        <section className="confirmation" aria-live="polite">
          <h2 className="section-title">Booking confirmed</h2>
          <p>
            Seats <strong>{confirmed.seatLabels.join(', ')}</strong> are yours. You paid {money(confirmed.totalAmount)}.
          </p>
          <div className="actions">
            <Link className="btn btn-primary" to="/bookings">
              View my bookings
            </Link>
            <button
              className="btn btn-quiet"
              onClick={() => {
                setConfirmed(null);
                setMessage(null);
                refreshSeats();
              }}
            >
              Book more seats
            </button>
          </div>
        </section>
      ) : (
        <>
          <div className="theatre">
            <div className="screen" aria-hidden="true">
              <span>Screen</span>
            </div>
            <div className="seat-scroll">
              <div className="seat-grid" role="group" aria-label="Seat map">
                {rows.map(([row, rowSeats]) => (
                  <div className="seat-row" key={row}>
                    <span className="row-label" aria-hidden="true">
                      {row}
                    </span>
                    {rowSeats.map((s) => {
                      const st = stateOf(s);
                      const unavailable = st === 'booked' || st === 'held';
                      return (
                        <button
                          key={s.label}
                          type="button"
                          className={`seat seat-${st}`}
                          onClick={() => toggle(s)}
                          disabled={unavailable || !!booking}
                          aria-pressed={st === 'selected' || st === 'mine'}
                          aria-label={`Seat ${s.label}, ${
                            st === 'booked' ? 'booked' : st === 'held' ? 'held by someone else' : st === 'mine' ? 'held for you' : 'available'
                          }`}
                        >
                          {s.label.slice(1)}
                        </button>
                      );
                    })}
                    <span className="row-label" aria-hidden="true">
                      {row}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <ul className="legend" aria-label="Seat key">
            <li><span className="seat seat-free" aria-hidden="true" /> Available</li>
            <li><span className="seat seat-selected" aria-hidden="true" /> Your pick</li>
            <li><span className="seat seat-held" aria-hidden="true" /> Held by someone else</li>
            <li><span className="seat seat-booked" aria-hidden="true" /> Booked</li>
          </ul>

          {message && (
            <p className={`notice ${message.type === 'error' ? 'notice-error' : 'notice-info'}`} role="alert">
              {message.text}
            </p>
          )}

          <div className="checkout-bar">
            {booking ? (
              <>
                <div className="checkout-info">
                  <span className={`timer ${remaining <= 60 ? 'timer-low' : ''}`} role="timer" aria-live="off">
                    {formatClock(remaining)}
                  </span>
                  <span>
                    Seats {booking.seatLabels.join(', ')} held for you. Total {money(booking.totalAmount)}.
                  </span>
                </div>
                <label className="demo-toggle">
                  <input type="checkbox" checked={simulateFailure} onChange={(e) => setSimulateFailure(e.target.checked)} />
                  Simulate a failed payment
                </label>
                <div className="actions">
                  <button className="btn btn-quiet" onClick={release} disabled={busy}>
                    Release seats
                  </button>
                  <button className="btn btn-primary" onClick={pay} disabled={busy}>
                    {busy ? 'Processing…' : `Pay ${money(booking.totalAmount)}`}
                  </button>
                </div>
              </>
            ) : (
              <>
                <div className="checkout-info">
                  {selected.length === 0 ? (
                    <span className="muted">Select up to {MAX_SEATS} seats.</span>
                  ) : (
                    <span>
                      {selected.length} {selected.length === 1 ? 'seat' : 'seats'}: {[...selected].sort().join(', ')}.
                      Total {money(selectedTotal)}.
                    </span>
                  )}
                </div>
                <div className="actions">
                  <button className="btn btn-primary" onClick={hold} disabled={busy || selected.length === 0}>
                    {busy ? 'Holding…' : user ? 'Hold seats' : 'Sign in to hold seats'}
                  </button>
                </div>
              </>
            )}
          </div>
        </>
      )}
    </>
  );
}
