const BASE = import.meta.env.VITE_API_URL || '/api';
const STORAGE_KEY = 'seatbook.auth';

function readToken() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY))?.token ?? null;
  } catch {
    return null;
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const token = readToken();
  let res;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    const err = new Error('Cannot reach the server. Check your connection and try again.');
    err.network = true;
    throw err;
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!res.ok) {
    // An expired or invalid token on a protected call: sign the user out everywhere in the app.
    if (res.status === 401 && token) {
      window.dispatchEvent(new Event('seatbook:signed-out'));
    }
    const err = new Error(data?.error || `Something went wrong (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),

  movies: (page = 0, size = 12) => request(`/movies?page=${page}&size=${size}`),
  movie: (id) => request(`/movies/${id}`),
  shows: (movieId) => request(`/movies/${movieId}/shows`),
  show: (id) => request(`/shows/${id}`),
  seats: (showId) => request(`/shows/${showId}/seats`),

  hold: (showId, seatLabels) => request(`/shows/${showId}/hold`, { method: 'POST', body: { seatLabels } }),
  pay: (bookingId, idempotencyKey, simulateFailure = false) =>
    request(`/bookings/${bookingId}/pay`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: { simulateFailure },
    }),
  cancel: (bookingId) => request(`/bookings/${bookingId}/cancel`, { method: 'POST' }),
  myBookings: () => request('/bookings/mine'),

  admin: {
    createMovie: (body) => request('/admin/movies', { method: 'POST', body }),
    createTheatre: (body) => request('/admin/theatres', { method: 'POST', body }),
    createShow: (body) => request('/admin/shows', { method: 'POST', body }),
  },
};
