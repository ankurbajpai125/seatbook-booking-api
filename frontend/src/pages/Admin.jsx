import { useEffect, useState } from 'react';
import { api } from '../api.js';

function useAction() {
  const [state, setState] = useState({ busy: false, error: null, done: null });
  async function run(fn) {
    setState({ busy: true, error: null, done: null });
    try {
      const result = await fn();
      setState({ busy: false, error: null, done: result });
      return result;
    } catch (e) {
      setState({ busy: false, error: e.message, done: null });
      return null;
    }
  }
  return [state, run];
}

function Result({ state, success }) {
  if (state.error)
    return (
      <p className="notice notice-error" role="alert">
        {state.error}
      </p>
    );
  if (state.done) return <p className="notice notice-ok">{success(state.done)}</p>;
  return null;
}

export default function Admin() {
  const [movies, setMovies] = useState([]);
  const [movieForm, setMovieForm] = useState({ title: '', language: 'English', durationMinutes: 120, description: '' });
  const [theatreForm, setTheatreForm] = useState({ name: '', city: '', rows: 6, seatsPerRow: 10 });
  const [showForm, setShowForm] = useState({ movieId: '', theatreId: '', startTime: '', price: 250 });
  const [movieAction, runMovie] = useAction();
  const [theatreAction, runTheatre] = useAction();
  const [showAction, runShow] = useAction();

  const loadMovies = () =>
    api
      .movies(0, 50)
      .then((d) => setMovies(d.content))
      .catch(() => {});

  useEffect(() => {
    loadMovies();
  }, []);

  const bind = (setter) => (field) => (e) => setter((f) => ({ ...f, [field]: e.target.value }));
  const setM = bind(setMovieForm);
  const setT = bind(setTheatreForm);
  const setS = bind(setShowForm);

  async function addMovie(e) {
    e.preventDefault();
    const created = await runMovie(() =>
      api.admin.createMovie({ ...movieForm, durationMinutes: Number(movieForm.durationMinutes) }),
    );
    if (created) {
      setMovieForm({ title: '', language: 'English', durationMinutes: 120, description: '' });
      loadMovies();
    }
  }

  async function addTheatre(e) {
    e.preventDefault();
    const created = await runTheatre(() =>
      api.admin.createTheatre({
        ...theatreForm,
        rows: Number(theatreForm.rows),
        seatsPerRow: Number(theatreForm.seatsPerRow),
      }),
    );
    if (created) setShowForm((f) => ({ ...f, theatreId: String(created.id) }));
  }

  function addShow(e) {
    e.preventDefault();
    runShow(() =>
      api.admin.createShow({
        movieId: Number(showForm.movieId),
        theatreId: Number(showForm.theatreId),
        startTime: showForm.startTime,
        price: Number(showForm.price),
      }),
    );
  }

  return (
    <>
      <h1 className="display">Admin</h1>

      <section className="admin-block">
        <h2 className="section-title">Add a movie</h2>
        <form className="form" onSubmit={addMovie}>
          <label>
            Title
            <input value={movieForm.title} onChange={setM('title')} required />
          </label>
          <div className="form-row">
            <label>
              Language
              <input value={movieForm.language} onChange={setM('language')} />
            </label>
            <label>
              Duration (minutes)
              <input type="number" min="1" value={movieForm.durationMinutes} onChange={setM('durationMinutes')} required />
            </label>
          </div>
          <label>
            Description
            <textarea rows="3" maxLength="1000" value={movieForm.description} onChange={setM('description')} />
          </label>
          <button className="btn btn-primary" disabled={movieAction.busy}>
            Add movie
          </button>
          <Result state={movieAction} success={(m) => `Added "${m.title}".`} />
        </form>
      </section>

      <section className="admin-block">
        <h2 className="section-title">Add a theatre</h2>
        <form className="form" onSubmit={addTheatre}>
          <div className="form-row">
            <label>
              Name
              <input value={theatreForm.name} onChange={setT('name')} required />
            </label>
            <label>
              City
              <input value={theatreForm.city} onChange={setT('city')} />
            </label>
          </div>
          <div className="form-row">
            <label>
              Rows (1 to 26)
              <input type="number" min="1" max="26" value={theatreForm.rows} onChange={setT('rows')} required />
            </label>
            <label>
              Seats per row (1 to 50)
              <input type="number" min="1" max="50" value={theatreForm.seatsPerRow} onChange={setT('seatsPerRow')} required />
            </label>
          </div>
          <button className="btn btn-primary" disabled={theatreAction.busy}>
            Add theatre
          </button>
          <Result state={theatreAction} success={(t) => `Added "${t.name}" with ID ${t.id}. It is filled in below.`} />
        </form>
      </section>

      <section className="admin-block">
        <h2 className="section-title">Schedule a show</h2>
        <form className="form" onSubmit={addShow}>
          <div className="form-row">
            <label>
              Movie
              <select value={showForm.movieId} onChange={setS('movieId')} required>
                <option value="" disabled>
                  Choose a movie
                </option>
                {movies.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.title}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Theatre ID
              <input type="number" min="1" value={showForm.theatreId} onChange={setS('theatreId')} required />
            </label>
          </div>
          <div className="form-row">
            <label>
              Start time
              <input type="datetime-local" value={showForm.startTime} onChange={setS('startTime')} required />
            </label>
            <label>
              Price per seat (₹)
              <input type="number" min="1" step="0.01" value={showForm.price} onChange={setS('price')} required />
            </label>
          </div>
          <button className="btn btn-primary" disabled={showAction.busy}>
            Schedule show
          </button>
          <Result state={showAction} success={(s) => `Show ${s.id} scheduled. Its seats are ready to book.`} />
        </form>
      </section>
    </>
  );
}
